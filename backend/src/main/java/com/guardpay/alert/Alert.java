package com.guardpay.alert;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.transaction.Transaction;
import com.guardpay.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One user-facing fraud alert. Written inside the decision transaction and
 * delivered separately through the outbox, so real-time visibility never
 * depends on a browser being connected.
 */
@Entity
@Table(name = "alerts")
public class Alert extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_alerts_user"))
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "transaction_id",
      foreignKey = @ForeignKey(name = "fk_alerts_transaction"))
  private Transaction transaction;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 32, nullable = false)
  private AlertType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", length = 16, nullable = false)
  private AlertSeverity severity;

  @Column(name = "title", length = 255, nullable = false)
  private String title;

  @Column(name = "message", columnDefinition = "TEXT", nullable = false)
  private String message;

  @Column(name = "score")
  private Integer score;

  @Enumerated(EnumType.STRING)
  @Column(name = "recommended_action", length = 16)
  private RecommendedAction recommendedAction;

  @Column(name = "amount", precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "recipient_name", length = 255)
  private String recipientName;

  /** JSON array of triggered signal codes, for the dashboard explanation. */
  @Column(name = "signals", columnDefinition = "TEXT")
  private String signals;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public void setTransaction(Transaction transaction) {
    this.transaction = transaction;
  }

  public AlertType getType() {
    return type;
  }

  public void setType(AlertType type) {
    this.type = type;
  }

  public AlertSeverity getSeverity() {
    return severity;
  }

  public void setSeverity(AlertSeverity severity) {
    this.severity = severity;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Integer getScore() {
    return score;
  }

  public void setScore(Integer score) {
    this.score = score;
  }

  public RecommendedAction getRecommendedAction() {
    return recommendedAction;
  }

  public void setRecommendedAction(RecommendedAction recommendedAction) {
    this.recommendedAction = recommendedAction;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getRecipientName() {
    return recipientName;
  }

  public void setRecipientName(String recipientName) {
    this.recipientName = recipientName;
  }

  public String getSignals() {
    return signals;
  }

  public void setSignals(String signals) {
    this.signals = signals;
  }
}
