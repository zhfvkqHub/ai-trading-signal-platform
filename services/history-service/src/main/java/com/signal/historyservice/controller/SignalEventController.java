package com.signal.historyservice.controller;

import com.signal.historyservice.entity.SignalEventEntity;
import com.signal.historyservice.repository.SignalEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalEventController {

    private final SignalEventRepository signalEventRepository;

    @GetMapping
    public Page<SignalEventEntity> getSignals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String stockCode
    ) {
        Pageable pageable = PageRequest.of(page, size);

        if (status != null && stockCode != null) {
            return signalEventRepository.findByStatusAndStockCodeOrderByEventAtDesc(
                    status.toUpperCase(), stockCode, pageable);
        } else if (status != null) {
            return signalEventRepository.findByStatusOrderByEventAtDesc(
                    status.toUpperCase(), pageable);
        } else if (stockCode != null) {
            return signalEventRepository.findByStockCodeOrderByEventAtDesc(stockCode, pageable);
        } else {
            return signalEventRepository.findAllByOrderByEventAtDesc(pageable);
        }
    }
}
