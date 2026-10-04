package com.liveshield.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import jakarta.annotation.PostConstruct;
import jakarta.mail.Authenticator;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Service
public class EmailNotificationService {

    private static final String APPLICATION_NAME = "LiveShield";

    @Value("${google.gmail.client-id:}")
    private String clientId;

    @Value("${google.gmail.client-secret:}")
    private String clientSecret;

    @Value("${google.gmail.refresh-token:}")
    private String refreshToken;

    @Value("${google.gmail.sender-email:}")
    private String senderEmail;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private int smtpPort;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.from:}")
    private String smtpFrom;

    private Gmail gmailService;

    @PostConstruct
    public void initialize() {

        if (clientId == null || clientId.isBlank() || refreshToken == null || refreshToken.isBlank()) {
            System.out.println(
                    ">>> Gmail API credentials not configured. Email notifications disabled."
            );
            return;
        }

        try {

            var httpTransport =
                    GoogleNetHttpTransport.newTrustedTransport();

            GoogleCredential credential =
                    new GoogleCredential.Builder()
                            .setTransport(httpTransport)
                            .setJsonFactory(
                                    GsonFactory.getDefaultInstance()
                            )
                            .setClientSecrets(
                                    clientId,
                                    clientSecret
                            )
                            .build()
                            .setRefreshToken(refreshToken);

            gmailService =
                    new Gmail.Builder(
                            httpTransport,
                            GsonFactory.getDefaultInstance(),
                            credential
                    )
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            System.out.println(
                    ">>> Gmail API initialized successfully"
            );

        } catch (Exception ex) {

            System.err.println(
                    ">>> Gmail API initialization failed: "
                            + ex.getMessage()
            );
        }
    }

    private final NotificationLogService notificationLogService;

    public EmailNotificationService(NotificationLogService notificationLogService) {
        this.notificationLogService = notificationLogService;
    }

    public synchronized void updateSmtpConfig(String host, int port, String username, String password, String from) {
        this.smtpHost = host;
        this.smtpPort = port;
        this.smtpUsername = username;
        this.smtpPassword = password;
        this.smtpFrom = from;
    }

    public boolean isEmailConfigured() {
        return (gmailService != null) || (smtpUsername != null && !smtpUsername.isBlank() && smtpPassword != null && !smtpPassword.isBlank());
    }

    public String getEmailProviderType() {
        if (gmailService != null) return "GMAIL_OAUTH";
        if (smtpUsername != null && !smtpUsername.isBlank() && smtpPassword != null && !smtpPassword.isBlank()) return "SMTP (" + smtpHost + ")";
        return "PENDING SETUP";
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void sendAlertEmail(
            String recipient,
            String subject,
            String messageText) {
        sendAlertEmail(recipient, subject, messageText, "SYSTEM");
    }

    public void sendAlertEmail(
            String recipient,
            String subject,
            String messageText,
            String source) {

        if (recipient == null || recipient.isBlank()) {
            return;
        }

        recipient = recipient.trim();

        if (gmailService != null) {
            try {
                MimeMessage email = createEmail(recipient, subject, messageText);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                email.writeTo(buffer);
                String encodedEmail = com.google.api.client.util.Base64.encodeBase64URLSafeString(buffer.toByteArray());

                Message message = new Message();
                message.setRaw(encodedEmail);
                Message sentMessage = gmailService.users().messages().send("me", message).execute();

                System.out.println(">>> [GMAIL API DELIVERED] To: " + recipient + " | MsgId: " + sentMessage.getId());
                notificationLogService.logEmail(recipient, subject, messageText, "DELIVERED", source);
                return;
            } catch (Exception ex) {
                System.err.println(">>> Gmail API send attempt encountered error: " + ex.getMessage());
                notificationLogService.logEmail(recipient, subject, messageText, "GMAIL_ERROR: " + ex.getMessage(), source);
            }
        }

        // 2. Standard SMTP Delivery Attempt (e.g. Gmail App Password, Outlook, SendGrid)
        if (smtpUsername != null && !smtpUsername.isBlank() && smtpPassword != null && !smtpPassword.isBlank()) {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                props.put("mail.smtp.host", (smtpHost != null && !smtpHost.isBlank()) ? smtpHost.trim() : "smtp.gmail.com");
                props.put("mail.smtp.port", String.valueOf(smtpPort > 0 ? smtpPort : 587));

                final String user = smtpUsername.trim();
                final String pass = smtpPassword.trim();

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, pass);
                    }
                });

                MimeMessage email = new MimeMessage(session);
                String fromAddress = (smtpFrom != null && !smtpFrom.isBlank()) ? smtpFrom.trim() : user;
                email.setFrom(new InternetAddress(fromAddress, "LiveShield Biosecurity"));
                email.setRecipient(RecipientType.TO, new InternetAddress(recipient));
                email.setSubject(subject, StandardCharsets.UTF_8.name());
                email.setText(messageText, StandardCharsets.UTF_8.name());

                Transport.send(email);

                System.out.println(">>> [SMTP EMAIL DELIVERED] Successfully sent real email to: " + recipient);
                notificationLogService.logEmail(recipient, subject, messageText, "DELIVERED", source);
                return;
            } catch (Exception ex) {
                System.err.println(">>> SMTP send error to " + recipient + ": " + ex.getMessage());
                notificationLogService.logEmail(recipient, subject, messageText, "SMTP_ERROR: " + ex.getMessage(), source);
            }
        }

        // Live delivery logger & audit recorder fallback
        System.out.println("==================================================================");
        System.out.println(">>> [EMAIL NOTIFICATION DISPATCHED]");
        System.out.println(">>> RECIPIENT: " + recipient);
        System.out.println(">>> SUBJECT:   " + subject);
        System.out.println(">>> SOURCE:    " + source);
        System.out.println(">>> NOTE: Configure SMTP credentials in .env to deliver real email.");
        System.out.println("==================================================================");

        notificationLogService.logEmail(recipient, subject, messageText, "DISPATCHED (NO SMTP/GMAIL SETUP)", source);
    }

    private MimeMessage createEmail(
            String recipient,
            String subject,
            String messageText) throws Exception {

        Properties properties = new Properties();

        Session session =
                Session.getDefaultInstance(
                        properties,
                        null
                );

        MimeMessage email =
                new MimeMessage(session);

        String from = (senderEmail != null && !senderEmail.isBlank())
                ? senderEmail
                : "biosecurity@liveshield.io";

        email.setFrom(
                new InternetAddress(from)
        );

        email.setRecipient(
                RecipientType.TO,
                new InternetAddress(recipient)
        );

        email.setSubject(
                subject,
                StandardCharsets.UTF_8.name()
        );

        email.setText(
                messageText,
                StandardCharsets.UTF_8.name()
        );

        return email;
    }
}