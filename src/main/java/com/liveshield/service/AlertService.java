package com.liveshield.service;

import com.liveshield.entity.Alert;
import com.liveshield.entity.Farm;
import com.liveshield.entity.RiskAssessment;
import com.liveshield.entity.VeterinaryRecord;
import com.liveshield.repository.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Service
public class AlertService {

    private final AlertRepository repository;
    private final FarmService farmService;
    private final EmailNotificationService emailNotificationService;
    private final WhatsAppNotificationService whatsappNotificationService;

    private String[] adminEmails;

    public AlertService(
            AlertRepository repository,
            FarmService farmService,
            EmailNotificationService emailNotificationService,
            WhatsAppNotificationService whatsAppNotificationService,
            @Value("${liveshield.admin.emails:admin@liveshield.io,biosecurity@liveshield.io}") String adminEmails) {

        this.repository = repository;
        this.farmService = farmService;
        this.emailNotificationService = emailNotificationService;
        this.whatsappNotificationService = whatsAppNotificationService;

        if (adminEmails != null && !adminEmails.isBlank()) {
            this.adminEmails = adminEmails.split(",");
            for (int i = 0; i < this.adminEmails.length; i++) {
                this.adminEmails[i] = this.adminEmails[i].trim();
            }
        } else {
            this.adminEmails = new String[]{"admin@liveshield.io", "biosecurity@liveshield.io"};
        }
    }

    public String[] getAdminEmails() {
        return adminEmails;
    }

    public synchronized void updateAdminEmails(String[] emails) {
        if (emails != null && emails.length > 0) {
            this.adminEmails = emails;
        }
    }

    public List<Alert> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<Alert> findActive() {
        return repository.findByResolvedFalseOrderByCreatedAtDesc();
    }

    @Transactional
    public Alert createRiskAlert(RiskAssessment assessment) {
        Farm farm = farmService.findById(assessment.getFarm().getId());
        String severity = assessment.getRiskLevel();

        Alert alert = new Alert();
        alert.setFarm(farm);
        alert.setSeverity(severity);
        alert.setSource("RISK_ASSESSMENT");
        alert.setTitle("Biosecurity Risk Assessment: " + severity + " (" + assessment.getRiskScore() + "% Score)");
        alert.setDescription(String.format(
                "Risk assessment recorded for %s with overall risk score of %d%% (%s). Review priority control actions.",
                farm.getName(), assessment.getRiskScore(), severity
        ));

        Alert savedAlert = repository.save(alert);
        System.out.println(">>> NEW RISK ASSESSMENT ALERT CREATED: ID " + savedAlert.getId() + " - " + savedAlert.getTitle());

        sendRiskAssessmentNotification(assessment, savedAlert);

        return savedAlert;
    }

    @Transactional
    public Alert createVeterinaryAlert(
            VeterinaryRecord record) {

        Farm farm = record
                .getBatch()
                .getFarm();

        Alert alert = new Alert();

        alert.setFarm(farm);

        alert.setSeverity("HIGH");

        alert.setSource("VETERINARY");

        alert.setTitle(
                "Disease reported in livestock batch");

        alert.setDescription(
                "A disease or health condition has been " +
                        "reported for livestock batch " +
                        record.getBatch().getBatchCode() +
                        ". Review the veterinary record and " +
                        "take appropriate biosecurity measures.");

        Alert savedAlert = repository.save(alert);

        System.out.println(
                ">>> NEW VETERINARY ALERT CREATED: "
                        + savedAlert.getTitle());

        sendEmailNotification(savedAlert);

        System.out.println(
                ">>> EMAIL SENT FOR ALERT: "
                        + savedAlert.getTitle());

        return savedAlert;
    }

    @Transactional
    public void resolve(Long id) {

        Alert alert = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Alert not found: " + id));

        alert.setResolved(true);

        repository.save(alert);
    }

    private void sendEmailNotification(Alert alert) {

        String subject =
                "LiveShield Alert: "
                        + alert.getTitle();

        String message = """
                LiveShield Alert

                Title: %s
                Severity: %s
                Source: %s

                Description:
                %s

                Farm: %s
                Farm ID: %s

                Please review the LiveShield dashboard for further details.
                """.formatted(
                alert.getTitle(),
                alert.getSeverity(),
                alert.getSource(),
                alert.getDescription(),
                alert.getFarm().getName(),
                alert.getFarm().getId());

        // =====================================================
        // EMAIL NOTIFICATIONS
        // =====================================================

        for (String adminEmail : adminEmails) {

            if (adminEmail.isBlank()) {
                continue;
            }

            try {

                emailNotificationService.sendAlertEmail(
                        adminEmail,
                        subject,
                        message);

                System.out.println(
                        ">>> ADMIN EMAIL SENT SUCCESSFULLY TO: "
                                + adminEmail);

            } catch (Exception e) {

                System.err.println(
                        ">>> EMAIL NOTIFICATION FAILED FOR "
                                + adminEmail
                                + ": "
                                + e.getMessage());
            }
        }

        // =====================================================
        // WHATSAPP NOTIFICATIONS
        // =====================================================

        try {

            whatsappNotificationService.sendAlertWhatsApp(message, "ALERTS");

            System.out.println(
                    ">>> ADMIN WHATSAPP NOTIFICATIONS SENT SUCCESSFULLY");

        } catch (Exception e) {

            System.err.println(
                    ">>> WHATSAPP NOTIFICATION FAILED: "
                            + e.getMessage());
        }
    }

