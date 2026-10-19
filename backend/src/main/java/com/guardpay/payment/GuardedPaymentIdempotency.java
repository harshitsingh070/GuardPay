package com.guardpay.payment;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import com.guardpay.transaction.Transaction;
import java.util.UUID;

/**
 * Idempotency record for guarded payments: one client key maps to exactly
 * one processed payment. Repeats return the stored response body verbatim;
 * a reused key with different content is rejected. Never stores decisions —
 * only the key, a request hash, the payment link, and the response snapshot.
 */
@Entity
@Table(name = "guarded_payment_idempotency")
public class GuardedPaymentIdempotency extends BaseEntity {

  @Column(name = "idempotency_key", length = 128, nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "request_hash", length = 64, nullable = false)
  private String requestHash;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "transaction_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_guarded_payment_idempotency_transaction"))
  private Transaction transaction;

  @Column(name = "response_body", columnDefinition = "TEXT", nullable = false)
  private String responseBody;

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public void setRequestHash(String requestHash) {
    this.requestHash = requestHash;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public void setTransaction(Transaction transaction) {
    this.transaction = transaction;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }
}
