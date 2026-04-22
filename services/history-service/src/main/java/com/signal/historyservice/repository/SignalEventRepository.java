package com.signal.historyservice.repository;

import com.signal.historyservice.entity.SignalEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalEventRepository extends JpaRepository<SignalEventEntity, Long> {

    Page<SignalEventEntity> findAllByOrderByEventAtDesc(Pageable pageable);

    Page<SignalEventEntity> findByStatusOrderByEventAtDesc(String status, Pageable pageable);

    Page<SignalEventEntity> findByStockCodeOrderByEventAtDesc(String stockCode, Pageable pageable);

    Page<SignalEventEntity> findByStatusAndStockCodeOrderByEventAtDesc(String status, String stockCode, Pageable pageable);
}
