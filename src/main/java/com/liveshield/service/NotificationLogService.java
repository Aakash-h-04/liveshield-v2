package com.liveshield.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Audit and delivery tracker for all outbound Biosecurity notifications (Email & WhatsApp).
 */
@Service
public class NotificationLogService {

    public record NotificationEntry(
            String id,
            String channel, // "EMAIL" or "WHATSAPP"
            String recipient,
            String subject,
            String content,
            LocalDateTime sentAt,
            String status, // "SENT", "DELIVERED", "SIMULATED"
            String source // "RISK_ASSESSMENT", "GATE_ENTRY", "GATE_EXIT", "VETERINARY"
    ) {}

    private final List<NotificationEntry> entries = new CopyOnWriteArrayList<>();

    public void logEmail(String recipient, String subject, String content, String status, String source) {
        entries.add(0, new NotificationEntry(
                UUID.randomUUID().toString(),
                "EMAIL",
                recipient,
                subject,
                content,
                LocalDateTime.now(),
                status,
                source
        ));
        // Keep last 100 entries
        if (entries.size() > 100) {
            entries.remove(entries.size() - 1);
        }
    }

    public void logWhatsApp(String recipient, String message, String status, String source) {
        entries.add(0, new NotificationEntry(
                UUID.randomUUID().toString(),
                "WHATSAPP",
                recipient,
                "LiveShield WhatsApp Alert",
                message,
                LocalDateTime.now(),
                status,
                source
        ));
        if (entries.size() > 100) {
            entries.remove(entries.size() - 1);
        }
    }

    public List<NotificationEntry> getRecentNotifications(int limit) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    public List<NotificationEntry> getAllNotifications() {
        return new ArrayList<>(entries);
    }
}
