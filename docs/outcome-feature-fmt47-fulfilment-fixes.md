# Outcome.feature changes — FMT-47 Pub/Sub migration + fulfilment validation

**Date:** 2026-06-15
**Scope:** `census31-fwmt-acceptance-tests` `Outcome.feature` + `OutcomeSteps` harness, plus the
`census31-fwmt-outcome-service` fixes that made the changes necessary.
**Runner verified:** `OutcomesTestRunner` — 0 Cucumber assertion failures, `BUILD SUCCESS`.

---

## 1. Background — why the feature file needed changing

During the FMT-47 Rabbit→Pub/Sub migration, two outcome-service bugs were fixed:

1. **TM `questionnaireType` → RM pack-code mapping** was missing. `FulfilmentRequestProcessor`
   passed the raw `questionnaireType` (e.g. `HUAC1`) straight to `ProductReference`, which only
   knows RM pack codes (e.g. `UACHHT1`). The lookup therefore failed and
   `FULFILMENT_REQUESTED` **threw** for every outcome that carried a paper/UAC fulfilment.
2. **Pub/Sub error handling** nacked failed messages with no dead-letter route, so a single
   failure was redelivered indefinitely and amplified processor counts.

Because `FULFILMENT_REQUESTED` used to throw, it **aborted the processor chain before the
following processors ran**. In the SPG/CE operation lists `FULFILMENT_REQUESTED` is immediately
followed by `LINKED_QID`, so `LINKED_QID` never executed and never emitted its
`PROCESSING_OUTCOME` event, and no fulfilment message was ever sent to RM.

The `Outcome.feature` `HasFulfilmentRequest=T` rows were therefore **calibrated to broken
behaviour**: they omitted `LINKED_QID` from the operation list and omitted `FULFILMENT_REQUESTED`
from the RM messages. Once the mapping was fixed, `FULFILMENT_REQUESTED` succeeded, the chain
continued, and the tests failed with off-by-one counts (`expected:<3> but was:<4>` etc.).

These feature changes update the expectations to reflect the **now-correct** service behaviour.

---

## 2. What the service actually does now (and the Confluence basis)

Both behaviours are confirmed by the design docs (see §5):

- A fulfilment **without** a `questionnaireId` (a `questionnaireType`, i.e. paper/UAC) is handled
  by `FULFILMENT_REQUESTED`, which sends a `FULFILMENT_REQUESTED` RM message whose
  `fulfilmentCode` is the **pack code** mapped from the questionnaire type.
- A fulfilment **with** a `questionnaireId` is handled by `LINKED_QID`, which sends a
  `QUESTIONNAIRE_LINKED` RM message.
- Both processors iterate the **same** `fulfilmentRequests` array, each handling only its own
  type. They run for the outcome regardless of outcome code, so both always appear in the
  operation list (and both emit `PROCESSING_OUTCOME` at the start of `process()`).

Consequently, for an outcome whose operation list contains both processors:

| Message contents | Processors that emit | RM messages produced |
|---|---|---|
| no fulfilment (`LQ=F, FR=F`) | both (no-op) | base only |
| linked QID only (`LQ=T, FR=F`) | both | base + `QUESTIONNAIRE_LINKED` |
| paper/UAC fulfilment only (`LQ=F, FR=T`) | both | base + `FULFILMENT_REQUESTED` |
| both (`LQ=T, FR=T`) | both | base + `FULFILMENT_REQUESTED` + `QUESTIONNAIRE_LINKED` |

("base" = whatever the outcome code itself emits, e.g. `ADDRESS_NOT_VALID`, `REFUSAL_RECEIVED`,
`FIELD_CASE_UPDATED`, or nothing.)

---

## 3. `Outcome.feature` changes

> **JsMessages were NOT changed.** No row's `JsMessages` column was edited — fulfilment/linked
> handling does not affect the job-service messages (`CANCEL` / `UPDATE`). All edits are to the
> **Operation List** and **RmMessages** columns only.

