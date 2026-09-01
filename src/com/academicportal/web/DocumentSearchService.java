package com.dbs.edoc.docsearch.service.search;

import com.dbs.edoc.config.DynamicStringProperty;
import com.dbs.edoc.docsearch.api.request.DashboardChartDataRequest;
import com.dbs.edoc.docsearch.api.request.DateAttribute;
import com.dbs.edoc.docsearch.api.request.DocumentSearchRequest;
import com.dbs.edoc.docsearch.api.request.DocumentType;
import com.dbs.edoc.docsearch.api.request.external.ExternalDocumentSearchRequest;
import com.dbs.edoc.docsearch.api.response.DashboardChartDataResponse;
import com.dbs.edoc.docsearch.api.response.DocumentLastUploadResponse;
import com.dbs.edoc.docsearch.api.response.EdocSearchResponse;
import com.dbs.edoc.docsearch.api.response.external.ExternalDocumentSearchRecord;
import com.dbs.edoc.docsearch.exception.ServiceException;
import com.dbs.edoc.docsearch.service.auth.LdapUser;
import com.dbs.edoc.docsearch.service.auth.MarsUser;
import com.dbs.edoc.docsearch.service.auth.User;
import com.dbs.edoc.docsearch.service.auth.UserService;
import com.dbs.edoc.docsearch.service.search.helper.DashboardChartDataAggregationHelper;
import com.dbs.edoc.docsearch.ui.model.TransactionConfirmations;
import com.dbs.edoc.docsearch.ui.service.TransactionConfirmationsService;
import com.dbs.edoc.docsearch.utils.DocumentSearchConstants;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.LocalDateTime.now;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.upperCase;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.search.sort.SortBuilders.fieldSort;
import static org.springframework.http.ResponseEntity.ok;

