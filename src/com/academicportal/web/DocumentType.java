package com.dbs.edoc.docsearch.api.request;


import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.Set;

public class DocumentType {

    @NotNull(message = "Document category cannot be null")
    private String categoryName;

    @NotNull(message = "Document class cannot be null")
    private String className;

    private Set<String> productTypeNames = Collections.emptySet();

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Set<String> getProductTypeNames() {
        return productTypeNames;
    }

    public void setProductTypeNames(Set<String> productTypeNames) {
        this.productTypeNames = productTypeNames;
    }

    @Override
    public String toString() {
        return String.format("DocumentType{categoryName='%s', className='%s', productTypeNames=%s}",
                categoryName, className, productTypeNames);
    }
}
