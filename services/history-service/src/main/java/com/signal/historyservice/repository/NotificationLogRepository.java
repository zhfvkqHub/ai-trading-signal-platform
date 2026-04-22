package com.signal.historyservice.repository;

import com.signal.historyservice.entity.NotificationLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, Long> {

    Page<NotificationLogEntity> findAllByOrderByDispatchedAtDesc(Pageable pageable);

    Page<NotificationLogEntity> findByStatusOrderByDispatchedAtDesc(String status, Pageable pageable);

    Page<NotificationLogEntity> findByChannelOrderByDispatchedAtDesc(String channel, Pageable pageable);

    Page<NotificationLogEntity> findByChannelAndStatusOrderByDispatchedAtDesc(String channel, String status, Pageable pageable);
}
