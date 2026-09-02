package com.dbs.edoc.docsearch.service.search;

import com.dbs.edoc.config.DynamicStringProperty;
import com.dbs.edoc.docsearch.api.request.DashboardChartDataRequest;
import com.dbs.edoc.docsearch.api.request.DateAttribute;
import com.dbs.edoc.docsearch.api.request.DocumentSearchRequest;
import com.dbs.edoc.docsearch.api.request.DocumentType;
import com.dbs.edoc.docsearch.api.request.StringAttribute;
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
    private static final String ENTITY_CODE = "entityCode";
    private static final String STATUS = "status";
    private static final String MX_BOOKING_ENTITY = "mxBookingEntity";
    private static final DynamicStringProperty IDW_EDOC_ENTITY_MAPPING = new DynamicStringProperty("idw.edoc.entity.mapping",
            "{ \"CN\": \"DBSCN\", \"HK-BR\": \"DBSHKBR\", \"HK-LTD\": \"DBSHK\", \"IND\": \"DBSIN\", \"SG\": \"DBSSG\", \"TW\": \"DBSTW\" }");
    private static final String IS_REVISED = "isRevised";
    private static final String CANCELLED = "CANCELLED";
    private static final String FIELD_UPLOAD_DATETIME_SGT = "uploadDatetimeSgt";
    private static final String ATTR_TXN_REF = "txnRef";
    private static final String ATTR_TXN_EVENT_DATE = "txnEventDate";
    private static final String ATTR_MATURITY_PAYMENT_DATE = "maturityPaymentDate";

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

        String lastUploadStr = buildSearchRequest(request).map(Page::getContent)
                .filter(content -> content.size() == 1)
                .map(content -> Objects.toString(content.get(0).getUploadDatetimeSgt(), null))
                .orElse(null);

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
        Set<DocumentType> documentTypes = request.getDocumentTypes();
        if (request.getDocumentTypes().isEmpty()) {
            throw new IllegalArgumentException("Requires at least 1 document type");
        }

        Set<String> entityCodes = request.getEntityCodes();
        Set<String> companyIds = request.getCompanyIds();
        Set<String> documentClasses = new HashSet<>();
        Set<String> productTypes = new HashSet<>();
        Set<String> accessibleEntities = new HashSet<>();

        // check permission
        User user = userService.currentUser();
        LOGGER.info("Performing search under user id: {}", user.getUserId());
        if (user instanceof LdapUser) {
            LdapUser ldapUser = (LdapUser) user;

            Map<String, Set<String>> entityDocumentMap = populateAccessLevels(ldapUser);
            LOGGER.info("Document and entities user has access to : {}", entityDocumentMap);
            addEntityDocumentProducts(entityDocumentMap, documentTypes,entityCodes,documentClasses,productTypes, accessibleEntities, user);
            LOGGER.info("Searching data under doc classes and entity codes [{}] for User {} and accessible entities {} ",
                    entityDocumentMap, ldapUser.getUserId(), accessibleEntities);
        } else {
            if (filterForExternal(documentTypes, entityCodes, companyIds, documentClasses, productTypes, (MarsUser) user))
                return Optional.empty();
        }

        if (DocumentSearchConstants.DistributionType.EMAIL.equalsIgnoreCase(request.getAccess())) {
            return Optional.of(searchTransactionConfirmations(accessibleEntities, companyIds, request));
        }
        if(user instanceof MarsUser) {
            LOGGER.info("External user - entities user has access to : {}", entityCodes);
            accessibleEntities = entityCodes;
        }

        return Optional.of(searchTransactionConfirmations(accessibleEntities, companyIds, request));
    }

    private Page<TransactionConfirmations> searchTransactionConfirmations(Set<String> entityCodes, Set<String> companyIds,
                                                                          DocumentSearchRequest request) {
        String category = request.getAccess();
        String product = request.getDocumentTypes().stream()
                .map(DocumentType::getCategoryName)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        String status = firstOrNull(request.getStatuses());
        String entity = firstOrNull(entityCodes);
        String company = firstOrNull(companyIds);
        String txnRef = findStringAttributeValue(request, ATTR_TXN_REF);
        LocalDate txnEventDateFrom = findDateAttribute(request, ATTR_TXN_EVENT_DATE).map(DateAttribute::getFrom).orElse(null);
        LocalDate txnEventDateTo = findDateAttribute(request, ATTR_TXN_EVENT_DATE).map(DateAttribute::getTo).orElse(null);
        LocalDate maturityPaymentDateFrom = findDateAttribute(request, ATTR_MATURITY_PAYMENT_DATE).map(DateAttribute::getFrom).orElse(null);
        LocalDate maturityPaymentDateTo = findDateAttribute(request, ATTR_MATURITY_PAYMENT_DATE).map(DateAttribute::getTo).orElse(null);

        return transactionConfirmationsService.searchConfirmations(category, product, status, entity, company, txnRef,
                txnEventDateFrom, txnEventDateTo, maturityPaymentDateFrom, maturityPaymentDateTo, buildPageable(request));
    }

    private String firstOrNull(Set<String> values) {
        return hasAnyItems(values) ? values.iterator().next() : null;
    }

    private String findStringAttributeValue(DocumentSearchRequest request, String attributeName) {
        return request.getStringAttributes().stream()
                .filter(attribute -> attributeName.equalsIgnoreCase(attribute.getName()))
                .map(StringAttribute::getValue)
                .findFirst().orElse(null);
    }

    private Optional<DateAttribute> findDateAttribute(DocumentSearchRequest request, String attributeName) {
        return request.getDateAttributes().stream()
                .filter(attribute -> attributeName.equalsIgnoreCase(attribute.getName()))
                .findFirst();
    }

    private Pageable buildPageable(DocumentSearchRequest request) {
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 20;
        int pageNumber = request.getOffset() / pageSize;
        if (StringUtils.isNotEmpty(request.getSortField())) {
            Sort.Direction direction = request.isSortAscending() ? Sort.Direction.ASC : Sort.Direction.DESC;
            return PageRequest.of(pageNumber, pageSize, Sort.by(direction, request.getSortField()));
        }
        return PageRequest.of(pageNumber, pageSize);
    }

    private void addEntityDocumentProducts(Map<String, Set<String>> entityDocumentMap, Set<DocumentType> documentTypes,
                                           Set<String> entityCodes, Set<String> documentClasses, Set<String> productTypes,
                                           Set<String> accessibleEntities, User user) {
        LdapUser ldapUser = (LdapUser) user;
        for (DocumentType documentType : documentTypes) {
            if(entityDocumentMap.containsKey(documentType.getClassName())) {
                documentClasses.add(upperCase(documentType.getClassName()));
                productTypes.addAll(documentType.getProductTypeNames().stream().map(StringUtils::upperCase).collect(Collectors.toSet()));
                LOGGER.info("User {} Requested to search on these entities: {}", ldapUser.getUserId(), entityCodes);
                if(!entityCodes.isEmpty()) {
                    accessibleEntities.addAll(entityCodes.stream().filter(entity -> entityDocumentMap.get(documentType.getClassName()).contains(entity)).collect(Collectors.toSet()));
                    LOGGER.info("User {} can search on these filtered entities: {}", ldapUser.getUserId(), accessibleEntities);
                } else {
                    accessibleEntities.addAll(entityDocumentMap.get(documentType.getClassName()));
                    LOGGER.info("User {} can only search on these accessibleEntities entities: {} for doctype : {}",
                            ldapUser.getUserId(), accessibleEntities, documentType.getClassName());
                }
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

    private EdocSearchResponse<Map<String, Object>> buildSearchResponse(Page<TransactionConfirmations> page) {
        EdocSearchResponse<Map<String, Object>> response = new EdocSearchResponse<>();
        response.setTotal(page.getTotalElements());
        response.setResults(page.getContent().stream().map(this::toResultMap).collect(Collectors.toList()));
        return response;
    }

    private Map<String, Object> toResultMap(TransactionConfirmations confirmation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", confirmation.getId());
        result.put("documentId", confirmation.getDoc_id());
        result.put("category", confirmation.getCategory());
        result.put("txnRef", confirmation.getTxnRef());
        result.put(ATTR_TXN_EVENT_DATE, confirmation.getTxnEventDate());
        result.put(ATTR_MATURITY_PAYMENT_DATE, confirmation.getMaturityPaymentDate());
        result.put("company", confirmation.getCompany());
        result.put(ENTITY_CODE, confirmation.getEntity());
        result.put("product", confirmation.getProduct());
        result.put("documentType", confirmation.getDocumentType());
        result.put("ccy", confirmation.getCcy());
        result.put(STATUS, confirmation.getStatus());
        result.put(FIELD_CREATE_TIMESTAMP, confirmation.getUploadDatetimeSgt());
        result.put(IS_REVISED, confirmation.getIsRevised());
        result.put("name", confirmation.getName());
        result.put("murexLabel", confirmation.getMurexLabel());
        result.put("cinOrCif", confirmation.getCinCif());
        return result;
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
