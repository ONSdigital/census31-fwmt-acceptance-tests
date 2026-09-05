{
"caseId":"${caseId}",
"transactionId":"${transactionId}",
"eventDate":"2020-04-17T11:53:11.000+0000",
"officerId":"SH-TWH1-ZA-25",
"coordinatorId":"SH-TWH1-ZA",
"primaryOutcomeDescription":"${primaryOutcomeDescription}",
"secondaryOutcomeDescription":"${secondaryOutcomeDescription}",
"outcomeCode":"${outcomeCode}",
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