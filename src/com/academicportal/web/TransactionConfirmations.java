package com.dbs.edoc.docsearch.ui.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transaction_confirmations")
public class TransactionConfirmations {

//	UNIQUE KEY doc_id+category+entity //CREATE NEW COLUMN AND WRITE UPSERT FUNCTION
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "document_id", length = 100, nullable = false)
	private String doc_id;

	@Column(name = "category", length = 10, nullable = false)
	private String category;

	@Column(name = "txn_ref", length = 255)
	private String txnRef;

	@Column(name = "txn_event_date")
	private LocalDate txnEventDate;

	@Column(name = "maturity_payment_date")
	private LocalDate maturityPaymentDate;

	@Column(name = "company", length = 255)
	private String company;

	@Column(name = "entity", length = 255)
	private String entity;

	@Column(name = "product", length = 255)
	private String product;

	@Column(name = "document_type", length = 255)
	private String documentType;

	@Column(name = "ccy", length = 3)
	private String ccy;

	@Column(name = "status", length = 50)
	private String status;

	@Column(name = "last_approved_rejected", length = 255)
	private String lastApprovedRejected;

	@Column(name = "upload_datetime_sgt")
	private LocalDateTime uploadDatetimeSgt;

	@Column(name = "email_datetime_sgt")
	private LocalDateTime emailDatetimeSgt;

	@Column(name = "action", length = 255)
	private String action;

	@Column(name = "unique_key", length = 64, unique = true)
	private String uniqueKey;

	@Column(name = "content_md5", length = 255)
	private String contentMd5;

	@Column(name = "mime_type", length = 100)
	private String mimeType;

	@Column(name = "type", length = 100)
	private String type;

	@Column(name = "is_revised", length = 50)
	private String isRevised;

	@Column(name = "cin_cif", length = 255)
	private String cinCif;

	@Column(name = "company_id", length = 255)
	private String companyId;

	@Column(name = "name", length = 255)
	private String name;

	@Column(name = "dup_check_md5", length = 255)
	private String dupCheckMd5;

	@Column(name = "user_type", length = 50)
	private String userType;

	@Column(name = "murex_label", length = 255)
	private String murexLabel;

	@Column(name = "levels_of_approval", length = 255)
	private String levelsOfApproval;

	@Column(name = "trade_date")
	private LocalDate tradeDate;

	@Column(name = "update_timestamp")
	private LocalDateTime updateTimestamp;
	
	@Column(name = "dynamic_attributes")
	private String dynamicAttributes;

	public String getDynamicAttributes() {
		return dynamicAttributes;
	}

	public void setDynamicAttributes(String dynamicAttributes) {
		this.dynamicAttributes = dynamicAttributes;
	}

	public TransactionConfirmations() {}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}


	public String getDoc_id() {
		return doc_id;
	}

	public void setDoc_id(String doc_id) {
		this.doc_id = doc_id;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getTxnRef() {
		return txnRef;
	}

	public void setTxnRef(String txnRef) {
		this.txnRef = txnRef;
	}

	public LocalDate getTxnEventDate() {
		return txnEventDate;
	}

	public void setTxnEventDate(LocalDate txnEventDate) {
		this.txnEventDate = txnEventDate;
	}

	public LocalDate getMaturityPaymentDate() {
		return maturityPaymentDate;
	}

	public void setMaturityPaymentDate(LocalDate maturityPaymentDate) {
		this.maturityPaymentDate = maturityPaymentDate;
	}

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getEntity() {
		return entity;
	}

	public void setEntity(String entity) {
		this.entity = entity;
	}

	public String getProduct() {
		return product;
	}

	public void setProduct(String product) {
		this.product = product;
	}

	public String getDocumentType() {
		return documentType;
	}

	public void setDocumentType(String documentType) {
		this.documentType = documentType;
	}

	public String getCcy() {
		return ccy;
	}

	public void setCcy(String ccy) {
		this.ccy = ccy;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getLastApprovedRejected() {
		return lastApprovedRejected;
	}

	public void setLastApprovedRejected(String lastApprovedRejected) {
		this.lastApprovedRejected = lastApprovedRejected;
	}

	public LocalDateTime getUploadDatetimeSgt() {
		return uploadDatetimeSgt;
	}

	public void setUploadDatetimeSgt(LocalDateTime uploadDatetimeSgt) {
		this.uploadDatetimeSgt = uploadDatetimeSgt;
	}

	public LocalDateTime getEmailDatetimeSgt() {
		return emailDatetimeSgt;
	}

	public void setEmailDatetimeSgt(LocalDateTime emailDatetimeSgt) {
		this.emailDatetimeSgt = emailDatetimeSgt;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getUniqueKey() {
		return uniqueKey;
	}

	public void setUniqueKey(String uniqueKey) {
		this.uniqueKey = uniqueKey;
	}

	public String getContentMd5() {
		return contentMd5;
	}

	public void setContentMd5(String contentMd5) {
		this.contentMd5 = contentMd5;
	}

	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getIsRevised() {
		return isRevised;
	}

	public void setIsRevised(String isRevised) {
		this.isRevised = isRevised;
	}

	public String getCinCif() {
		return cinCif;
	}

	public void setCinCif(String cinCif) {
		this.cinCif = cinCif;
	}

	public String getCompanyId() {
		return companyId;
	}

	public void setCompanyId(String companyId) {
		this.companyId = companyId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDupCheckMd5() {
		return dupCheckMd5;
	}

	public void setDupCheckMd5(String dupCheckMd5) {
		this.dupCheckMd5 = dupCheckMd5;
	}

	public String getUserType() {
		return userType;
	}

	public void setUserType(String userType) {
		this.userType = userType;
	}

	public String getMurexLabel() {
		return murexLabel;
	}

	public void setMurexLabel(String murexLabel) {
		this.murexLabel = murexLabel;
	}

	public String getLevelsOfApproval() {
		return levelsOfApproval;
	}

	public void setLevelsOfApproval(String levelsOfApproval) {
		this.levelsOfApproval = levelsOfApproval;
	}

	public LocalDate getTradeDate() {
		return tradeDate;
	}

	public void setTradeDate(LocalDate tradeDate) {
		this.tradeDate = tradeDate;
	}

	public LocalDateTime getUpdateTimestamp() {
		return updateTimestamp;
	}

	public void setUpdateTimestamp(LocalDateTime updateTimestamp) {
		this.updateTimestamp = updateTimestamp;
	}
}
