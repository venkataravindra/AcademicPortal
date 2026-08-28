package com.dbs.edoc.docsearch.ui.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.dbs.edoc.docsearch.api.request.DocumentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbs.edoc.docsearch.ui.model.TransactionConfirmations;
import com.dbs.edoc.docsearch.ui.repo.TransactionConfirmationsRepo;

@Service
public class TransactionConfirmationsService {

	@Autowired private TransactionConfirmationsRepo repo;

	public Page<TransactionConfirmations> getConfirmations(String category, List<String> entities, Pageable pageable) {
		return repo.findByCategoryAndEntities(category, entities, pageable);
	}
	
	public Page<TransactionConfirmations> getConfirmations(String category, Pageable pageable) {
		return repo.findByCategory(category, pageable);
	}
	
	public Page<TransactionConfirmations> findConfirmations(String product,String category,String status, Pageable pageable) {
		System.out.println(String.format("product %s , category %s, status %s", product,category,status));
		return repo.findByProductAndStatus(product, category, status, pageable);
	}

//	public Page<TransactionConfirmations> searchConfirmations(String category, Set<DocumentType> documentTypes, Set<String> statuses,
//															  Set<String> entityCodes, Set<String> companyIds, String txnRef, LocalDate txnEventDateFrom, LocalDate txnEventDateTo,
//															  LocalDate maturityPaymentDateFrom, LocalDate maturityPaymentDateTo, Pageable pageable) {
//		return repo.searchConfirmations(category, documentTypes, statuses, entityCodes, companyIds, txnRef,
//				txnEventDateFrom, txnEventDateTo, maturityPaymentDateFrom, maturityPaymentDateTo, pageable);
//	}




	public Page<TransactionConfirmations> searchConfirmations(String category, Set<DocumentType> documentTypes, Set<String> statuses,
															  Set<String> entityCodes, Set<String> companyIds, Pageable pageable) {

		// Convert DocumentType objects to string class names
		Set<String> documentTypeNames = Collections.emptySet();
		if (documentTypes != null && !documentTypes.isEmpty()) {
			documentTypeNames = documentTypes.stream()
					.map(DocumentType::getClassName)
					.filter(className -> className != null && !className.trim().isEmpty())
					.collect(Collectors.toSet());
		}

		// Handle null/empty collections by converting to empty sets
		Set<String> safeStatuses = (statuses != null) ? statuses : Collections.emptySet();
		Set<String> safeEntityCodes = (entityCodes != null) ? entityCodes : Collections.emptySet();
		Set<String> safeCompanyIds = (companyIds != null) ? companyIds : Collections.emptySet();
		System.out.println("=== SEARCH CONFIRMATIONS DEBUG ===");
		System.out.println("Category: " + category);
		System.out.println("Document Types: " + documentTypeNames);
		System.out.println("Statuses: " + safeStatuses);
		System.out.println("Entity Codes: " + safeEntityCodes);
		System.out.println("Company IDs: " + safeCompanyIds);
		System.out.println("Pageable: " + pageable);
		return repo.searchConfirmations(category, documentTypeNames, safeStatuses, safeEntityCodes, safeCompanyIds, pageable);
	}

	public TransactionConfirmations getConfirmationById(Long id) {
		return repo.findById(id).orElse(null);
	}

	public TransactionConfirmations saveConfirmation(TransactionConfirmations confirmation) {
		return repo.save(confirmation);
	}

	public List<TransactionConfirmations> saveAllConfirmations(List<TransactionConfirmations> confirmations) {
		return repo.saveAll(confirmations);
	}

	@Transactional
	public TransactionConfirmations updateConfirmation(Long id, TransactionConfirmations confirmationData) {
		TransactionConfirmations existing = repo.findById(id).orElse(null);
		if (existing == null) {
			return null;
		}
		if (confirmationData.getCategory() != null) existing.setCategory(confirmationData.getCategory());
		if (confirmationData.getProduct() != null) existing.setProduct(confirmationData.getProduct());
		if (confirmationData.getStatus() != null) existing.setStatus(confirmationData.getStatus());
		if (confirmationData.getEntity() != null) existing.setEntity(confirmationData.getEntity());
		if (confirmationData.getCompany() != null) existing.setCompany(confirmationData.getCompany());
		if (confirmationData.getTxnRef() != null) existing.setTxnRef(confirmationData.getTxnRef());
		if (confirmationData.getTxnEventDate() != null) existing.setTxnEventDate(confirmationData.getTxnEventDate());
		if (confirmationData.getMaturityPaymentDate() != null) existing.setMaturityPaymentDate(confirmationData.getMaturityPaymentDate());
		return repo.save(existing);
	}

	@Transactional
	public TransactionConfirmations saveOrUpdate(TransactionConfirmations confirmation) {
		if (confirmation.getId() != null && confirmation.getId() > 0) {
			TransactionConfirmations existing = repo.findById(confirmation.getId()).orElse(null);
			if (existing != null) {
				return updateConfirmation(confirmation.getId(), confirmation);
			}
		}
		return saveConfirmation(confirmation);
	}

	public void deleteConfirmation(Long id) {
		repo.deleteById(id);
	}
}
