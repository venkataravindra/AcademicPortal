package com.dbs.edoc.docsearch.ui.repo;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dbs.edoc.docsearch.ui.model.TransactionConfirmations;

import jakarta.transaction.Transactional;

public interface TransactionConfirmationsRepo extends JpaRepository<TransactionConfirmations, Long> {

	@Query("SELECT t FROM TransactionConfirmations t WHERE t.category = :category AND t.entity IN :entities")
	Page<TransactionConfirmations> findByCategoryAndEntities(@Param("category") String category, @Param("entities") List<String> entities, Pageable pageable);

	@Query("SELECT t FROM TransactionConfirmations t WHERE t.category = :category")
	Page<TransactionConfirmations> findByCategory(@Param("category") String category, Pageable pageable);

	@Query("SELECT t FROM TransactionConfirmations t WHERE t.category = :category AND" +
			"(:product IS NULL OR t.documentType = :product) " +
			"AND (:status IS NULL OR t.status = :status)")
	Page<TransactionConfirmations> findByProductAndStatus(@Param("product") String product,@Param("category") String category,
			@Param("status") String status, Pageable pageable);

	@Query("SELECT t FROM TransactionConfirmations t WHERE t.category = :category " +
			"AND (:product IS NULL OR t.documentType = :product) " +
			"AND (:status IS NULL OR t.status = :status) " +
			"AND (:entity IS NULL OR t.entity = :entity) " +
			"AND (:company IS NULL OR LOWER(t.companyId) LIKE LOWER(CONCAT('%', :company, '%'))) " +
			"AND (:txnRef IS NULL OR LOWER(t.txnRef) LIKE LOWER(CONCAT('%', :txnRef, '%'))) " +
			"AND (:txnEventDateFrom IS NULL OR t.txnEventDate >= :txnEventDateFrom) " +
			"AND (:txnEventDateTo IS NULL OR t.txnEventDate <= :txnEventDateTo) " +
			"AND (:maturityPaymentDateFrom IS NULL OR t.maturityPaymentDate >= :maturityPaymentDateFrom) " +
			"AND (:maturityPaymentDateTo IS NULL OR t.maturityPaymentDate <= :maturityPaymentDateTo)")
	Page<TransactionConfirmations> searchConfirmations(
			@Param("category") String category,
			@Param("product") String product,
			@Param("status") String status,
			@Param("entity") String entity,
			@Param("company") String company,
			@Param("txnRef") String txnRef,
			@Param("txnEventDateFrom") LocalDate txnEventDateFrom,
			@Param("txnEventDateTo") LocalDate txnEventDateTo,
			@Param("maturityPaymentDateFrom") LocalDate maturityPaymentDateFrom,
			@Param("maturityPaymentDateTo") LocalDate maturityPaymentDateTo,
			Pageable pageable);

	@Query("SELECT t FROM TransactionConfirmations t WHERE t.entity = :entity AND t.doc_id = :documentId")
	TransactionConfirmations findByEntityAndDocumentId(@Param("entity") String entity, @Param("documentId") Long documentId);

	@Transactional
	default void batchInsertConfirmations(List<TransactionConfirmations> confirmations, int batchsize, JdbcTemplate jdbcTemplate) {
		if (confirmations.isEmpty()) return;

		String sql = """
			INSERT INTO transaction_confirmations (
				document_id, category, txn_ref, txn_event_date, maturity_payment_date, company, entity, product,
				document_type, ccy, status, last_approved_rejected, upload_datetime_sgt, email_datetime_sgt, action,
				unique_key, content_md5, mime_type, type, is_revised, cin_cif, company_id, name, dup_check_md5,
				user_type, murex_label, levels_of_approval, trade_date, update_timestamp
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

		try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
			System.out.println("Inserting TransactionConfirmations to: " + conn.getMetaData().getURL());
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			int[][] result = jdbcTemplate.batchUpdate(sql, confirmations, batchsize, (ps, confirmation) -> {
				ps.setString(1, confirmation.getDoc_id());
				ps.setString(2, confirmation.getCategory());
				ps.setString(3, confirmation.getTxnRef());
				ps.setObject(4, confirmation.getTxnEventDate());
				ps.setObject(5, confirmation.getMaturityPaymentDate());
				ps.setString(6, confirmation.getCompany());
				ps.setString(7, confirmation.getEntity());
				ps.setString(8, confirmation.getProduct());
				ps.setString(9, confirmation.getDocumentType());
				ps.setString(10, confirmation.getCcy());
				ps.setString(11, confirmation.getStatus());
				ps.setString(12, confirmation.getLastApprovedRejected());
				ps.setObject(13, confirmation.getUploadDatetimeSgt());
				ps.setObject(14, confirmation.getEmailDatetimeSgt());
				ps.setString(15, confirmation.getAction());
				ps.setString(16, confirmation.getUniqueKey());
				ps.setString(17, confirmation.getContentMd5());
				ps.setString(18, confirmation.getMimeType());
				ps.setString(19, confirmation.getType());
				ps.setString(20, confirmation.getIsRevised());
				ps.setString(21, confirmation.getCinCif());
				ps.setString(22, confirmation.getCompanyId());
				ps.setString(23, confirmation.getName());
				ps.setString(24, confirmation.getDupCheckMd5());
				ps.setString(25, confirmation.getUserType());
				ps.setString(26, confirmation.getMurexLabel());
				ps.setString(27, confirmation.getLevelsOfApproval());
				ps.setObject(28, confirmation.getTradeDate());
				ps.setObject(29, confirmation.getUpdateTimestamp());
			});
			int totalInserted = 0;
			for (int[] batch : result) {
				totalInserted += Arrays.stream(batch).sum();
			}
			System.out.println("Total TransactionConfirmations rows inserted: " + totalInserted);
		} catch (Exception e) {
			System.err.println("Batch insert failed: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}
}