    private void sendRiskAssessmentNotification(RiskAssessment assessment, Alert alert) {
        Farm farm = alert.getFarm();
        String subject = String.format("[LiveShield Alert] Biosecurity Risk Assessment: %s - %s Risk (%d%%)",
                farm.getName(), assessment.getRiskLevel(), assessment.getRiskScore());

        StringBuilder sb = new StringBuilder();
        sb.append("====================================================================\n");
        sb.append("         LIVESHIELD BIOSECURITY RISK ASSESSMENT NOTIFICATION        \n");
        sb.append("====================================================================\n\n");
        sb.append("Farm Name:         ").append(farm.getName()).append("\n");
        sb.append("Farm Location:     ").append(farm.getLocation() != null ? farm.getLocation() : "—").append("\n");
        sb.append("Farm Owner:        ").append(farm.getOwnerName() != null ? farm.getOwnerName() : "—").append("\n");
        sb.append("Overall Risk:      ").append(assessment.getRiskLevel()).append("\n");
        sb.append("Risk Score:        ").append(assessment.getRiskScore()).append("%\n");
        sb.append("Livestock Count:   ").append(farm.getLivestockCount() != null ? farm.getLivestockCount() : 0).append("\n\n");

        sb.append("KEY CONTROL AUDIT STATUS:\n");
        sb.append(" - Perimeter Control:         ").append(assessment.getPerimeterControl()).append("\n");
        sb.append(" - Visitor Movement:          ").append(assessment.getVisitorControl()).append("\n");
        sb.append(" - Cleaning & Disinfection:   ").append(assessment.getCleaningDisinfection()).append("\n");
        sb.append(" - Equipment Hygiene:         ").append(assessment.getEquipmentHygiene()).append("\n");
        sb.append(" - Animal Health Monitoring:  ").append(assessment.getAnimalHealthMonitoring()).append("\n");
        sb.append(" - Sick Animal Isolation:     ").append(assessment.getSickAnimalIsolation()).append("\n");
        sb.append(" - Feed Storage:              ").append(assessment.getFeedStorage()).append("\n");
        sb.append(" - Water Source Safety:       ").append(assessment.getWaterSafety()).append("\n");
        sb.append(" - Waste Management:          ").append(assessment.getWasteManagement()).append("\n\n");

        sb.append("URGENT RECOMMENDATION:\n");
        if ("HIGH".equalsIgnoreCase(assessment.getRiskLevel())) {
            sb.append("🚨 HIGH BIOSECURITY THREAT: Immediately restrict external visitors, sanitize all gate entries, and isolate any suspicious stock.\n");
        } else if ("MEDIUM".equalsIgnoreCase(assessment.getRiskLevel())) {
            sb.append("⚠️ MODERATE RISK: Implement corrective actions on perimeter security and regular disinfection.\n");
        } else {
            sb.append("✓ LOW RISK: Farm controls meet current biosecurity standards. Maintain routine monitoring.\n");
        }
        sb.append("\nPortal Link: http://localhost:8081/risk-assessment?farmId=").append(farm.getId()).append("\n");

        String fullMessage = sb.toString();

        // 1. Send Email to all configured admins
        for (String adminEmail : adminEmails) {
            if (adminEmail != null && !adminEmail.isBlank()) {
                try {
                    emailNotificationService.sendAlertEmail(adminEmail.trim(), subject, fullMessage, "RISK_ASSESSMENT");
                } catch (Exception ex) {
                    System.err.println(">>> Risk assessment email failed for " + adminEmail + ": " + ex.getMessage());
                }
            }
        }

        // 2. Send WhatsApp notification
        String waMessage = String.format(
                "🛡️ *LiveShield Risk Assessment Alert*\n\n" +
                "*Farm:* %s\n" +
                "*Risk Level:* %s (%d%% Score)\n" +
                "*Status:* %s\n\n" +
                "Review priority controls on LiveShield:\nhttp://localhost:8081/risk-assessment?farmId=%d",
                farm.getName(), assessment.getRiskLevel(), assessment.getRiskScore(),
                "HIGH".equalsIgnoreCase(assessment.getRiskLevel()) ? "🚨 Action Required" : "Monitored",
                farm.getId()
        );

        try {
            whatsappNotificationService.sendAlertWhatsApp(waMessage, "RISK_ASSESSMENT");
        } catch (Exception ex) {
            System.err.println(">>> Risk assessment WhatsApp failed: " + ex.getMessage());
        }
    }
}