### 3.1 Operation List — `LINKED_QID` added

`LINKED_QID` was added to the operation list of every `HasFulfilmentRequest=T` row whose outcome
code's lookup (`outcomeCodeLookup.txt`) includes it — i.e. the `F|T` and `T|T` variants of the
codes below. (The `F|F` and `T|F` variants already listed it.)

| Survey | Business Function | Outcome Code | Rows updated (`LQ|FR`) |
|---|---|---|---|
| SPG | Not Valid Address | 6-30-03 | `F|T`, `T|T` |
| SPG | Extraordinary Refusal | 6-20-05 | `F|T`, `T|T` |
| SPG | Cancel Feedback | 22-20-05 | `F|T`, `T|T` |
| SPG | Delivered Feedback | 7-20-04 | `F|T`, `T|T` |
| SPG | No Action | 6-20-02 | `F|T`, `T|T` |
| CE | Not Valid Address | 20-10-05 | `F|T`, `T|T` |
| CE | Extraordinary Refusal | 20-20-03 | `F|T`, `T|T` |
| CE | Cancel Feedback | 22-20-05 | `F|T`, `T|T` |
| CE | Delivered Feedback | 7-20-04 | `F|T`, `T|T` |
| CE | Update Resident Count | 20-20-01 | `F|T`, `T|T` |
| CE | No Action | 25-30-02 | `F|T`, `T|T` |

**22 rows** total. `HH | No Action | 01-01-05` was **not** changed in the operation list because
its lookup is `NO_ACTION,FULFILMENT_REQUESTED` (no `LINKED_QID`).

### 3.2 RmMessages — `FULFILMENT_REQUESTED` added (paper/UAC fulfilment)

`FULFILMENT_REQUESTED` was added to the `RmMessages` of every `HasFulfilmentRequest=T` row whose
operation list contains `FULFILMENT_REQUESTED`.

| Survey | Business Function | Outcome Code | Row (`LQ|FR`) | RmMessages before | RmMessages after |
|---|---|---|---|---|---|
| SPG | Not Valid Address | 6-30-03 | `F|T` | `ADDRESS_NOT_VALID` | `ADDRESS_NOT_VALID,FULFILMENT_REQUESTED` |
| SPG | Extraordinary Refusal | 6-20-05 | `F|T` | `REFUSAL_RECEIVED` | `REFUSAL_RECEIVED,FULFILMENT_REQUESTED` |
| SPG | Cancel Feedback | 22-20-05 | `F|T` | *(empty)* | `FULFILMENT_REQUESTED` |
| SPG | Delivered Feedback | 7-20-04 | `F|T` | *(empty)* | `FULFILMENT_REQUESTED` |
| SPG | No Action | 6-20-02 | `F|T` | *(empty)* | `FULFILMENT_REQUESTED` |
| CE | Not Valid Address | 20-10-05 | `F|T` | `ADDRESS_NOT_VALID` | `ADDRESS_NOT_VALID,FULFILMENT_REQUESTED` |
| CE | Extraordinary Refusal | 20-20-03 | `F|T` | `REFUSAL_RECEIVED` | `REFUSAL_RECEIVED,FULFILMENT_REQUESTED` |
| CE | Cancel Feedback | 22-20-05 | `F|T` | *(empty)* | `FULFILMENT_REQUESTED` |
| CE | Delivered Feedback | 7-20-04 | `F|T` | *(empty)* | `FULFILMENT_REQUESTED` |
| CE | Update Resident Count | 20-20-01 | `F|T` | `FIELD_CASE_UPDATED` | `FIELD_CASE_UPDATED,FULFILMENT_REQUESTED` |
| CE | No Action | 25-30-02 | `F|T` | *(empty)* | `FULFILMENT_REQUESTED` |
| HH | No Action | 01-01-05 | `F|T` | *(empty)* | `FULFILMENT_REQUESTED` |

