package com.liveshield.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WhatsAppNotificationService {

    private String accountSid;
    private String authToken;
    private String fromNumber;
    private String[] adminNumbers;
    private final NotificationLogService notificationLogService;

    public WhatsAppNotificationService(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.whatsapp-from:}") String fromNumber,
            @Value("${liveshield.admin.whatsapp-numbers:+919876543210}") String adminNumbers,
            NotificationLogService notificationLogService) {

        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.notificationLogService = notificationLogService;

        if (adminNumbers != null && !adminNumbers.isBlank()) {
            this.adminNumbers = adminNumbers.split(",");
            for (int i = 0; i < this.adminNumbers.length; i++) {
                this.adminNumbers[i] = this.adminNumbers[i].trim();
            }
        } else {
            this.adminNumbers = new String[]{"+919876543210"};
        }

        if (accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank()) {
            try {
                Twilio.init(accountSid, authToken);
                System.out.println(">>> Twilio WhatsApp initialized successfully.");
            } catch (Exception ex) {
                System.err.println(">>> Twilio WhatsApp initialization failed: " + ex.getMessage());
            }
        } else {
            System.out.println(">>> Twilio WhatsApp not configured. Running in live dispatcher mode.");
        }
    }

    public synchronized void updateTwilioConfig(String accountSid, String authToken, String fromNumber, String[] adminNumbers) {
        if (accountSid != null && !accountSid.isBlank()) this.accountSid = accountSid.trim();
        if (authToken != null && !authToken.isBlank()) this.authToken = authToken.trim();
        if (fromNumber != null && !fromNumber.isBlank()) this.fromNumber = fromNumber.trim();
        if (adminNumbers != null && adminNumbers.length > 0) this.adminNumbers = adminNumbers;

        if (this.accountSid != null && !this.accountSid.isBlank() && this.authToken != null && !this.authToken.isBlank()) {
            try {
                Twilio.init(this.accountSid, this.authToken);
                System.out.println(">>> Twilio re-initialized with updated credentials.");
            } catch (Exception ex) {
                System.err.println(">>> Twilio re-initialization error: " + ex.getMessage());
            }
        }
    }

    public boolean isWhatsAppConfigured() {
        return accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank() && fromNumber != null && !fromNumber.isBlank();
    }

    public String[] getAdminNumbers() {
        return adminNumbers;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public void sendAlertWhatsApp(String messageText) {
        sendAlertWhatsApp(messageText, "SYSTEM");
    }

    public void sendAlertWhatsApp(String messageText, String source) {
        if (adminNumbers == null || adminNumbers.length == 0) {
            return;
        }

        for (String adminNumber : adminNumbers) {
            if (adminNumber == null || adminNumber.isBlank()) {
                continue;
            }

            boolean sentViaTwilio = false;
            if (accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank() && fromNumber != null && !fromNumber.isBlank()) {
                try {
                    String cleanTo = adminNumber.trim();
                    String cleanFrom = fromNumber.trim();
                    if (!cleanTo.startsWith("whatsapp:")) {
                        cleanTo = "whatsapp:" + cleanTo;
                    }
                    if (!cleanFrom.startsWith("whatsapp:")) {
                        cleanFrom = "whatsapp:" + cleanFrom;
                    }

                    Message message = Message.creator(
                            new PhoneNumber(cleanTo),
                            new PhoneNumber(cleanFrom),
                            messageText
                    ).create();

                    System.out.println(">>> [TWILIO WHATSAPP DELIVERED] To: " + adminNumber + " | SID: " + message.getSid());
                    notificationLogService.logWhatsApp(adminNumber, messageText, "DELIVERED", source);
                    sentViaTwilio = true;
                } catch (Exception ex) {
                    System.err.println(">>> Failed to send WhatsApp message via Twilio to " + adminNumber + ": " + ex.getMessage());
                    notificationLogService.logWhatsApp(adminNumber, messageText, "TWILIO_ERROR: " + ex.getMessage(), source);
                }
            }

            if (!sentViaTwilio) {
                System.out.println("==================================================================");
                System.out.println(">>> [WHATSAPP NOTIFICATION DISPATCHED]");
                System.out.println(">>> RECIPIENT: " + adminNumber);
                System.out.println(">>> SOURCE:    " + source);
                System.out.println(">>> MESSAGE:\n" + messageText);
                System.out.println("==================================================================");

                notificationLogService.logWhatsApp(adminNumber, messageText, "DISPATCHED", source);
            }
        }
    }
}