@Component
public class DocumentSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentSearchService.class);
    private static final String FIELD_CREATE_TIMESTAMP = "createTimestamp";
    private static final String FIELD_UPLOAD_DATETIME_SGT = "uploadDatetimeSgt";
    private static final String ENTITY_CODE = "entityCode";
    private static final String STATUS = "status";
    private static final String MX_BOOKING_ENTITY = "mxBookingEntity";
    private static final DynamicStringProperty IDW_EDOC_ENTITY_MAPPING = new DynamicStringProperty("idw.edoc.entity.mapping",
            "{ \"CN\": \"DBSCN\", \"HK-BR\": \"DBSHKBR\", \"HK-LTD\": \"DBSHK\", \"IND\": \"DBSIN\", \"SG\": \"DBSSG\", \"TW\": \"DBSTW\" }");
    private static final String IS_REVISED = "isRevised";
    private static final String CANCELLED = "CANCELLED";

    private final AtomicReference<Map<String, String>> entityMappingRef;
    private SearchConfigService configService;
    private UserService userService;
    private ExcelBuilder excelBuilder;
    private TransactionConfirmationsService transactionConfirmationsService;
    private Gson gson;

    @Autowired
    public DocumentSearchService(SearchConfigService configService,
                                 UserService userService,
                                 ExcelBuilder excelBuilder,
                                 TransactionConfirmationsService transactionConfirmationsService) {
        this.configService = configService;
        this.userService = userService;
        this.excelBuilder = excelBuilder;
        this.transactionConfirmationsService = transactionConfirmationsService;
        this.gson = new Gson();

        this.entityMappingRef = new AtomicReference<>(populateEntityMappingMap());
        final Runnable createEntityMappingMap = () -> entityMappingRef.set(populateEntityMappingMap());
        IDW_EDOC_ENTITY_MAPPING.addCallback(createEntityMappingMap);
    }

    public DocumentLastUploadResponse getLastUploadTime(DocumentSearchRequest request) {
        request.setOffset(0);
        request.setPageSize(1);
        request.setSortField(FIELD_UPLOAD_DATETIME_SGT);
        request.setSortAscending(false);

        String lastUploadStr = buildSearchRequest(request).map(page ->
                page.hasContent() ? Objects.toString(page.getContent().get(0).getUploadDatetimeSgt(), null) : null
        ).orElse(null);

        DocumentLastUploadResponse response = new DocumentLastUploadResponse();
        response.setData(lastUploadStr);
        return response;
    }

    public EdocSearchResponse<Map<String, Object>> search(DocumentSearchRequest request) {
        // allow max of 100 records for normal search request
        request.setPageSize(Math.min(100, request.getPageSize()));
        return buildSearchRequest(request).map(this::buildSearchResponse).orElse(EdocSearchResponse.empty());
    }

    public ResponseEntity<byte[]> export(DocumentSearchRequest request) {
        request.setOffset(0);
        request.setPageSize(65000);
        return buildSearchRequest(request).map(page -> {
            EdocSearchResponse<Map<String, Object>> searchResponse = buildSearchResponse(page);
            String filename = String.format("SearchResult_%s.%s", now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")), "xls");
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            responseHeaders.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
            return ok().headers(responseHeaders).body(excelBuilder.build(request, searchResponse));
        }).orElse(null);
    }


    public EdocSearchResponse<ExternalDocumentSearchRecord> externalSystemDocumentSearch(ExternalDocumentSearchRequest request) {

        LOGGER.info("External Request Received to search documents [{}]", request);
        DocumentSearchRequest documentSearchRequest = new DocumentSearchRequest();

        Set<String> documentClasses = new HashSet<>();
        documentClasses.add(request.getDocumentClass());

        final String resolvedEntity = entityMappingRef.get().get(request.getLegalEntityCode().trim());
        if (resolvedEntity == null) {
            throw new IllegalArgumentException("No EDOC entity Mapping found for IDW Entity [" + request.getLegalEntityCode() + "]");
        }

        Set<String> entityCodes = new HashSet<>();
        entityCodes.add(resolvedEntity.trim());

        Set<String> cinCifs = new HashSet<>();
        cinCifs.add(request.getLcin().trim());
        documentSearchRequest.setCinCifs(cinCifs);

        if (request.getExcludeStatuses().length > 0) {
            Set<String> excludeStatuses = new HashSet<>(Arrays.asList(request.getExcludeStatuses()));
            documentSearchRequest.setExcludeStatuses(excludeStatuses);
        }

        if (request.getCobStartDate() != null || request.getCobEndDate() != null) {
            List<DateAttribute> dateAttributes = new ArrayList<>();
            DateAttribute cobAttribute = new DateAttribute();
            cobAttribute.setName(request.getCobAttributeName());
            cobAttribute.setFrom(request.getCobStartDate());
            cobAttribute.setTo(request.getCobEndDate());
            dateAttributes.add(cobAttribute);
            documentSearchRequest.setDateAttributes(dateAttributes);
        }

        User user = new User();
        user.setInternal(false);

        LOGGER.info("Executing the count Query to count all Search Hits");
        SearchSourceBuilder countRequestBuilder = buildEdocQuery(documentClasses, entityCodes, Collections.emptySet(), Collections.emptySet(), documentSearchRequest, user);
        final SearchRequest countRequest = configService.createSearchRequest(countRequestBuilder);
        final EdocSearchResponse<Map<String, Object>> initialSearch = buildSearchResponse(countRequest);
        documentSearchRequest.setPageSize(Math.toIntExact(initialSearch.getTotal()));

        LOGGER.info("Found [{}] Search Hits for the request", initialSearch.getTotal());

        LOGGER.info("Executing Search query with Request Count [{}]", documentSearchRequest.getPageSize());
        SearchSourceBuilder searchBuilder = buildEdocQuery(documentClasses, entityCodes, Collections.emptySet(), Collections.emptySet(), documentSearchRequest, user);
        final SearchRequest searchRequest = configService.createSearchRequest(searchBuilder);

        final List<ExternalDocumentSearchRecord> externalDocumentSearchRecords = buildSearchResponse(searchRequest)
                .getResults().stream().map(this::mapSearchRecordToIbgResponse)
                .collect(Collectors.toList());

        EdocSearchResponse<ExternalDocumentSearchRecord> response = new EdocSearchResponse<>();
        response.setResults(externalDocumentSearchRecords);
        response.setTotal(externalDocumentSearchRecords.size());
        LOGGER.info("External Search Document request Completed with [{}] documents", response.getTotal());
        return response;
    }


    private Optional<Page<TransactionConfirmations>> buildSearchRequest(DocumentSearchRequest request) {
        LOGGER.info("=== IMMEDIATE REQUEST OBJECT DEBUG ===");
        LOGGER.info("Request toString: {}", request);
        LOGGER.info("request.getCompanyIds() direct call: {}", request.getCompanyIds());
        LOGGER.info("request.getStatuses() direct call: {}", request.getStatuses());
        LOGGER.info("request.getEntityCodes() direct call: {}", request.getEntityCodes());
        LOGGER.info("request.getAccess() direct call: {}", request.getAccess());

        // Check if they're null vs empty
        LOGGER.info("CompanyIds == null? {}", request.getCompanyIds() == null);
        LOGGER.info("Statuses == null? {}", request.getStatuses() == null);
        LOGGER.info("CompanyIds.isEmpty()? {}", request.getCompanyIds().isEmpty());
        LOGGER.info("Statuses.isEmpty()? {}", request.getStatuses().isEmpty());


        Set<DocumentType> documentTypes = request.getDocumentTypes();
        Set<String> productTypeNames = documentTypes.stream()
                .flatMap(documentType -> documentType.getProductTypeNames().stream())
                .collect(Collectors.toSet());
        if (request.getDocumentTypes().isEmpty()) {
            throw new IllegalArgumentException("Requires at least 1 document type");
        }

        // Validate that the first parameter (access/category) is provided
        if (StringUtils.isBlank(request.getAccess())) {
            LOGGER.warn("Search request rejected: Access/Category parameter is required");
            throw new IllegalArgumentException("Access/Category parameter is required for search");
        }

        // PRESERVE ORIGINAL REQUEST VALUES IMMEDIATELY
        Set<String> originalEntityCodes = new HashSet<>(request.getEntityCodes());
        Set<String> originalCompanyIds = new HashSet<>(request.getCompanyIds());
        Set<String> originalStatuses = new HashSet<>(request.getStatuses());

        LOGGER.info("=== PRESERVED ORIGINAL VALUES ===");
        LOGGER.info("Preserved EntityCodes: {}", originalEntityCodes);
        LOGGER.info("Preserved CompanyIds: {}", originalCompanyIds);
        LOGGER.info("Preserved Statuses: {}", originalStatuses);

        Set<String> entityCodes = request.getEntityCodes();
        Set<String> companyIds = request.getCompanyIds();
        Set<String> documentClasses = new HashSet<>();
        Set<String> productTypes = new HashSet<>();
        Set<String> accessibleEntities = new HashSet<>();

        // check permission
        User user = userService.currentUser();
        LOGGER.info("Performing search under user id: {}", user.getUserId());
        LOGGER.info("Primary search parameter - Category/Access: '{}'", request.getAccess());

        if (user instanceof LdapUser) {
            LdapUser ldapUser = (LdapUser) user;

            Map<String, Set<String>> entityDocumentMap = populateAccessLevels(ldapUser);
            LOGGER.info("Document and entities user has access to : {}", entityDocumentMap);
            addEntityDocumentProducts(entityDocumentMap, documentTypes, entityCodes, documentClasses, productTypes, accessibleEntities, user);
            LOGGER.info("Searching data under doc classes and entity codes [{}] for User {} and accessible entities {} ",
                    entityDocumentMap, ldapUser.getUserId(), accessibleEntities);
        } else {
            if (filterForExternal(documentTypes, entityCodes, companyIds, documentClasses, productTypes, (MarsUser) user))
                return Optional.empty();
        }

        Pageable pageable = buildConfirmationsPageable(request);
        LOGGER.info("Search parameters - documentTypes: {}, entityCodes: {}, companyIds: {}, statuses: {}",
                documentTypes, entityCodes, originalCompanyIds, originalStatuses); // ← Use original values

        LOGGER.info("Accessible entities: {}, Document classes: {}, productTypeNames: {}", accessibleEntities, documentClasses, productTypeNames);

        // Prepare filter parameters using ORIGINAL values
        String product = productTypeNames.isEmpty() ? null : productTypeNames.iterator().next();
        String status = originalStatuses.isEmpty() ? null : originalStatuses.iterator().next(); // ← Use original
        String entity = accessibleEntities.isEmpty() ? null : accessibleEntities.iterator().next();
        String company = originalCompanyIds.isEmpty() ? null : originalCompanyIds.iterator().next(); // ← Use original

        if (DocumentSearchConstants.DistributionType.EMAIL.equalsIgnoreCase(request.getAccess())) {
            LOGGER.info("=== EMAIL SEARCH DEBUG ===");
            LOGGER.info("Required Parameter - Category: '{}'", request.getAccess());
            LOGGER.info("Optional Filter - ProductTypeNames: {}", productTypeNames);
            LOGGER.info("Optional Filter - AccessibleEntities: {}", accessibleEntities);
            LOGGER.info("Optional Filter - CompanyIds: {}", originalCompanyIds);
            LOGGER.info("Optional Filter - Statuses: {}", originalStatuses);

            LOGGER.info("Final EMAIL search parameters - category: '{}' (REQUIRED), product: '{}', status: '{}', entity: '{}', company: '{}'",
                    request.getAccess(), product, status, entity, company);

            return Optional.of(transactionConfirmationsService.searchConfirmations(
                    request.getAccess(),
                    product,
                    status,
                    entity,
                    company,
                    null, null, null, null, null, pageable));
        }

        if(user instanceof MarsUser) {
            LOGGER.info("External user - entities user has access to : {}", entityCodes);
            accessibleEntities = entityCodes;
            // Recalculate entity filter for external users
            entity = accessibleEntities.isEmpty() ? null : accessibleEntities.iterator().next();
        }

        LOGGER.info("=== EDOC SEARCH DEBUG ===");
        LOGGER.info("Required Parameter - Category: '{}'", request.getAccess());
        LOGGER.info("Optional Filter - ProductTypeNames: {}", productTypeNames);
        LOGGER.info("Optional Filter - AccessibleEntities: {}", accessibleEntities);
        LOGGER.info("Optional Filter - CompanyIds: {}", originalCompanyIds);     // ← FIXED: Use original
        LOGGER.info("Optional Filter - Statuses: {}", originalStatuses);         // ← FIXED: Use original
        LOGGER.info("##Accessible entities##: {}, Document classes: {}, productTypeNames: {}", accessibleEntities, documentClasses, productTypeNames);

        LOGGER.info("Final EDOC search parameters - category: '{}' (REQUIRED), product: '{}', status: '{}', entity: '{}', company: '{}'",
                request.getAccess(), product, status, entity, company);

        return Optional.of(transactionConfirmationsService.searchConfirmations(
                request.getAccess(),
                product,
                status,
                entity,
                company,
                null, null, null, null, null, pageable));
    }





    private Pageable buildConfirmationsPageable(DocumentSearchRequest request) {
        int pageSize = Math.max(request.getPageSize(), 1);
        int pageNumber = request.getOffset() / pageSize;
        // sortField historically named an Elasticsearch document attribute; map the
        // known ES-only field to its TransactionConfirmations JPA equivalent.
        String sortField = FIELD_CREATE_TIMESTAMP.equals(request.getSortField())
                ? FIELD_UPLOAD_DATETIME_SGT
                : request.getSortField();
        Sort sort = StringUtils.isNotEmpty(sortField)
                ? Sort.by(request.isSortAscending() ? Sort.Direction.ASC : Sort.Direction.DESC, sortField)
                : Sort.unsorted();
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    private EdocSearchResponse<Map<String, Object>> buildSearchResponse(Page<TransactionConfirmations> page) {
        EdocSearchResponse<Map<String, Object>> response = new EdocSearchResponse<>();
        response.setTotal(page.getTotalElements());
        response.setResults(page.getContent().stream().map(this::confirmationToMap).collect(Collectors.toList()));
        return response;
    }

    private Map<String, Object> confirmationToMap(TransactionConfirmations confirmation) {
        Map<String, Object> map = new HashMap<>();

        // Existing SIT fields
        map.put("id", confirmation.getId());
        map.put("docId", confirmation.getDoc_id());
        map.put("category", confirmation.getCategory());
        map.put("txnRef", confirmation.getTxnRef());
        map.put("txnEventDate", confirmation.getTxnEventDate());
        map.put("maturityPaymentDate", confirmation.getMaturityPaymentDate());
        map.put("company", confirmation.getCompany());
        map.put("entity", confirmation.getEntity());
        map.put("product", confirmation.getProduct());
        map.put("documentType", confirmation.getDocumentType());
        map.put("ccy", confirmation.getCcy());
        map.put("status", confirmation.getStatus());
        map.put("lastApprovedRejected", confirmation.getLastApprovedRejected());
        map.put("uploadDatetimeSgt", confirmation.getUploadDatetimeSgt());
        map.put("emailDatetimeSgt", confirmation.getEmailDatetimeSgt());
        map.put("action", confirmation.getAction());
        map.put("uniqueKey", confirmation.getUniqueKey());
        map.put("contentMd5", confirmation.getContentMd5());
        map.put("mimeType", confirmation.getMimeType());
        map.put("type", confirmation.getType());
        map.put("isRevised", confirmation.getIsRevised());
        map.put("cinCif", confirmation.getCinCif());
        map.put("companyId", confirmation.getCompanyId());
        map.put("name", confirmation.getName());
        map.put("dupCheckMd5", confirmation.getDupCheckMd5());
        map.put("userType", confirmation.getUserType());
        map.put("murexLabel", confirmation.getMurexLabel());
        map.put("levelsOfApproval", confirmation.getLevelsOfApproval());
        map.put("tradeDate", confirmation.getTradeDate());
        map.put("updateTimestamp", confirmation.getUpdateTimestamp());
        try {
            map.put("tradeReference", confirmation.getTxnRef());
            map.put("companyName", confirmation.getCompany());
            map.put("maturityOrPaymentDate", confirmation.getMaturityPaymentDate());
            map.put("createTimestamp", confirmation.getUploadDatetimeSgt());
            map.put("maturityDate", confirmation.getMaturityPaymentDate());
            map.put("productTypeName", confirmation.getProduct());
            map.put("entityCode", confirmation.getEntity());
            map.put("tradeOrEventDate", confirmation.getTxnEventDate() != null ? confirmation.getTxnEventDate() : confirmation.getTradeDate());
            map.put("cinOrCif", confirmation.getCinCif());
            map.put("createOrUpdateTimestamp", confirmation.getUpdateTimestamp());
            map.put("productCode", confirmation.getProduct());
            map.put("ackStatus", confirmation.getStatus());
        } catch(Exception e){
            e.getMessage();
        }

        return map;
    }

    private void addEntityDocumentProducts(Map<String, Set<String>> entityDocumentMap, Set<DocumentType> documentTypes,
                                           Set<String> entityCodes, Set<String> documentClasses, Set<String> productTypes,
                                           Set<String> accessibleEntities, User user) {
        LdapUser ldapUser = (LdapUser) user;
        for (DocumentType documentType : documentTypes) {
            String documentClassName = documentType.getClassName();

            // Add mapping for TRANSACTIONCONFIRMATION to CONFIRMATION
            String mappedDocumentClass = documentClassName;
            if ("TRANSACTIONCONFIRMATION".equalsIgnoreCase(documentClassName)) {
                mappedDocumentClass = "CONFIRMATION";
            }

            LOGGER.info("User {} searching for document type: {}, mapped to permission type: {}",
                    ldapUser.getUserId(), documentClassName, mappedDocumentClass);

            if(entityDocumentMap.containsKey(mappedDocumentClass.toUpperCase())) {
                documentClasses.add(upperCase(documentClassName)); // Use original class name
                productTypes.addAll(documentType.getProductTypeNames().stream().map(StringUtils::upperCase).collect(Collectors.toSet()));
                LOGGER.info("User {} Requested to search on these entities: {}", ldapUser.getUserId(), entityCodes);

                if(!entityCodes.isEmpty()) {
                    // Filter requested entities against user's accessible entities for this document type
                    Set<String> userAccessibleEntities = entityDocumentMap.get(mappedDocumentClass.toUpperCase());
                    Set<String> filteredEntities = entityCodes.stream()
                            .filter(entity -> userAccessibleEntities.contains(entity))
                            .collect(Collectors.toSet());
                    accessibleEntities.addAll(filteredEntities);
                    LOGGER.info("User {} can search on these filtered entities: {}", ldapUser.getUserId(), filteredEntities);
                } else {
                    // If no specific entities requested, add all accessible entities for this document type
                    accessibleEntities.addAll(entityDocumentMap.get(mappedDocumentClass.toUpperCase()));
                    LOGGER.info("User {} can only search on these accessibleEntities entities: {} for doctype : {}",
                            ldapUser.getUserId(), accessibleEntities, mappedDocumentClass);
                }
            } else {
                LOGGER.warn("User {} does not have access to document type: {} (mapped: {})",
                        ldapUser.getUserId(), documentClassName, mappedDocumentClass);
            }
        }
        LOGGER.info("User {} final accessible entities: {}", ldapUser.getUserId(), accessibleEntities);
    }


    private static Map<String, Set<String>> populateAccessLevels(LdapUser ldapUser) {
        Map<String, Set<String>> documentPermissions = new HashMap<>();
        ldapUser.getPermissions().forEach(permission -> {
            if (StringUtils.equalsIgnoreCase("OPSADMIN", permission.getGroupType()) || StringUtils.equalsIgnoreCase("OPSUSER", permission.getGroupType())) {
                if (!documentPermissions.containsKey(upperCase(permission.getDocumentClass()))) {
                    documentPermissions.put(upperCase(permission.getDocumentClass()), new HashSet<>());
                }
                documentPermissions.get(upperCase(permission.getDocumentClass())).add(permission.getEntityCode());
            }
        });
        return documentPermissions;
    }

    private boolean filterForExternal(Set<DocumentType> documentTypes, Set<String> entityCodes, Set<String> companyIds, Set<String> documentClasses, Set<String> productTypes, MarsUser user) {
        if (entityCodes.size() != 1 || companyIds.size() != 1) {
            throw new IllegalArgumentException("External search must specify exactly 1 entity and 1 company");
        }
        String entityCode = entityCodes.iterator().next();
        String companyId = companyIds.iterator().next();

        // check entity and company permission
        List<MarsUser.Permission> permissions = user.getCompanies().stream()
                .filter(e -> StringUtils.equalsIgnoreCase(e.getEntityCode(), entityCode) && StringUtils.equalsIgnoreCase(e.getCompanyId(), companyId))
                .map(MarsUser.CompanyProfile::getPermissions)
                .findFirst().orElse(Collections.emptyList());
        if (permissions.isEmpty()) {
            LOGGER.info("User {} does not have access to entity {}, company {}", user.getUserId(), entityCode, companyId);
            return true;
        }

        // check category and product permission
        for (DocumentType documentType : documentTypes) {
            List<String> accessibleProductTypes = permissions.stream()
                    .filter(permission -> StringUtils.equalsIgnoreCase(permission.getCategory(), documentType.getCategoryName()))
                    .map(MarsUser.Permission::getProduct)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            List<String> requestedProductTypes = documentType.getProductTypeNames().stream().map(String::toLowerCase).collect(Collectors.toList());
            if (requestedProductTypes.isEmpty()) {
                requestedProductTypes = accessibleProductTypes;
            } else if (requestedProductTypes.retainAll(accessibleProductTypes)) {
                LOGGER.info("User {} can only search on these products: {}. Requested products: {}", user.getUserId(), accessibleProductTypes,
                        documentType.getProductTypeNames());
            }
            if (!requestedProductTypes.isEmpty()) {
                documentClasses.add(documentType.getClassName());
                productTypes.addAll(requestedProductTypes);
            }
        }
        if (documentClasses.isEmpty() || productTypes.isEmpty()) {
            LOGGER.info("User does not have access to any of the requested document classes {} and products {}",
                    documentTypes.stream().map(DocumentType::getClassName).collect(Collectors.toList()),
                    documentTypes.stream().flatMap(e -> e.getProductTypeNames().stream()).collect(Collectors.toList()));
            return true;
        }
        return false;
    }

    private SearchSourceBuilder buildEdocQuery(Set<String> documentClasses, Set<String> entityCodes,
                                               Set<String> companyIds, Set<String> productTypes, DocumentSearchRequest request, User user) {

        SearchSourceBuilder builder = SearchSourceBuilder.searchSource().from(request.getOffset()).size(request.getPageSize());

        // sorting
        if (StringUtils.isNotEmpty(request.getSortField())) {
            FieldSortBuilder fieldSort = new FieldSortBuilder(request.getSortField())
                    .order(request.isSortAscending() ? SortOrder.ASC : SortOrder.DESC)
                    .missing("_last").unmappedType("keyword");
            builder.sort(fieldSort);
        }

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        builder.query(queryBuilder);

        queryBuilder.must(termsQuery("type", documentClasses));
        queryBuilder.must(termsQuery(ENTITY_CODE, entityCodes));
        if (hasAnyItems(productTypes)) {
            queryBuilder.must(termsQuery("productTypeName", productTypes));
        }
        if (hasAnyItems(companyIds)) {
            queryBuilder.must(termsQuery("companyId", companyIds));
        }
        if (hasAnyItems(request.getStatuses())) {
            queryBuilder.must(termsQuery(STATUS, request.getStatuses()));
        }
        if (!user.isInternal()) {
            queryBuilder.mustNot(termQuery("ackStatus", CANCELLED)); // exclude CANCELLED documents for external users
        }
        if (hasAnyItems(request.getCinCifs())) {
            queryBuilder.must(termsQuery("cinOrCif", request.getCinCifs()));
        }
        if(hasAnyItems(request.getExcludeStatuses())) {
            queryBuilder.mustNot(termsQuery(IS_REVISED,request.getExcludeStatuses()));
        }

        queryBuilder.mustNot(termQuery(STATUS, "ARCHIVED")); // exclude ARCHIVED documents
        filterDynamicAttributes(queryBuilder, request);
        return builder;
    }


    private SearchSourceBuilder buildNonEdocQuery(Set<String> documentClasses, Set<String> entityCodes,
                                                  Set<String> companyIds, Set<String> productTypes, DocumentSearchRequest request) {

        SearchSourceBuilder builder = SearchSourceBuilder.searchSource().from(request.getOffset()).size(request.getPageSize());

        // sorting
        if (StringUtils.isNotEmpty(request.getSortField())) {
            FieldSortBuilder fieldSort = new FieldSortBuilder(request.getSortField())
                    .order(request.isSortAscending() ? SortOrder.ASC : SortOrder.DESC)
                    .missing("_last").unmappedType("keyword");
            builder.sort(fieldSort);
        }

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        builder.query(queryBuilder);

        queryBuilder.must(termsQuery("type", documentClasses));
        queryBuilder.must(termsQuery(ENTITY_CODE, entityCodes));
        if (hasAnyItems(companyIds)) {
            queryBuilder.must(termsQuery("companyName", companyIds));
        }
        if (documentClasses.contains(DocumentSearchConstants.NonEdocDocumentTypes.NE_PORTFOLIO_STATEMENTS.toLowerCase()) ||
                documentClasses.contains(DocumentSearchConstants.NonEdocDocumentTypes.NE_STATEMENT_OF_ACCOUNT.toLowerCase())) {
            productTypes = Set.of("ALL");
        }
        if (hasAnyItems(productTypes)) {
            queryBuilder.must(termsQuery("productTypeName", productTypes));
        }

        if (hasAnyItems(request.getStatuses())) {
            queryBuilder.must(termsQuery(IS_REVISED, request.getStatuses()));
        }
        filterDynamicAttributes(queryBuilder, request);
        LOGGER.info("Non-edoc document search query {} {}",request, builder);
        return builder;
    }

    private void filterDynamicAttributes(BoolQueryBuilder queryBuilder, DocumentSearchRequest request) {
        request.getDateAttributes().forEach(attribute -> {
            RangeQueryBuilder rangeQuery = rangeQuery(attribute.getName());
            if (attribute.getFrom() != null) {
                rangeQuery.gte(attribute.getFrom().format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            if (attribute.getTo() != null) {
                rangeQuery.lte(attribute.getTo().format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            queryBuilder.must(rangeQuery);
        });
        request.getStringAttributes().forEach(attribute -> {
            if (attribute.isNotMatch()) {
                queryBuilder.mustNot(termQuery(attribute.getName(), attribute.getValue()));
            } else if (attribute.isPartialMatch()) {
                queryBuilder.must(prefixQuery(attribute.getName(), attribute.getValue()));
            } else {
                queryBuilder.must(termQuery(attribute.getName(), attribute.getValue()));
            }
        });
    }

    public DashboardChartDataResponse getDashboardChartData(DashboardChartDataRequest request) {
        User user = userService.currentUser();
        LOGGER.debug("Performing dashboard chart data search under user id: {}", user.getUserId());
        if (user instanceof LdapUser) {
            LOGGER.warn("Dashboard chart data search not available for internal user");
            return DashboardChartDataResponse.EMPTY;
        }
        if (user instanceof MarsUser) {
            String[] indices = configService.getAvailableIndexFor(request.getEntityCode()).toArray(new String[0]);
            SearchRequest searchRequest = configService.createSearchRequest(SearchSourceBuilder.searchSource()).indices(indices);
            if (DashboardChartDataAggregationHelper.prepareSearchRequest(searchRequest.source(), request, (MarsUser) user)) {
                SearchResponse searchResponse = configService.search(searchRequest);
                return DashboardChartDataAggregationHelper.extractSearchResults(searchResponse);
            }
            return DashboardChartDataResponse.EMPTY;
        }
        throw new ServiceException("Unknown user type: " + user.getClass());
    }

    private EdocSearchResponse<Map<String, Object>> buildSearchResponse(SearchRequest searchRequest) {
        SearchResponse searchResponse = configService.search(searchRequest);
        ShardSearchFailure[] failures = searchResponse.getShardFailures();
        Stream.of(failures).forEach(failure -> LOGGER.error("Error searching documents. Index: {}, Reason: {}", failure.index(), failure.reason()));
        SearchHits hits = searchResponse.getHits();
        EdocSearchResponse<Map<String, Object>> response = new EdocSearchResponse<>();

        response.setTotal(hits.getTotalHits().value);
        response.setResults(Stream.of(hits.getHits()).map(SearchHit::getSourceAsMap).collect(Collectors.toList()));
        List<Map<String,Object>> resultList = new ArrayList<>();
        for(Map<String,Object>sourceMap : response.getResults()){
            String mxBookingEntity = null;
            if(sourceMap.containsKey(MX_BOOKING_ENTITY) && sourceMap.get(MX_BOOKING_ENTITY) != null) {
                mxBookingEntity = sourceMap.get(MX_BOOKING_ENTITY).toString();
            }
            if(mxBookingEntity != null && mxBookingEntity.startsWith("INT")){
                sourceMap.put(ENTITY_CODE, String.format("%s-%s",String.valueOf(sourceMap.get(ENTITY_CODE)),"INT"));
            }
            resultList.add(sourceMap);
        }
        response.setResults(resultList);
        return response;
    }

    private ExternalDocumentSearchRecord mapSearchRecordToIbgResponse(Map<String, Object> result) {
        ExternalDocumentSearchRecord record = new ExternalDocumentSearchRecord();
        record.setFileName(safeGetValue("name", result));
        record.setDocumentId(safeGetValue("id", result));
        record.setCounterPartyName(safeGetValue("companyName", result));

        final String[] fileNameParts = record.getFileName().split("_");
        final String mxCounterEntity = fileNameParts[0];

        record.setCounterPartyEntity(mxCounterEntity);
        record.setMxBookingEntity(mxCounterEntity);

        record.setAccountEntity(safeGetValue(ENTITY_CODE, result));
        record.setMurexLabel(safeGetValue("murexLabel", result));
        record.setLcin(safeGetValue("cinOrCif", result));
        record.setCob(safeGetValue("valuationDate", result));
        record.setCashflow(safeGetValue("cashflow", result));
        record.setRevisionStatus(safeGetValue(STATUS, result));
        record.setCreatedBy(safeGetValue("createdBy", result));
        record.setUploadedDate(safeGetValue(FIELD_CREATE_TIMESTAMP, result));
        record.setIsRevised(safeGetValue(IS_REVISED, result));

        return record;
    }

    private boolean hasAnyItems(Collection<String> collection) {
        return nonNull(collection) && !collection.isEmpty();
    }

    private String safeGetValue(String name, Map<String, Object> result) {
        return result.get(name) != null ? String.valueOf(result.get(name)) : null;
    }

    private Map<String, String> populateEntityMappingMap() {
        return gson.fromJson(IDW_EDOC_ENTITY_MAPPING.get(), Map.class);
    }
}