### 3.3 RmMessages — `FULFILMENT_REQUESTED` + `QUESTIONNAIRE_LINKED` added (both fulfilment types)

On the `T|T` rows (both a linked QID **and** a paper/UAC fulfilment present) both RM messages are
produced. `QUESTIONNAIRE_LINKED` was already expected on the `T|F` rows; here it is added
alongside the new `FULFILMENT_REQUESTED`.

| Survey | Business Function | Outcome Code | Row (`LQ|FR`) | RmMessages before | RmMessages after |
|---|---|---|---|---|---|
| SPG | Not Valid Address | 6-30-03 | `T|T` | `ADDRESS_NOT_VALID` | `ADDRESS_NOT_VALID,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| SPG | Extraordinary Refusal | 6-20-05 | `T|T` | `REFUSAL_RECEIVED` | `REFUSAL_RECEIVED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| SPG | Cancel Feedback | 22-20-05 | `T|T` | *(empty)* | `FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| SPG | Delivered Feedback | 7-20-04 | `T|T` | *(empty)* | `FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| SPG | No Action | 6-20-02 | `T|T` | *(empty)* | `FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| CE | Not Valid Address | 20-10-05 | `T|T` | `ADDRESS_NOT_VALID` | `ADDRESS_NOT_VALID,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| CE | Extraordinary Refusal | 20-20-03 | `T|T` | `REFUSAL_RECEIVED` | `REFUSAL_RECEIVED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| CE | Cancel Feedback | 22-20-05 | `T|T` | *(empty)* | `FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| CE | Delivered Feedback | 7-20-04 | `T|T` | *(empty)* | `FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| CE | Update Resident Count | 20-20-01 | `T|T` | `FIELD_CASE_UPDATED` | `FIELD_CASE_UPDATED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| CE | No Action | 25-30-02 | `T|T` | *(empty)* | `FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |

### 3.4 Rows deliberately left unchanged

- **Hard Refusal** rows (SPG `6-20-04`, CE `20-20-04`, HH `01-02-07`) keep empty Operation
  List / RmMessages. `HARD_REFUSAL_RECEIVED` runs first and requires a pre-existing cache entry;
  the DB is cleared between scenarios (`CLEARDB` in `@Before`), so it fails fast, emits nothing,
  and the message is dead-lettered. 0 emitted events still matches the empty expectations.
- **`HH | No Action | 01-01-04`** (`NO_ACTION,LINKED_QID`, no `FULFILMENT_REQUESTED`) — unchanged.
- All `HasFulfilmentRequest=F` rows — unchanged (no fulfilment message is sent when there is no
  fulfilment request; the processors still emit their `PROCESSING_OUTCOME` but produce no RM
  output).

---

## 4. Harness change — `OutcomeSteps.java`

`createExpectedRmMessages()` previously built **every** expected RM message with
`fulfilmentCode = outcomeCode`. The `FULFILMENT_REQUESTED-out.ftl` expected template is the only
template that uses `${fulfilmentCode}`, and the actual RM message carries the **pack code**, not
the outcome code. The harness now sets the pack code for the `FULFILMENT_REQUESTED` expected
message:

```java
private final static String FULFILMENT_REQUESTED_PACK_CODE = "UACHHT1";
...
root.put("fulfilmentCode",
    "FULFILMENT_REQUESTED".equals(rmMessageType) ? FULFILMENT_REQUESTED_PACK_CODE : outcomeCode);
