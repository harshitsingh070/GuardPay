package com.guardpay.behavior;

import com.guardpay.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Per-hour payment distribution for one user. */
public interface UserHourlyBehaviorRepository
    extends JpaRepository<UserHourlyBehavior, UUID> {

  Optional<UserHourlyBehavior> findByUserIdAndHourOfDay(UUID userId, int hourOfDay);

  List<UserHourlyBehavior> findByUserIdOrderByHourOfDayAsc(
      UUID userId, Pageable pageable);

  @Query(
      """
      select sum(transaction_count) from UserHourlyBehavior
      where user.id = :userId
        and hour_of_day >= :startHour
        and hour_of_day <= :endHour
      """)
  Long countTransactionsInHours(
      @Param("userId") UUID userId,
      @Param("startHour") int startHour,
      @Param("endHour") int endHour);
}
