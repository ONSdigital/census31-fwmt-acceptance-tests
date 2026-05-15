{
"event":{
"type":"NEW_ADDRESS_REPORTED",
"source":"FIELDWORK_GATEWAY",
"channel":"FIELD",
"dateTime":"2020-04-17T12:53:11.000+01",
"transactionId":"b1646499-c5d8-4fbe-bb21-8e057601a3c2"
},
"payload":{
"newAddress":{
"sourceCaseId": "3e007cdb-446d-4164-b2d7-8d8bd7b86c49",
"collectionCase" : {
"id":"3e007cdb-446d-4164-b2d7-8d8bd7b86c49",
"caseType": "${surveyType}",
"survey": "CENSUS",
"fieldCoordinatorId":"SH-TWH1-ZA",
"fieldOfficerId":"SH-TWH1-ZA-25",
"address":{
"addressLine1" : "Unit name",
"addressLine2" : null,
"addressLine3" : null,
"townName" : null,
"postcode" : null,
"region" : "E",
"addressType" : "${surveyType}",
"addressLevel" : "U"
}
<#if surveyType == "CE">
,"ceExpectedCapacity" : "${newAddressUsualResidents?c}"
</#if>
}
}
}
}