package com.dbs.edoc.docsearch.api;

import com.dbs.edoc.docsearch.api.request.*;
import com.dbs.edoc.docsearch.api.request.external.ExternalDocumentSearchRequest;
import com.dbs.edoc.docsearch.api.response.*;
import com.dbs.edoc.docsearch.api.response.external.ExternalDocumentSearchRecord;
import com.dbs.edoc.docsearch.service.search.DocumentSearchService;
import com.dbs.edoc.docsearch.service.search.AutomatedFailureSearchService;
import com.dbs.edoc.docsearch.service.search.DocumentStagingService;
import com.dbs.edoc.docsearch.service.search.SMTPFailureSearchService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/api/v1", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
public class DocumentSearchController {

    @Autowired
    private DocumentSearchService documentSearchService;

    @Autowired
    private AutomatedFailureSearchService automatedFailureSearchService;

    @Autowired
    private SMTPFailureSearchService smtpFailureSearchService;

    @Autowired
    private DocumentStagingService documentStagingService;

    @PostMapping("/search")
    public EdocSearchResponse<Map<String, Object>> search(@Valid @RequestBody DocumentSearchRequest request) {
        return documentSearchService.search(request);
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @RequestBody DocumentSearchRequest request) {
        return documentSearchService.export(request);
    }

    @PostMapping("/last-upload")
    public DocumentLastUploadResponse getLastUploadTime(@Valid @RequestBody DocumentSearchRequest request) {
        return documentSearchService.getLastUploadTime(request);
    }

    @PostMapping("/dashboard-chart-data")
    public DashboardChartDataResponse getDashboadData(@Valid @RequestBody DashboardChartDataRequest request) {
        return documentSearchService.getDashboardChartData(request);
    }

    @PostMapping("/external/system/document/search")
    public EdocSearchResponse<ExternalDocumentSearchRecord> externalDocumentSearch(@Valid @RequestBody ExternalDocumentSearchRequest request) {
        return documentSearchService.externalSystemDocumentSearch(request);
    }

    @PostMapping("/automatedfailure/search")
    public EdocSearchResponse<Map<String, Object>> automatedFailureSearch(@Valid @RequestBody AutomatedFailureSearchRequest request) {
        return automatedFailureSearchService.search(request);
    }

    @PostMapping("/smtpfailure/search")
    public EdocSearchResponse<Map<String, Object>> smtpFailureSearch(@Valid @RequestBody SMTPFailureSearchRequest request) {
        return smtpFailureSearchService.search(request);
    }

    @PostMapping("/smtpfailures/export")
    public ResponseEntity<byte[]> smtpFailureExport(@Valid @RequestBody SMTPFailureSearchRequest request) {
        return smtpFailureSearchService.export(request);
    }

    @PostMapping("/automatedfailure/search-by-date")
    public List<AutomatedFailureSearchByDateResponse> automatedFailureSearchByDate(@Valid @RequestBody AutomatedFailureSearchByDateRequest request) {
        return automatedFailureSearchService.searchFromDate(request);
    }

    @PostMapping("/upload-staging")
    public UploadFailureSearchResponse getFailedUploads(@RequestBody UploadFailureSearchRequest request) {
        return documentStagingService.getAll(request);
    }

    @PostMapping("/upload-staging/export")
    public ResponseEntity<byte[]> exportFailedUploads(@RequestBody UploadFailureSearchRequest request) {
        return documentStagingService.export(request);
    }
}
