package com.signal.historyservice.controller;

import com.signal.historyservice.entity.NotificationLogEntity;
import com.signal.historyservice.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationLogController {

    private final NotificationLogRepository notificationLogRepository;

    @GetMapping
    public Page<NotificationLogEntity> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status
    ) {
        Pageable pageable = PageRequest.of(page, size);

        if (channel != null && status != null) {
            return notificationLogRepository.findByChannelAndStatusOrderByDispatchedAtDesc(
                    channel.toUpperCase(), status.toUpperCase(), pageable);
        } else if (channel != null) {
            return notificationLogRepository.findByChannelOrderByDispatchedAtDesc(
                    channel.toUpperCase(), pageable);
        } else if (status != null) {
            return notificationLogRepository.findByStatusOrderByDispatchedAtDesc(
                    status.toUpperCase(), pageable);
        } else {
            return notificationLogRepository.findAllByOrderByDispatchedAtDesc(pageable);
        }
    }
}