```

`UACHHT1` is the pack code that outcome-service derives from `HUAC1`, the questionnaire type
hard-coded in `FULFILMENT_REQUESTED-in.ftl` (the only fulfilment input the suite sends). This is
what makes the `each message has the correct values` step actually validate the
questionnaireType→packCode mapping end-to-end.

---

## 5. Confluence cross-check — confirms, does not counter

The fixes were checked against the indexed design docs. **Nothing counters them; all are
confirmed.**

### Fulfilment Request — CATD 55220034 (`fwmt-interfaces-outcomes-rm-fulfilment-request`)
- *"Fulfilment requests may be attached to any outcome from TM, therefore these shall be
  processed irrespective of specific outcome code."* → confirms `FULFILMENT_REQUESTED` belongs in
  the operation list for every outcome code and sends an RM message when a fulfilment is present.
- *"This function specification is for the first type; i.e. where a `questionnaireId` HAS NOT been
  supplied."* → confirms `FULFILMENT_REQUESTED` handles only non-linked requests (matches the
  `!isQuestionnaireLinked` guard).
- *"the FWMT Gateway needs to map from the received information to the pack code required by the
  RM Case Event service … provided in a common library (`census-int-product-reference`)."* →
  confirms the RM message carries the **pack code**, validating both the service mapping fix and
  the harness `fulfilmentCode` fix.
- The page's own example pairs outcome code `7-20-04` (Delivered Feedback) with a `HUAC2`
  fulfilment → confirms fulfilments ride on feedback/no-action outcomes, not just address ones.

### Linked QID — CATD 55220019 (`fwmt-interfaces-outcomes-rm-linked-qid`)
- *"a linked QID shall be identified as those fulfilment items in the fulfilmentRequests array
  that have a `questionnaireId` set"* and maps to a `QUESTIONNAIRE_LINKED` message. → confirms
  `LINKED_QID` runs over the same array and, when both types are present (`T|T` rows), both
  `FULFILMENT_REQUESTED` and `QUESTIONNAIRE_LINKED` are produced.

### Household Outcome Interface — CATD page (`11-fieldwork-household-outcome-interface`)
- Contains the canonical **Questionnaire Type → Pack Code** table, including `HUAC1 → UACHHT1`.
  This is the source of `questionnaireTypeLookup.txt` and confirms the harness expects `UACHHT1`.
- Note: the source table flags a likely upstream typo for `HC2` (shown as `P_OR_HC3`); the
  service lookup uses `P_OR_HC2`.

---

## 6. Category A — other feature files (same pattern)

The same stale-expectation pattern was applied to three additional feature files. **JsMessages
were not changed** in any of them.

### 6.1 `OutcomeSwitch.feature` (`OutcomesSwitchRunner`)

Outcome codes `20-20-08` and `24-30-04` both end in `...,FULFILMENT_REQUESTED,LINKED_QID`.

| Outcome Code | Row (`LQ|FR`) | Operation List change | RmMessages change |
|---|---|---|---|
| 20-20-08 | `F\|T` | added `LINKED_QID` | added `FULFILMENT_REQUESTED` |
| 20-20-08 | `T\|T` | added `LINKED_QID` | added `FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |
| 24-30-04 | `F\|T` | added `LINKED_QID` | added `FULFILMENT_REQUESTED` |
| 24-30-04 | `T\|T` | added `LINKED_QID` | added `FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |

### 6.2 `OutcomeAddressTypeChange.feature` (`OutcomesAddressTypeChangeTestRunner`)

Outcome codes `6-10-03`, `6-10-01`, `20-10-01`, `20-10-03` — four rows each (`F|F`, `T|F`,
`F|T`, `T|T`). The `F|T` and `T|T` rows received `LINKED_QID` in the operation list and
`FULFILMENT_REQUESTED` in RmMessages; `T|T` rows also gained `QUESTIONNAIRE_LINKED` in
RmMessages. The HH `01-03-06` row is unchanged (lookup is `ADDRESS_TYPE_CHANGED_CE_EST` only).

### 6.3 `OutcomeHardRefusal.feature` (`OutcomesHardRefusalTestRunner`)

Only the SPG/CE hard-refusal rows with fulfilment (`6-20-04`, `20-20-04`) were updated:

| Outcome Code | Row (`LQ|FR`) | Operation List change | RmMessages change |
|---|---|---|---|
| 20-20-04 / 6-20-04 | `F\|T` | added `LINKED_QID` | added `FULFILMENT_REQUESTED` |
| 20-20-04 / 6-20-04 | `T\|T` | added `LINKED_QID` | added `FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED` |

Other hard-refusal rows (HH `01-02-07`, CE `01-02-07` visit, HH `21-20-14`, CE `01-03-07`) were
left unchanged — they do not carry a paper/UAC fulfilment on the `F|T`/`T|T` variants, or their
operation lists do not include `FULFILMENT_REQUESTED`.

---

## 7. Verification

| Runner | Before Category A | After Category A | Notes |
|---|---|---|---|
| `OutcomesTestRunner` | 23 AssertionErrors | **0** | Full pass |
| `OutcomesSwitchRunner` | 4 (`+1` pattern) | **0** | Full pass |
| `OutcomesAddressTypeChangeTestRunner` | 12 (8× `+1`, 4× RM short) | **12** → **0** (after Category C harness fix) | Category A expectations applied; Category C harness fix resolves new caseId from `ADDRESS_TYPE_CHANGED` queue message and matches `OUTCOME_SENT` events on **both** original and new caseIds. |
| `OutcomesHardRefusalTestRunner` | 14 (mixed) | **10** → **0** (after Category B harness fix + HH feature rows) | NC rows: harness now matches `PROCESSING_OUTCOME` on **both** NC and parent HH caseIds (`NO_ACTION`/`LINKED_QID` on HH, `CANCEL_FEEDBACK` on NC). HH `21-20-14` and `01-02-07` operation lists aligned with `outcomeCodeLookup.txt`. |
| `OutcomesNewAddressReportedRunner` | 20 (`was:<0>`) | **0** | Generated caseId resolved from `PREPROCESSING_*` event; fulfilment rows updated (§9). |
| `OutcomesSwitchCeSIteRunner` | 4 (`was:<0>`) | **0** | Same harness fix as new-address; `OutcomeSwitchCeSite.feature` fulfilment rows updated. |

Command:
`FWMT_MESSAGING=pubsub FWMT_TM_MOCK_PORT=18000 ./run-acceptance-test.sh <RunnerName>`

Log files from the Category A verification run (2026-06-15):
`scripts/logs/run-*-catA-20260615-*.log`

---

## 8. Category C — address-type-change new caseId routing (harness fix)

### Problem

Address-type-change processors (`ADDRESS_TYPE_CHANGED_*`) create a **new caseId** and return it to
the processor chain. Subsequent `FULFILMENT_REQUESTED` and `LINKED_QID` processors emit RM messages
and `OUTCOME_SENT` gateway events keyed to that **new** caseId. `ADDRESS_TYPE_CHANGED` itself stays
on the **original** parent caseId.

The harness filtered all `OUTCOME_SENT` events by `getMessageCaseId()` (the original caseId), so
scenarios expecting fulfilment or linked-QID RM messages failed at `confirmRmMessagesAreSent`
(`expected:<2/3> but was:<1>`) even though the Pub/Sub queue held the correct messages.

### Fix (`OutcomeSteps.java`)

1. **`resolveNewCaseIdFromAddressTypeChangedMessage()`** — for address-type-change flows, pull
   `ADDRESS_TYPE_CHANGED` from the RM queue first and parse `newCaseId` before collecting events.
2. **`matchesRmOutcomeEventCaseId()`** — match events on original caseId **or** the resolved
   `newCaseId`.
3. **`collectRmMessages()`** — skip message types already collected during resolution.
4. **`rmMessageTypeFromEvent()`** — read `"Template type"` metadata (with `"type"` fallback); the
   service emits `"Template type"`, not `"type"`.

No feature-file changes were required for Category C.

---

## 9. Category B — NC dual caseId + HH hard-refusal processor lists

### Problem (NC)

The outcome controller remaps NC TM payloads to the **original HH caseId** before processing.
Processors then log inconsistently:

| Processor | `PROCESSING_OUTCOME` caseId |
|---|---|
| `NO_ACTION`, `LINKED_QID` | original HH caseId |
| `CANCEL_FEEDBACK` | NC caseId (via `SwitchCaseIdService`) |

The harness filtered `PROCESSING_OUTCOME` events using only `ncCaseId`, so `NO_ACTION` scenarios
reported `expected:<1> but was:<0>`, and `CANCEL_FEEDBACK,LINKED_QID` rows reported
`expected:<2> but was:<1>`.

`COMET_NC_OUTCOME_RECEIVED` confirmation correctly continues to use `ncCaseId` via
`getMessageCaseId()`.

### Problem (HH hard refusal)

Two HH rows in `OutcomeHardRefusal.feature` listed fewer processors than
`outcomeCodeLookup.txt` defines:

| Outcome Code | Feature had | Lookup is |
|---|---|---|
| `21-20-14` (visit + linked QID) | `HARD_REFUSAL_RECEIVED` | `HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID` |
| `01-02-07` (phone) | `HARD_REFUSAL_RECEIVED` | `HARD_REFUSAL_RECEIVED,FEEDBACK_LONG_PAUSE` |

### Fix

**`OutcomeSteps.java`**

1. **`matchesProcessingEventCaseId()`** — accept events on parent HH caseId, NC caseId, or (for
   address-type-change) the new caseId.
2. **`matchesRmOutcomeEventCaseId()`** — also accept parent HH caseId for NC linked-QID RM events.
3. **`processorFromEvent()`** — read `"Processor"` metadata (service key) with `"processor"`
   fallback.

**`OutcomeHardRefusal.feature`** — updated HH `21-20-14` and `01-02-07` operation lists.

### Category B (continued) — new-address / switch-site generated caseId

New-unit and standalone outcomes assign `UUID.randomUUID()` in preprocessing
(`OutcomePreprocessingReceiver`). All subsequent `PROCESSING_OUTCOME`, `OUTCOME_SENT`, and
`RM_FIELD_REPUBLISH` events use that generated id. `getMessageCaseId()` returns `"N/A"` for these
flows (matching `COMET_*_OUTCOME_RECEIVED`), so event collection previously matched nothing
(`expected:<N> but was:<0>`).

**`OutcomeSteps.java` (additional changes)**

1. **`resolveGeneratedCaseIdFromPreprocessingEvent()`** — after the outcome is received, read the
   generated caseId from the matching `PREPROCESSING_*` gateway event (filtered by survey type and
   outcome code).
2. **`isGeneratedCaseIdFlow()`** — `New Unit Reported`, `New Standalone Address`,
   `Switch Feedback Site`.
3. Unified **`newCaseId`** matching in `matchesProcessingEventCaseId`, `matchesRmOutcomeEventCaseId`,
   and new `matchesJsOutcomeEventCaseId`.

**Feature files (Category A fulfilment rows on top of Category B harness fix)**

- `OutcomeNewAddressReported.feature` — `LINKED_QID` + `FULFILMENT_REQUESTED` / `QUESTIONNAIRE_LINKED`
  on `F|T` / `T|T` rows for outcome codes `9-20-01`, `23-20-01`, `26-20-01`.
- `OutcomeSwitchCeSite.feature` — same on `23-20-01` `F|T` / `T|T` rows.

| Runner | Before | After |
|---|---|---|
| `OutcomesNewAddressReportedRunner` | 20 (`was:<0>`) | **0** |
| `OutcomesSwitchCeSIteRunner` | 4 (`was:<0>`) | **0** |

### Related changes (context, not in this feature file)
- `outcome-service`: `OutcomePreprocessingExceptionHandler` (ack + retry/DLQ instead of infinite
  nack), `GatewayCacheService.saveAndFlush` (idempotent within-transaction caching),
  `questionnaireTypeLookup` + `ProductReferenceBootstrapConfig`.
- `acceptance-tests/scripts/setup-pubsub.sh`: dead-letter policy on the outcome preprocessing
  subscription.
