package com.signal.historyservice.controller;

import com.signal.historyservice.entity.NotificationLogEntity;
import com.signal.historyservice.entity.SignalEventEntity;
import com.signal.historyservice.repository.NotificationLogRepository;
import com.signal.historyservice.repository.SignalEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private static final int PAGE_SIZE = 20;

    private final SignalEventRepository signalEventRepository;
    private final NotificationLogRepository notificationLogRepository;

    @GetMapping("/")
    public String index() {
        return "redirect:/signals";
    }

    @GetMapping("/signals")
    public String signals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String stockCode,
            Model model
    ) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<SignalEventEntity> signalPage;

        if (status != null && !status.isBlank() && stockCode != null && !stockCode.isBlank()) {
            signalPage = signalEventRepository.findByStatusAndStockCodeOrderByEventAtDesc(
                    status.toUpperCase(), stockCode, pageable);
        } else if (status != null && !status.isBlank()) {
            signalPage = signalEventRepository.findByStatusOrderByEventAtDesc(status.toUpperCase(), pageable);
        } else if (stockCode != null && !stockCode.isBlank()) {
            signalPage = signalEventRepository.findByStockCodeOrderByEventAtDesc(stockCode, pageable);
        } else {
            signalPage = signalEventRepository.findAllByOrderByEventAtDesc(pageable);
        }

        model.addAttribute("signalPage", signalPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("status", status != null ? status : "");
        model.addAttribute("stockCode", stockCode != null ? stockCode : "");
        model.addAttribute("activeTab", "signals");
        return "signals";
    }

    @GetMapping("/notifications")
    public String notifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            Model model
    ) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE);
        Page<NotificationLogEntity> notifPage;

        if (channel != null && !channel.isBlank() && status != null && !status.isBlank()) {
            notifPage = notificationLogRepository.findByChannelAndStatusOrderByDispatchedAtDesc(
                    channel.toUpperCase(), status.toUpperCase(), pageable);
        } else if (channel != null && !channel.isBlank()) {
            notifPage = notificationLogRepository.findByChannelOrderByDispatchedAtDesc(
                    channel.toUpperCase(), pageable);
        } else if (status != null && !status.isBlank()) {
            notifPage = notificationLogRepository.findByStatusOrderByDispatchedAtDesc(
                    status.toUpperCase(), pageable);
        } else {
            notifPage = notificationLogRepository.findAllByOrderByDispatchedAtDesc(pageable);
        }

        model.addAttribute("notifPage", notifPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("channel", channel != null ? channel : "");
        model.addAttribute("status", status != null ? status : "");
        model.addAttribute("activeTab", "notifications");
        return "notifications";
    }
}
