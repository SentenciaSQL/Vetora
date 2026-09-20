package com.animalin.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByCode(String code);
    List<Plan> findByActiveTrueOrderByMonthlyPriceAsc();
    Optional<Plan> findByPaddleProductId(String paddleProductId);
    Optional<Plan> findByPaddleMonthlyPriceId(String paddleMonthlyPriceId);
    Optional<Plan> findByPaddleAnnualPriceId(String paddleAnnualPriceId);
    Optional<Plan> findByPaddleMonthlyPriceIdOrPaddleAnnualPriceId(String monthlyPriceId, String annualPriceId);
}
