package com.liveshield.controller;

import com.liveshield.service.AlertService;
import com.liveshield.service.EmailNotificationService;
import com.liveshield.service.NotificationLogService;
import com.liveshield.service.WhatsAppNotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/notifications")
public class NotificationConfigController {

    private final EmailNotificationService emailService;
    private final WhatsAppNotificationService whatsappService;
    private final AlertService alertService;
    private final NotificationLogService logService;

    public NotificationConfigController(
            EmailNotificationService emailService,
            WhatsAppNotificationService whatsappService,
            AlertService alertService,
            NotificationLogService logService) {
        this.emailService = emailService;
        this.whatsappService = whatsappService;
        this.alertService = alertService;
        this.logService = logService;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        String adminEmails = String.join(", ", alertService.getAdminEmails());
        String adminWhatsApp = String.join(", ", whatsappService.getAdminNumbers());

        model.addAttribute("adminEmails", adminEmails);
        model.addAttribute("adminWhatsApp", adminWhatsApp);
        model.addAttribute("isEmailConfigured", emailService.isEmailConfigured());
        model.addAttribute("emailProviderType", emailService.getEmailProviderType());
        model.addAttribute("smtpUsername", emailService.getSmtpUsername());
        model.addAttribute("smtpHost", emailService.getSmtpHost());
        model.addAttribute("isWhatsAppConfigured", whatsappService.isWhatsAppConfigured());
        model.addAttribute("twilioFrom", whatsappService.getFromNumber());
        model.addAttribute("recentNotifications", logService.getRecentNotifications(15));

        return "notification-settings";
    }

