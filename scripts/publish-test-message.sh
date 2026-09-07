#!/usr/bin/env zsh
set -euo pipefail

INPUT_ROOT="../src/test/resources/files/input"
HH_DIR="${INPUT_ROOT}/hh"
CE_DIR="${INPUT_ROOT}/ce"

# Local-only static settings
EMULATOR="localhost:8085"
PROJECT="fwmt-local"
TOPIC="RM.Field"

TYPE=""
RAW_JSON=""
JSON_FILE=""
USER_PROVIDED_FILE="false"
CLEAR_DB="false"
NO_TRANSFORM="false"

usage() {
  cat <<EOF
Usage:
  $(basename "$0") -t <HH_CREATE|CE_CREATE> [-f file.json] [-T topic] [-c]
  $(basename "$0") -m '{"some":"json"}' [-n] [-T topic] [-c]
  $(basename "$0") -c
  $(basename "$0") -h

Options:
  -t TYPE       Preset: HH_CREATE or CE_CREATE
                - Loads default fixture from acceptance-test directory if -f not specified
                - Required when using -f (for any file path or filename)
  -f FILE       JSON file to use with -t (fixture filename or path)
                - Fixture: -t HH_CREATE -f hhCreate.json
                - Custom path: -t HH_CREATE -f ./my-msg.json or -t HH_CREATE -f /path/to/file.json
  -m JSON       Custom raw JSON payload (string, standalone - no -t needed)
  -T TOPIC      Topic name (default: RM.Field)
  -c            Clear database tables before publishing (message_cache, quarantined_message, gateway_case_record)
  -n            No transformations: publish exact JSON as-is (skip adding caseId, addressLevel, oa, etc.)
                - Use with -m to wrap exact payload without modifications
  -h            Help

Examples:
  $(basename "$0") -t HH_CREATE
  $(basename "$0") -t CE_CREATE -f ceEstabCreate.json
  $(basename "$0") -c -t HH_CREATE
  $(basename "$0") -t HH_CREATE -f ./my-msg.json -T GW.Field -c
  $(basename "$0") -m '{"actionInstruction":"CREATE","surveyName":"CENSUS"}'
  $(basename "$0") -c
EOF
}

while getopts ":t:f:m:T:cnh" opt; do
  case "$opt" in
    t) TYPE="$OPTARG" ;;
    f) JSON_FILE="$OPTARG"; USER_PROVIDED_FILE="true" ;;
    m) RAW_JSON="$OPTARG" ;;
    T) TOPIC="$OPTARG" ;;
    c) CLEAR_DB="true" ;;
    n) NO_TRANSFORM="true" ;;
    h) usage; exit 0 ;;
    :) echo "Missing value for -$OPTARG" >&2; usage; exit 2 ;;
    \?) echo "Unknown option -$OPTARG" >&2; usage; exit 2 ;;
  esac
done

modes=0
[[ -n "$TYPE" ]] && ((modes+=1))
[[ -n "$RAW_JSON" ]] && ((modes+=1))

# -f cannot be used standalone, must be with -t
if [[ -n "$JSON_FILE" && -z "$TYPE" ]]; then
  echo "Error: -f requires -t <HH_CREATE|CE_CREATE>" >&2
  usage
  exit 2
fi

# -n can only be used with -m
if [[ "$NO_TRANSFORM" == "true" && -z "$RAW_JSON" ]]; then
  echo "Error: -n requires -m '...' (no transformations mode only works with inline JSON)" >&2
  usage
  exit 2
fi

if [[ "$NO_TRANSFORM" == "true" && -n "$TYPE" ]]; then
  echo "Error: Cannot combine -n with -t (no transformations mode only works with -m)" >&2
  usage
  exit 2
fi

if (( modes == 0 && CLEAR_DB != "true" )); then
  echo "Error: Must specify one of: -t (with optional -f), -m, or -c" >&2
  usage
  exit 2
fi

if (( modes > 1 )); then
  echo "Error: Cannot combine -t and -m" >&2
  usage
  exit 2
fi

# Clear database if requested (early, before any other processing)
if [[ "$CLEAR_DB" == "true" ]]; then
  echo "Clearing database tables: message_cache, quarantined_message, gateway_case_record"

  # Static container name for docker-compose setup
  CONTAINER_NAME="fwmt-postgres"

  # Clear tables via podman exec, capturing both stdout and stderr
  OUTPUT=$(podman exec -e PGPASSWORD=postgres "$CONTAINER_NAME" psql -U postgres -d postgres -c \
    "TRUNCATE TABLE fwmtg.message_cache, fwmtg.quarantined_message, fwmtg.gateway_case_record CASCADE;" 2>&1)
  CLEAR_EXIT_CODE=$?

  if [[ $CLEAR_EXIT_CODE -eq 0 ]]; then
    echo "✓ Database tables cleared successfully"
    # If only -c was specified, exit after clearing
    if (( modes == 0 )); then
      exit 0
    fi
  else
    echo "✗ Failed to clear database tables. Make sure the postgres container ($CONTAINER_NAME) is running."
    if [[ -n "$OUTPUT" ]]; then
      echo "Error details: $OUTPUT"
    fi
    exit 2
  fi
fi

