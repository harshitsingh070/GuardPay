package com.guardpay.merchant;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A merchant referenced by transactions (reputation/familiarity context).
 */
@Entity
@Table(name = "merchants")
public class Merchant extends BaseEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "merchant_identifier", length = 128)
  private String merchantIdentifier;

  @Column(name = "domain")
  private String domain;

  @Column(name = "category", length = 128)
  private String category;

  @Column(name = "is_verified", nullable = false)
  private boolean verified = false;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getMerchantIdentifier() {
    return merchantIdentifier;
  }

  public void setMerchantIdentifier(String merchantIdentifier) {
    this.merchantIdentifier = merchantIdentifier;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public boolean isVerified() {
    return verified;
  }

  public void setVerified(boolean verified) {
    this.verified = verified;
  }
}
