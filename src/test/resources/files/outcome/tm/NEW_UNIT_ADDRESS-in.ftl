{
"transactionId":"b1646499-c5d8-4fbe-bb21-8e057601a3c2",
"siteCaseId":"3e007cdb-446d-4164-b2d7-8d8bd7b86c49",
"eventDate":"2020-04-17T11:53:11.000+0000",
"officerId":"SH-TWH1-ZA-25",
"coordinatorId":"SH-TWH1-ZA",
"primaryOutcomeDescription":"${primaryOutcomeDescription}",
"secondaryOutcomeDescription":"${secondaryOutcomeDescription}",
"outcomeCode":"${outcomeCode}",
<#if usualResidents??>
     ${usualResidents}
     ,
<#else>
     "ceDetails":null,
</#if>
"address":{
"addressLine1":"Unit name"
},
"accessInfo":null,
"careCodes":["CAT","DOG"],
 <#if (linkedQid??) || (fulfilmentRequested??)>
    "fulfilmentRequests":[
    <#if linkedQid??>
       ${linkedQid}
        <#if fulfilmentRequested??>
         ,
        </#if>
    </#if>
    <#if fulfilmentRequested??>
       ${fulfilmentRequested}
    </#if>
    ]
<#else>
      "fulfilmentRequests":null
</#if>
}