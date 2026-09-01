
package com.dbs.edoc.docsearch.ui.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dbs.edoc.docsearch.ui.dto.TransactionConfirmationsResponse;
import com.dbs.edoc.docsearch.ui.model.TransactionConfirmations;
import com.dbs.edoc.docsearch.ui.service.TransactionConfirmationsService;
import com.dbs.edoc.docsearch.ui.sync.SyncConstants;

@RestController
@RequestMapping("/api/ui/transaction-confirmations")
public class TransactionConfirmationsController {

	@Autowired private TransactionConfirmationsService transactionConfirmationsService;

	@GetMapping("/api/v1/internal/transactionconfirmations")
	public ResponseEntity<?> getTransactionConfirmations(
			@RequestParam(defaultValue = "EDOC") String category,
			@RequestParam(required = false) String product,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "20") int pageSize,
			@RequestParam(defaultValue = "id") String sortField,
			@RequestParam(defaultValue = "true") boolean sortAscending) {

		try {
//			category = category.equalsIgnoreCase("EDOC")?SyncConstants.documentClassMapping.get("TRANSACTION_CONFIRMATION").edocClassName.get(0):SyncConstants.documentClassMapping.get("FIXING_SETTLEMENT_ADVICE").emailClassName.get(0);
			
			Sort.Direction direction = sortAscending ? Sort.Direction.ASC : Sort.Direction.DESC;
			Pageable pageable = PageRequest.of(offset, pageSize, Sort.by(direction, sortField));
//			Page<TransactionConfirmations> confirmationsPage = transactionConfirmationsService.getConfirmations("TRANSACTIONCONFIRMATION", SyncConstants.edocentities, pageable);
//			Page<TransactionConfirmations> confirmationsPage = transactionConfirmationsService.getConfirmations(category, pageable);
			Page<TransactionConfirmations> confirmationsPage = transactionConfirmationsService.findConfirmations(product, category, status, pageable);
			
			List<TransactionConfirmationsResponse> results = new ArrayList<>();
			for (TransactionConfirmations tc : confirmationsPage.getContent()) {
				results.add(new TransactionConfirmationsResponse(tc));
			}

			Map<String, Object> response = new HashMap<>();
			response.put("total", confirmationsPage.getTotalElements());
			response.put("results", results);

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error retrieving transaction confirmations: " + e.getMessage());
		}
	}
	
	@GetMapping("/search")
	public ResponseEntity<?> searchTransactionConfirmations(
			@RequestParam(defaultValue = "EDOC") String category,
			@RequestParam(required = false) String product,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String entity,
			@RequestParam(required = false) String company,
			@RequestParam(required = false) String txnRef,
			@RequestParam(required = false) String txnEventDateFrom,
			@RequestParam(required = false) String txnEventDateTo,
			@RequestParam(required = false) String maturityPaymentDateFrom,
			@RequestParam(required = false) String maturityPaymentDateTo,
			@RequestParam(defaultValue = "0") int offset,
			@RequestParam(defaultValue = "20") int pageSize,
			@RequestParam(defaultValue = "id") String sortField,
			@RequestParam(defaultValue = "true") boolean sortAscending) {

		try {
			LocalDate txnEventFromDate = txnEventDateFrom != null && !txnEventDateFrom.isEmpty() ? LocalDate.parse(txnEventDateFrom) : null;
			LocalDate txnEventToDate = txnEventDateTo != null && !txnEventDateTo.isEmpty() ? LocalDate.parse(txnEventDateTo) : null;
			LocalDate maturityFromDate = maturityPaymentDateFrom != null && !maturityPaymentDateFrom.isEmpty() ? LocalDate.parse(maturityPaymentDateFrom) : null;
			LocalDate maturityToDate = maturityPaymentDateTo != null && !maturityPaymentDateTo.isEmpty() ? LocalDate.parse(maturityPaymentDateTo) : null;

			Sort.Direction direction = sortAscending ? Sort.Direction.ASC : Sort.Direction.DESC;
			Pageable pageable = PageRequest.of(offset, pageSize, Sort.by(direction, sortField));

			Page<TransactionConfirmations> confirmationsPage = transactionConfirmationsService.searchConfirmations(
					category, product, status, entity, company, txnRef,
					txnEventFromDate, txnEventToDate, maturityFromDate, maturityToDate, pageable);

			List<TransactionConfirmationsResponse> results = new ArrayList<>();
			for (TransactionConfirmations tc : confirmationsPage.getContent()) {
				results.add(new TransactionConfirmationsResponse(tc));
			}

			Map<String, Object> response = new HashMap<>();
			response.put("total", confirmationsPage.getTotalElements());
			response.put("results", results);

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error searching transaction confirmations: " + e.getMessage());
		}
	}
}
