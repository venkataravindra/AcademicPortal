1.here our flow starts from /search endpoint in DoumentSearchController.java

2.from here flow will enter into DocumentSearchService.java here we are trying to get values of  category, product, status,entitym companyId, and remaining we are sending as null from out service it self 
3.this DocumentSearchService.java is the old flow we are trying to integrate
4.after integrating with this flow we are getting result as below:
{
    "total": 0,
    "results": []
}

5.but another developer avinash has developed a flow 

from TransactionConfirmationsController .java his flow is starting from /search endpoint in this class 
and response we are receiving is available at  response.json , similarly the old flow had to get fixed to work as endpoint present in TransactionConfirmationsController.java(/search)


but in old flow in DocumentSearchRequest.java we should not change any values / columns defined in it 

6. since in TransactionConfirmationsRepo.java we are executing query in side searchConfirmations method since in DocumentSearchRequest we dont have company i have changed it to companyId, except this dont change any thing in the flow 

7.in the query for category we are using request.getAccess() in DocumentSearchRequest , for 
a.documentType avinash is using strung it is refered as product in DocumentSearchRequest but here we are using DocumentType 
b.entity has been refered from request.getEntites()
c.companyId we are getting from request.getCompanyIds()
analyze he assignments and help to fix the issues , ultimately flow should return expexted values , the result has been handled properly dont make any changes in confirmationToMap() method of DocumentSearchService class 
i have attached logs in logs.txt file kondly help to analyze and fix  the issue
