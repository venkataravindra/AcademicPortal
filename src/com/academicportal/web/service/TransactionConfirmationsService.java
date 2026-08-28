package com.dbs.edoc.docsearch.ui.service;

import java.time.LocalDate;
import java.util.List;

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

	public Page<TransactionConfirmations> searchConfirmations(String category, String product, String status,
			String entity, String company, String txnRef, LocalDate txnEventDateFrom, LocalDate txnEventDateTo,
			LocalDate maturityPaymentDateFrom, LocalDate maturityPaymentDateTo, Pageable pageable) {
		return repo.searchConfirmations(category, product, status, entity, company, txnRef,
				txnEventDateFrom, txnEventDateTo, maturityPaymentDateFrom, maturityPaymentDateTo, pageable);
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
