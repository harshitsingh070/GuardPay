package com.guardpay.merchant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Basic persistence for {@link Merchant} plus name lookup for payee resolution. */
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

  Optional<Merchant> findByNameIgnoreCase(String name);
}
