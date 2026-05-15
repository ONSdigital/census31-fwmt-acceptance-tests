{
"accessInfo":"Jump the gate!",
"address":{
"addressLine1":"Unit name",
"addressLine2":"Some house name",
"addressLine3":"some road",
"locality":"some town",
"postcode":"AA1 2BB",
"latitude":99.99999999,
"longitude":99.99999999
},
"careCodes":["Big Angry Dog","Slippery when wet"],

"coordinatorId":"SH-TWH1-ZA",
"dummyInfo":true,
"eventDate":"2020-04-17T11:53:11.000+0000",

"officerId":"SH-TWH1-ZA-25",
"outcomeCode":"${outcomeCode}",
"primaryOutcomeDescription":"${primaryOutcomeDescription}",
"secondaryOutcomeDescription":"${secondaryOutcomeDescription}",
"transactionId":"b1646499-c5d8-4fbe-bb21-8e057601a3c2",

   <#if surveyType == "CE">
     "ceDetails":{
       "establishmentName":"The last rest",
       "establishmentType":"Care Home",
       "establishmentSecure":"true",
       "managerTitle":"Mr",
       "managerForename":"John",
       "managerSurname":"Smith",
       <#if usualResidents??>
         "usualResidents": 5        ,
       </#if>
       "contactPhone":"07977123456"
      },
  <#else>
   
    <#if usualResidents??>
       ${usualResidents}
      ,
    <#else>
       "ceDetails":null,
    </#if>
  </#if>


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