    @PostMapping("/settings")
    public String saveSettings(
            @RequestParam(required = false) String adminEmails,
            @RequestParam(required = false) String adminWhatsApp,
            @RequestParam(required = false) String smtpHost,
            @RequestParam(required = false, defaultValue = "587") int smtpPort,
            @RequestParam(required = false) String smtpUsername,
            @RequestParam(required = false) String smtpPassword,
            @RequestParam(required = false) String smtpFrom,
            @RequestParam(required = false) String twilioAccountSid,
            @RequestParam(required = false) String twilioAuthToken,
            @RequestParam(required = false) String twilioFrom,
            RedirectAttributes redirectAttrs) {

        try {
            // 1. Update runtime services immediately
            if (adminEmails != null && !adminEmails.isBlank()) {
                String[] emails = Arrays.stream(adminEmails.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toArray(String[]::new);
                alertService.updateAdminEmails(emails);
            }

            if (adminWhatsApp != null && !adminWhatsApp.isBlank()) {
                String[] numbers = Arrays.stream(adminWhatsApp.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toArray(String[]::new);
                whatsappService.updateTwilioConfig(twilioAccountSid, twilioAuthToken, twilioFrom, numbers);
            }

            if (smtpUsername != null && !smtpUsername.isBlank() && smtpPassword != null && !smtpPassword.isBlank()) {
                emailService.updateSmtpConfig(
                        smtpHost != null && !smtpHost.isBlank() ? smtpHost : "smtp.gmail.com",
                        smtpPort > 0 ? smtpPort : 587,
                        smtpUsername.trim(),
                        smtpPassword.trim(),
                        smtpFrom != null && !smtpFrom.isBlank() ? smtpFrom.trim() : smtpUsername.trim()
                );
            }

            // 2. Persist to .env file
            updateEnvFile(adminEmails, adminWhatsApp, smtpHost, smtpPort, smtpUsername, smtpPassword, smtpFrom,
                    twilioAccountSid, twilioAuthToken, twilioFrom);

            redirectAttrs.addFlashAttribute("successMessage",
                    "Notification settings saved successfully and loaded into runtime!");

        } catch (Exception ex) {
            redirectAttrs.addFlashAttribute("errorMessage",
                    "Error saving settings: " + ex.getMessage());
        }

        return "redirect:/notifications/settings";
    }

    @PostMapping("/test-email")
    public String testEmail(
            @RequestParam(required = false) String targetEmail,
            RedirectAttributes redirectAttrs) {

        String recipient = (targetEmail != null && !targetEmail.isBlank())
                ? targetEmail.trim()
                : (alertService.getAdminEmails().length > 0 ? alertService.getAdminEmails()[0] : "admin@liveshield.io");

        String subject = "[LiveShield Test] Biosecurity Dispatch Diagnostic";
        String message = """
                LiveShield Biosecurity Notification Test
                
                Time: %s
                Recipient: %s
                Provider: %s
                
                This is a live test notification verifying end-to-end email delivery for the LiveShield Biosecurity Companion portal.
                If you received this email, automated risk assessments and veterinary alerts will successfully reach your inbox!
                """.formatted(java.time.LocalDateTime.now(), recipient, emailService.getEmailProviderType());

        emailService.sendAlertEmail(recipient, subject, message, "DIAGNOSTIC_TEST");

        redirectAttrs.addFlashAttribute("testMessage",
                "Email diagnostic dispatched to " + recipient + ". Check below in the Outbound Dispatch Stream for delivery confirmation status.");

        return "redirect:/notifications/settings";
    }

    @PostMapping("/test-whatsapp")
    public String testWhatsApp(
            @RequestParam(required = false) String targetPhone,
            RedirectAttributes redirectAttrs) {

        String testPhone = (targetPhone != null && !targetPhone.isBlank())
                ? targetPhone.trim()
                : (whatsappService.getAdminNumbers().length > 0 ? whatsappService.getAdminNumbers()[0] : "+919876543210");

        String message = String.format(
                "🛡️ *LiveShield WhatsApp Diagnostic*\n\n" +
                "Time: %s\n" +
                "Target: %s\n\n" +
                "Live test verifying real-time biosecurity alert routing. Farm threat notifications and Smart Gate authorizations will deliver here!",
                java.time.LocalDateTime.now(), testPhone
        );

        whatsappService.sendAlertWhatsApp(message, "DIAGNOSTIC_TEST");

        redirectAttrs.addFlashAttribute("testMessage",
                "WhatsApp diagnostic dispatched to " + testPhone + ". Check below in the Outbound Dispatch Stream for delivery confirmation status.");

        return "redirect:/notifications/settings";
    }

    private void updateEnvFile(
            String adminEmails, String adminWhatsApp,
            String smtpHost, int smtpPort, String smtpUsername, String smtpPassword, String smtpFrom,
            String twilioAccountSid, String twilioAuthToken, String twilioFrom) {

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ============================================================\n");
            sb.append("# LIVESHIELD NOTIFICATION CONFIGURATION (.env)\n");
            sb.append("# ============================================================\n\n");

            sb.append("# 1. RECIPIENT SETTINGS\n");
            sb.append("ADMIN_EMAILS=").append(adminEmails != null ? adminEmails.trim() : "").append("\n");
            sb.append("LIVESHIELD_ADMIN_WHATSAPP_NUMBERS=").append(adminWhatsApp != null ? adminWhatsApp.trim() : "").append("\n\n");

            sb.append("# 2. EMAIL SENDER - SMTP\n");
            sb.append("SPRING_MAIL_HOST=").append(smtpHost != null && !smtpHost.isBlank() ? smtpHost.trim() : "smtp.gmail.com").append("\n");
            sb.append("SPRING_MAIL_PORT=").append(smtpPort > 0 ? smtpPort : 587).append("\n");
            sb.append("SPRING_MAIL_USERNAME=").append(smtpUsername != null ? smtpUsername.trim() : "").append("\n");
            sb.append("SPRING_MAIL_PASSWORD=").append(smtpPassword != null ? smtpPassword.trim() : "").append("\n");
            sb.append("SPRING_MAIL_FROM=").append(smtpFrom != null ? smtpFrom.trim() : "").append("\n\n");

            sb.append("# 3. WHATSAPP SENDER - TWILIO\n");
            sb.append("TWILIO_ACCOUNT_SID=").append(twilioAccountSid != null ? twilioAccountSid.trim() : "").append("\n");
            sb.append("TWILIO_AUTH_TOKEN=").append(twilioAuthToken != null ? twilioAuthToken.trim() : "").append("\n");
            sb.append("TWILIO_WHATSAPP_FROM=").append(twilioFrom != null && !twilioFrom.isBlank() ? twilioFrom.trim() : "whatsapp:+14155238886").append("\n");

            Files.writeString(Paths.get(".env"), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            System.err.println("Could not write to .env: " + ex.getMessage());
        }
    }
}
