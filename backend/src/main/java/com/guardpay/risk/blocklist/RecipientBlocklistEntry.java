package com.guardpay.risk.blocklist;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.recipient.Recipient;
import com.guardpay.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A counterparty the authenticated workspace has marked as reported/fraudulent.
 *
 * <p>Blocklist membership is evidence, not the final decision: the
 * deterministic engine still combines it with every other signal.
 */
@Entity
@Table(name = "recipient_blocklist")
public class RecipientBlocklistEntry extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_recipient_blocklist_user"))
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "recipient_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_recipient_blocklist_recipient"))
  private Recipient recipient;

  @Column(name = "reason", length = 255)
  private String reason;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public void setRecipient(Recipient recipient) {
    this.recipient = recipient;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}

