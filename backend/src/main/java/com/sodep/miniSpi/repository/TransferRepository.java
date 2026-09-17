package com.sodep.miniSpi.repository;

import com.sodep.miniSpi.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
    List<Transfer> findTop20ByOrderByCreatedAtDesc();
}
