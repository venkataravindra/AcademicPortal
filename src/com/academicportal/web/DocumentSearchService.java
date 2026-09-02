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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

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

    private final AtomicReference<Map<String, String>> entityMappingRef;
    private SearchConfigService configService;
    private UserService userService;
    private ExcelBuilder excelBuilder;
    private Gson gson;

    @Autowired
    public DocumentSearchService(SearchConfigService configService,
                                 UserService userService,
                                 ExcelBuilder excelBuilder) {
        this.configService = configService;
        this.userService = userService;
        this.excelBuilder = excelBuilder;
        this.gson = new Gson();

        this.entityMappingRef = new AtomicReference<>(populateEntityMappingMap());
        final Runnable createEntityMappingMap = () -> entityMappingRef.set(populateEntityMappingMap());
        IDW_EDOC_ENTITY_MAPPING.addCallback(createEntityMappingMap);
    }

    public DocumentLastUploadResponse getLastUploadTime(DocumentSearchRequest request) {
        String lastUploadStr = buildSearchRequest(request).map(searchRequest -> {
            searchRequest.source().from(0).size(1)
                    .sort(fieldSort(FIELD_CREATE_TIMESTAMP).order(SortOrder.DESC))
                    .fetchSource(FIELD_CREATE_TIMESTAMP, null);

            if (DocumentSearchConstants.DistributionType.EMAIL.equalsIgnoreCase(request.getAccess())) {
                Set<String> docClasses = new HashSet<>();
                for (DocumentType documentType : request.getDocumentTypes()) {
                    docClasses.add(upperCase(documentType.getClassName()));
                }
                Set<String> indices = configService.getIndexForDocType(docClasses);
                searchRequest.indices(indices.toArray(new String[indices.size()]));
            }

            SearchResponse searchResponse = configService.search(searchRequest);
            SearchHits hits = searchResponse.getHits();
            if (hits.getHits().length == 1) {
                return Objects.toString(hits.getAt(0).getSourceAsMap().get(FIELD_CREATE_TIMESTAMP), null);
            }

            return null;
        }).orElse(null);

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
        return buildSearchRequest(request).map(searchRequest -> {
            searchRequest.source().from(0).size(65000);
            EdocSearchResponse<Map<String, Object>> searchResponse = buildSearchResponse(searchRequest);
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


    private Optional<SearchRequest> buildSearchRequest(DocumentSearchRequest request) {
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
            Set<String> nonEdcoDocumentClasses = configService.getNonEdocDocClassesForDocType(documentClasses);
            SearchSourceBuilder builder = buildNonEdocQuery(nonEdcoDocumentClasses, accessibleEntities, companyIds, productTypes, request);
            Set<String> indices = configService.getIndexForDocType(documentClasses);
            // instead of this return we need to use transactionConfirmationsService.searchConfirmations 
            return Optional.of(configService.createSearchRequest(builder).indices(indices.toArray(new String[indices.size()])));
    
        }
        if(user instanceof MarsUser) {
            LOGGER.info("External user - entities user has access to : {}", entityCodes);
            accessibleEntities = entityCodes;
        }

        SearchSourceBuilder builder = buildEdocQuery(documentClasses, accessibleEntities, companyIds, productTypes, request, user);
         // instead of this return we need to use transactionConfirmationsService.searchConfirmations 
        return Optional.of(configService.createSearchRequest(builder));
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
