package com.dbs.edoc.docsearch.api.request;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DocumentSearchRequest extends SearchRequest {

    private Set<DocumentType> documentTypes = Collections.emptySet();
    private Set<String> entityCodes = Collections.emptySet();
    private Set<String> companyIds = Collections.emptySet();
    private Set<String> cinCifs = Collections.emptySet();
    private Set<String> statuses = Collections.emptySet();
    private Set<String> ackStatuses = Collections.emptySet();
    private List<DateAttribute> dateAttributes = Collections.emptyList();
    private List<StringAttribute> stringAttributes = Collections.emptyList();
    private Set<String> excludeStatuses = Collections.emptySet();
    private String access;

    public Set<DocumentType> getDocumentTypes() {
        return documentTypes;
    }

    public void setDocumentTypes(Set<DocumentType> documentTypes) {
        this.documentTypes = documentTypes;
    }

    public Set<String> getEntityCodes() {
        return entityCodes;
    }

    public void setEntityCodes(Set<String> entityCodes) {
        this.entityCodes = entityCodes;
    }

    public Set<String> getCompanyIds() {
        return companyIds;
    }

    public void setCompanyIds(Set<String> companyIds) {
        this.companyIds = companyIds;
    }

    public Set<String> getStatuses() {
        return statuses;
    }

    public void setStatuses(Set<String> statuses) {
        this.statuses = statuses;
    }

    public Set<String> getAckStatuses() {
        return ackStatuses;
    }

    public void setAckStatuses(Set<String> ackStatuses) {
        this.ackStatuses = ackStatuses;
    }

    public List<DateAttribute> getDateAttributes() {
        return dateAttributes;
    }

    public void setDateAttributes(List<DateAttribute> dateAttributes) {
        this.dateAttributes = dateAttributes;
    }

    public List<StringAttribute> getStringAttributes() {
        return stringAttributes;
    }

    public void setStringAttributes(List<StringAttribute> stringAttributes) {
        this.stringAttributes = stringAttributes;
    }

    public String getAccess() {
        return access;
    }

    public void setAccess(String access) {
        this.access = access;
    }

    public Set<String> getCinCifs() {
        return cinCifs;
    }

    public void setCinCifs(Set<String> cinCifs) {
        this.cinCifs = cinCifs;
    }

    public Set<String> getExcludeStatuses() {
        return excludeStatuses;
    }

    public void setExcludeStatuses(Set<String> excludeStatuses) {
        this.excludeStatuses = excludeStatuses;
    }
}