if [[ -n "$TYPE" ]]; then
  case "$TYPE" in
    HH_CREATE)
      # Use provided -f file or default fixture
      if [[ -z "$JSON_FILE" ]]; then
        JSON_FILE="${HH_DIR}/hhCreate.json"
      fi
      ;;
    CE_CREATE)
      # Use provided -f file or default fixture
      if [[ -z "$JSON_FILE" ]]; then
        JSON_FILE="${CE_DIR}/ceEstabCreate.json"
      fi
      ;;
    *)
      echo "Invalid TYPE '$TYPE' (use HH_CREATE or CE_CREATE)" >&2
      exit 2
      ;;
  esac
fi

if [[ -n "$JSON_FILE" ]]; then
   [[ -f "$JSON_FILE" ]] || { echo "JSON file not found: $JSON_FILE" >&2; exit 2; }
   RAW_JSON="$(cat "$JSON_FILE")"
fi

echo "$RAW_JSON" | jq -e . >/dev/null

# Check if JSON is already in Pub/Sub message format
IS_PUBSUB_FORMAT=$(echo "$RAW_JSON" | grep -c '{"messages":\[{"data":' 2>/dev/null || true)
[[ -z "$IS_PUBSUB_FORMAT" ]] && IS_PUBSUB_FORMAT=0

# Generate unique caseId (UUID v4)
UNIQUE_CASE_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"

# Skip all transformations if -n flag is set
if [[ "$NO_TRANSFORM" == "true" ]]; then
  # No transformations: publish exact JSON as-is
  UNIQUE_CASE_ID="(no caseId generated)"
else
  # Apply transformations based on message type (only if not already in Pub/Sub format)
  if [[ "$IS_PUBSUB_FORMAT" -eq 0 ]]; then
    # First: handle caseId (always replace for default fixtures, add only if missing otherwise)
    if [[ -n "$TYPE" && "$USER_PROVIDED_FILE" == "false" ]]; then
      # Default fixture: always replace caseId
      RAW_JSON=$(echo "$RAW_JSON" | jq ".caseId = \"$UNIQUE_CASE_ID\"")
    else
      # Custom file or inline JSON: add caseId only if missing
      RAW_JSON=$(echo "$RAW_JSON" | jq ".caseId = (.caseId // \"$UNIQUE_CASE_ID\")")
    fi

    # Then: handle type-specific fields (addressLevel, oa)
    if [[ "$TYPE" == "HH_CREATE" ]]; then
      # HH_CREATE: add addressLevel (default U) and oa
      RAW_JSON=$(echo "$RAW_JSON" | jq \
        ".addressLevel = (.addressLevel // \"U\") |
         .oa = (if .oa then (if .oa | startswith(\"N\") then .oa else .oa end) else \"E00167164\" end)")
    elif [[ "$TYPE" == "CE_CREATE" ]]; then
      # CE_CREATE: add addressLevel (default E)
      RAW_JSON=$(echo "$RAW_JSON" | jq ".addressLevel = (.addressLevel // \"E\")")
    elif [[ -z "$TYPE" && -n "$RAW_JSON" ]]; then
      # Using -m without -t: detect type from AddressType field and apply appropriate transformations
      ADDRESS_TYPE=$(echo "$RAW_JSON" | jq -r '.addressType // empty' 2>/dev/null || echo "")

      if [[ "$ADDRESS_TYPE" == "HH" ]]; then
        # Treat as HH_CREATE: add addressLevel (default U) and oa
        RAW_JSON=$(echo "$RAW_JSON" | jq \
          ".addressLevel = (.addressLevel // \"U\") |
           .oa = (if .oa then (if .oa | startswith(\"N\") then .oa else .oa end) else \"E00167164\" end)")
      elif [[ "$ADDRESS_TYPE" == "CE" ]]; then
        # Treat as CE_CREATE: add addressLevel (default E)
        RAW_JSON=$(echo "$RAW_JSON" | jq ".addressLevel = (.addressLevel // \"E\")")
      else
        # Default to HH-like behavior if AddressType is missing or unrecognized
        RAW_JSON=$(echo "$RAW_JSON" | jq \
          ".addressLevel = (.addressLevel // \"U\") |
           .oa = (if .oa then (if .oa | startswith(\"N\") then .oa else .oa end) else \"E00167164\" end)")
      fi
    fi
  fi
fi  # End of NO_TRANSFORM check

# Build complete Pub/Sub message with attributes
TS="$(date +%s)000"
B64="$(printf '%s' "$RAW_JSON" | base64 | tr -d '\n')"
URL="http://${EMULATOR}/v1/projects/${PROJECT}/topics/${TOPIC}:publish"

# Always add attributes
ATTRS="\"__TypeId__\":\"uk.gov.ons.census.fwmt.common.rm.dto.FwmtActionInstruction\",\"timestamp\":\"$TS\""

# Always publish in Pub/Sub format with attributes
FINAL_MESSAGE="{\"messages\":[{\"data\":\"${B64}\",\"attributes\":{$ATTRS}}]}"
curl -sS -X POST "$URL" \
  -H "Content-Type: application/json" \
  -d "$FINAL_MESSAGE" | jq .

echo ""
[[ -n "${JSON_FILE}" ]] && echo "Published fixture: ${JSON_FILE}"
if [[ "$NO_TRANSFORM" == "true" ]]; then
  echo "Published (no transformations applied)"
else
  echo "Case ID: $UNIQUE_CASE_ID"
fi
echo "✓ Published to topic '${TOPIC}' on local emulator '${EMULATOR}' (project '${PROJECT}')."