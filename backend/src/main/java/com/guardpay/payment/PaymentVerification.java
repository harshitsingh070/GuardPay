package com.guardpay.payment;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * One controlled verification challenge attached to a payment attempt that the
 * risk engine asked to VERIFY. Stores only a hash of the challenge answer — the
 * answer itself is never persisted, and no client can set the outcome.
 */
@Entity
@Table(name = "payment_verifications")
public class PaymentVerification extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "transaction_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_payment_verifications_transaction"))
  private Transaction transaction;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private VerificationStatus status = VerificationStatus.PENDING;

  @Column(name = "challenge_code_hash", length = 128, nullable = false)
  private String challengeCodeHash;

  /** Delivery/verification channel: TOTP, EMAIL, or SIMULATED. */
  @Column(name = "channel", length = 16, nullable = false)
  private String channel = "SIMULATED";

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "verified_at")
  private OffsetDateTime verifiedAt;

  public Transaction getTransaction() {
    return transaction;
  }

  public void setTransaction(Transaction transaction) {
    this.transaction = transaction;
  }

  public VerificationStatus getStatus() {
    return status;
  }

  public void setStatus(VerificationStatus status) {
    this.status = status;
  }

  public String getChallengeCodeHash() {
    return challengeCodeHash;
  }

  public void setChallengeCodeHash(String challengeCodeHash) {
    this.challengeCodeHash = challengeCodeHash;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public int getAttempts() {
    return attempts;
  }

  public void setAttempts(int attempts) {
    this.attempts = attempts;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public OffsetDateTime getVerifiedAt() {
    return verifiedAt;
  }

  public void setVerifiedAt(OffsetDateTime verifiedAt) {
    this.verifiedAt = verifiedAt;
  }
}
