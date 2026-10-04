package com.liveshield.controller;

import com.liveshield.entity.Farm;
import com.liveshield.entity.FarmVisit;
import com.liveshield.entity.Visitor;
import com.liveshield.entity.VisitorLicence;
import com.liveshield.repository.FarmVisitRepository;
import com.liveshield.service.FarmService;
import com.liveshield.service.QrCodeService;
import com.liveshield.service.VisitorCheckInService;
import com.liveshield.service.VisitorService;
import com.liveshield.service.VisitorVerificationService;
import com.liveshield.repository.VisitorRepository;
import com.liveshield.service.FaceVerificationService;
import com.liveshield.service.EmailNotificationService;
import com.liveshield.service.WhatsAppNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/visitors")
public class VisitorController {

    private final VisitorService visitorService;
    private final FarmService farmService;
    private final QrCodeService qrCodeService;
    private final VisitorVerificationService verificationService;
    private final VisitorCheckInService checkInService;
    private final FarmVisitRepository farmVisitRepository;
    private final FaceVerificationService faceVerificationService;
    private final EmailNotificationService emailNotificationService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final VisitorRepository visitorRepository;
    private final String[] adminEmails;

    public VisitorController(
            VisitorService visitorService,
            QrCodeService qrCodeService,
            VisitorVerificationService verificationService,
            FarmService farmService,
            VisitorCheckInService checkInService,
            FarmVisitRepository farmVisitRepository,
            FaceVerificationService faceVerificationService,
            EmailNotificationService emailNotificationService,
            WhatsAppNotificationService whatsAppNotificationService,
            VisitorRepository visitorRepository,
            @Value("${liveshield.admin.emails:}") String adminEmails) {

        this.visitorService = visitorService;
        this.qrCodeService = qrCodeService;
        this.verificationService = verificationService;
        this.farmService = farmService;
        this.checkInService = checkInService;
        this.farmVisitRepository = farmVisitRepository;
        this.faceVerificationService = faceVerificationService;
        this.emailNotificationService = emailNotificationService;
        this.whatsAppNotificationService = whatsAppNotificationService;
        this.visitorRepository = visitorRepository;

        if (adminEmails != null && !adminEmails.isBlank()) {
            this.adminEmails = adminEmails.split(",");
            for (int i = 0; i < this.adminEmails.length; i++) {
                this.adminEmails[i] = this.adminEmails[i].trim();
            }
        } else {
            this.adminEmails = new String[0];
        }
    }

    /*
     * Visitor Management dashboard.
     */
    @GetMapping
    public String visitors(Model model) {

        List<FarmVisit> activeVisits = farmVisitRepository
                .findByCheckOutTimeIsNullOrderByCheckInTimeDesc();

        List<Farm> farms = farmService.findAll();

        model.addAttribute("activeVisits", activeVisits);
        model.addAttribute("farms", farms);

        model.addAttribute(
                "registeredActiveVisitors",
                visitorService.getRegisteredActiveVisitors());

        return "visitors";
    }

    /*
     * Registration page.
     */
    @GetMapping("/register")
    public String registerForm(Model model) {

        model.addAttribute(
                "visitor",
                new Visitor());

        model.addAttribute(
                "licence",
                new VisitorLicence());

        return "visitor-register";
    }

    /*
     * Register a new visitor.
     */
    @PostMapping("/register")
    public String register(
            @ModelAttribute("visitor") Visitor visitor,

            @ModelAttribute("licence") VisitorLicence licence,

            Model model) {

        try {

            Visitor saved = visitorService.registerVisitor(
                    visitor,
                    licence);

            return "redirect:/visitors/"
                    + saved.getVisitorCode();

        } catch (IllegalArgumentException ex) {

            model.addAttribute(
                    "error",
                    ex.getMessage());

            return "visitor-register";
        }
    }

    /*
     * Search a visitor using the permanent
     * Visitor ID encoded in the QR/barcode.
     */
    @GetMapping("/lookup")
    public String lookup(
            @RequestParam String visitorCode,
            Model model) {

        try {

            VisitorService.VisitorProfile profile = visitorService.loadProfile(visitorCode);

            Visitor visitor = profile.visitor();

            List<FarmVisit> visitHistory = visitorService.getVisitHistory(visitor.getId());

            List<FarmVisit> recentFarmVisits = visitorService.getRecentFarmVisits(
                    visitor.getId(),
                    5);

            model.addAttribute("profile", profile);

            model.addAttribute(
                    "visitHistory",
                    visitHistory);

            model.addAttribute(
                    "recentFarmVisits",
                    recentFarmVisits);

            String qrCode = qrCodeService.generateBase64(
                    visitor.getVisitorCode());

            model.addAttribute(
                    "qrCode",
                    qrCode);

            return "visitor-profile";

        } catch (IllegalArgumentException ex) {

            model.addAttribute(
                    "error",
                    ex.getMessage());

            return "error";
        }
    }

    @GetMapping("/scan")
    public String scanPage() {
        return "visitor-scan";
    }

    @GetMapping("/{visitorCode}/verify")
    public String verifyVisitor(
            @PathVariable String visitorCode,
            Model model) {

        try {

            VisitorService.VisitorProfile profile = visitorService.loadProfile(visitorCode);

            VisitorVerificationService.VerificationResult result = verificationService.verify(profile);

            Visitor visitor = profile.visitor();

            List<Farm> farms = farmService.findAll();

            List<FarmVisit> recentFarmVisits = visitorService.getRecentFarmVisits(
                    visitor.getId(),
                    5);

            model.addAttribute(
                    "profile",
                    profile);

            model.addAttribute(
                    "verification",
                    result);

            model.addAttribute(
                    "recentFarmVisits",
                    recentFarmVisits);

            model.addAttribute("farms", farms);

            return "visitor-verification";

        } catch (IllegalArgumentException ex) {

            model.addAttribute(
                    "error",
                    ex.getMessage());

            return "error";
        }
    }

    @GetMapping("/{visitorCode}/check-in")
    public String checkInForm(
            @PathVariable String visitorCode,
            Model model) {

        try {
            VisitorService.VisitorProfile profile = visitorService.loadProfile(visitorCode);

            VisitorVerificationService.VerificationResult verification = verificationService.verify(profile);

            if (!"ALLOWED".equalsIgnoreCase(
                    verification.decision())) {

                model.addAttribute("profile", profile);
                model.addAttribute("verification", verification);

                return "visitor-verification";
            }

            List<Farm> farms = farmService.findAll();

            model.addAttribute("profile", profile);
            model.addAttribute("farms", farms);

            return "visitor-check-in";

        } catch (IllegalArgumentException ex) {

            model.addAttribute("error", ex.getMessage());

            return "error";
        }
    }

    @PostMapping("/{visitorCode}/check-in")
    public String checkIn(
            @PathVariable String visitorCode,
            @RequestParam Long farmId,
            @RequestParam String purpose,
            @RequestParam(required = false) String vehicleNumber,
            Model model) {

        try {

            Farm farm = farmService.findById(farmId);

            FarmVisit visit = checkInService.checkIn(
                    visitorCode,
                    farm,
                    purpose,
                    vehicleNumber);

            return "redirect:/visitors/check-in-success/"
                    + visit.getId();

        } catch (IllegalArgumentException ex) {

            VisitorService.VisitorProfile profile = visitorService.loadProfile(visitorCode);

            List<Farm> farms = farmService.findAll();

            model.addAttribute("profile", profile);
            model.addAttribute("farms", farms);
            model.addAttribute("error", ex.getMessage());

            return "visitor-check-in";
        }
    }

    @GetMapping("/check-in-success/{visitId}")
    public String checkInSuccess(
            @PathVariable Long visitId,
            Model model) {

        try {

            FarmVisit visit = farmVisitRepository
                    .findById(visitId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Farm visit not found."));

            model.addAttribute("visit", visit);

            return "visitor-check-in-success";

        } catch (IllegalArgumentException ex) {

            model.addAttribute("error", ex.getMessage());

            return "error";
        }
    }

    @PostMapping("/{visitorCode}/biosecurity/verify")
    public String verifyBiosecurity(
            @PathVariable String visitorCode,
            @RequestParam(required = false) String remarks) {

        Visitor visitor = visitorService
                .findByVisitorCode(visitorCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Visitor not found."));

        visitorService.verifyBiosecurity(
                visitor.getId(),
                remarks);

        return "redirect:/visitors/"
                + visitorCode
                + "/verify";
    }

    @PostMapping("/check-out/{visitId}")
    public String checkOut(
            @PathVariable Long visitId,
            @RequestParam(required = false) String returnUrl,
            Model model) {

        try {

            FarmVisit visit = checkInService.checkOut(visitId);

            if (returnUrl != null && !returnUrl.isBlank()) {
                return "redirect:" + returnUrl;
            }

            return "redirect:/visitors";

        } catch (IllegalArgumentException ex) {

            model.addAttribute("error", ex.getMessage());

            return "error";
        }
    }

    /*
     * Display a visitor profile.
     */
    @GetMapping("/{visitorCode}")
    public String profile(
            @PathVariable String visitorCode,
            Model model) {

        try {

            VisitorService.VisitorProfile profile = visitorService.loadProfile(visitorCode);

            Visitor visitor = profile.visitor();

            List<FarmVisit> visitHistory = visitorService.getVisitHistory(
                    visitor.getId());

            List<FarmVisit> recentFarmVisits = visitorService.getRecentFarmVisits(
                    visitor.getId(),
                    5);

            model.addAttribute(
                    "recentFarmVisits",
                    recentFarmVisits);

            model.addAttribute("profile", profile);

            model.addAttribute(
                    "visitHistory",
                    visitHistory);

            String qrCode = qrCodeService.generateBase64(
                    visitor.getVisitorCode());

            model.addAttribute(
                    "qrCode",
                    qrCode);

            return "visitor-profile";

        } catch (IllegalArgumentException ex) {

            model.addAttribute(
                    "error",
                    ex.getMessage());

            return "error";
        }
    }

    @PostMapping("/licence/{licenceId}/verify")
    public String verifyLicence(
            @PathVariable Long licenceId,
            @RequestParam(required = false) String remarks) {

        visitorService.verifyLicence(licenceId, remarks);

        return "redirect:/visitors";
    }

    @PostMapping("/licence/{licenceId}/reject")
    public String rejectLicence(
            @PathVariable Long licenceId,
            @RequestParam(required = false) String remarks) {

        visitorService.rejectLicence(licenceId, remarks);

        return "redirect:/visitors";
    }

    @GetMapping("/licence/{licenceId}/review")
    public String reviewLicence(
            @PathVariable Long licenceId,
            Model model) {

        VisitorLicence licence = visitorService.findLicenceById(licenceId);

        model.addAttribute("licence", licence);

        return "visitor-licence-review";
    }

    /*
     * =========================================================
     * SMART GATE TERMINAL ENDPOINTS
     * =========================================================
     */

    @GetMapping("/smart-gate")
    public String smartGate(
            @RequestParam(required = false) Long farmId,
            Model model) {

        List<Farm> farms = farmService.findAll();
        Long selectedFarmId = (farmId != null) ? farmId : (farms.isEmpty() ? null : farms.get(0).getId());

        model.addAttribute("farms", farms);
        model.addAttribute("selectedFarmId", selectedFarmId);

        return "smart-gate";
    }

    @PostMapping("/smart-gate/verify")
    @ResponseBody
    public ResponseEntity<?> verifySmartGate(@RequestBody SmartGateVerifyRequest req) {

        if (req == null || req.visitorCode() == null || req.visitorCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("found", false, "message", "Visitor code is required."));
        }

        String code = req.visitorCode().trim().toUpperCase();
        var optVisitor = visitorService.findByVisitorCode(code);

        if (optVisitor.isEmpty()) {
            return ResponseEntity.ok(new SmartGateVerifyResponse(
                    false,
                    code,
                    null,
                    null,
                    null,
                    null,
                    null,
                    req.currentPhoto(),
                    new FaceVerificationService.FaceMatchResult(false, 0, "Visitor not found in registry."),
                    new VisitorVerificationService.PastWeekExposureResult(false, List.of(), "Visitor not registered."),
                    new VisitorVerificationService.VerificationResult("DENIED", "Visitor not registered.", List.of("Unknown visitor identity.")),
                    false,
                    null,
                    false,
                    "Visitor identity code not recognized."
            ));
        }

        Visitor visitor = optVisitor.get();
        VisitorService.VisitorProfile profile = visitorService.loadProfile(code);

        // 1. Biometric Face Comparison (Gate Captured Photo vs. Registration Photo)
        FaceVerificationService.FaceMatchResult faceMatch = faceVerificationService.comparePhotos(
                visitor.getPhoto(),
                req.currentPhoto()
        );

        // 2. Past 7-Day Farm Sickness & Disease Exposure Screening
        VisitorVerificationService.PastWeekExposureResult pastWeek = verificationService.checkPastWeekExposure(visitor.getId());

        // 3. Overall Multi-point Biosecurity Verification
        VisitorVerificationService.VerificationResult verification = verificationService.verify(profile);

        // 4. Duplicate Active Visit Collision Check
        var activeVisitOpt = farmVisitRepository.findTopByVisitorIdOrderByCheckInTimeDesc(visitor.getId());
        boolean alreadyActiveVisit = activeVisitOpt.map(v -> v.getCheckOutTime() == null).orElse(false);
        String activeVisitFarmName = alreadyActiveVisit ? activeVisitOpt.get().getFarm().getName() : null;

        // 5. Final Authoritative Clearance
        boolean canAdmit = faceMatch.matched()
                && pastWeek.safe()
                && "ALLOWED".equalsIgnoreCase(verification.decision())
                && !alreadyActiveVisit;

        String summaryMessage = canAdmit
                ? "Visitor passed all identity, facial biometric, and 7-day disease checks."
                : "Visitor admission cannot be granted due to biosecurity or identity restrictions.";

        return ResponseEntity.ok(new SmartGateVerifyResponse(
                true,
                visitor.getVisitorCode(),
                visitor.getFullName(),
                visitor.getCompanyName(),
                visitor.getVisitorType(),
                visitor.getMobile(),
                visitor.getPhoto(),
                req.currentPhoto(),
                faceMatch,
                pastWeek,
                verification,
                alreadyActiveVisit,
                activeVisitFarmName,
                canAdmit,
                summaryMessage
        ));
    }

    @PostMapping("/smart-gate/admit")
    @ResponseBody
    public ResponseEntity<?> admitSmartGate(@RequestBody SmartGateAdmitRequest req) {

        if (req == null || req.visitorCode() == null || req.farmId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing required admission parameters."));
        }

        try {
            Farm farm = farmService.findById(req.farmId());
            String purpose = (req.purpose() != null && !req.purpose().isBlank()) ? req.purpose() : "ENTRY";

            // Authoritative server check-in
            FarmVisit visit = checkInService.checkIn(
                    req.visitorCode().trim().toUpperCase(),
                    farm,
                    purpose,
                    req.vehicleNumber()
            );

            // If visitor has no registration photo on file yet, save this verified gate photo as their reference
            Visitor visitor = visit.getVisitor();
            if ((visitor.getPhoto() == null || visitor.getPhoto().isBlank()) && req.currentPhoto() != null && !req.currentPhoto().isBlank()) {
                visitor.setPhoto(req.currentPhoto());
                visitorRepository.save(visitor);
            }

            // Synchronous Notifications (Blocking before response treated as successful)
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
            String checkInStr = visit.getCheckInTime().format(dtf);
            String validUntilStr = visit.getPassValidUntil() != null ? visit.getPassValidUntil().format(dtf) : "N/A";

            String notifBody = String.format("""
                    LiveShield Smart Gate Admission Alert

                    Visitor: %s
                    Visitor ID: %s
                    Company: %s
                    Destination Farm: %s
                    Purpose: %s
                    Vehicle: %s
                    Pass Code: %s
                    Check-in Time: %s
                    Pass Valid Until: %s

                    Biosecurity Clearance: PASSED (Face Verified, QR Verified, 0 Sick Farm Contacts in 7 Days)
                    """,
                    visitor.getFullName(),
                    visitor.getVisitorCode(),
                    visitor.getCompanyName() != null ? visitor.getCompanyName() : "N/A",
                    farm.getName(),
                    visit.getPurpose(),
                    visit.getVehicleNumber() != null ? visit.getVehicleNumber() : "None",
                    visit.getPassCode(),
                    checkInStr,
                    validUntilStr
            );

            // 1. Synchronous Gmail API Alert
            boolean emailSent = false;
            if (this.adminEmails != null && this.adminEmails.length > 0) {
                for (String email : this.adminEmails) {
                    if (email != null && !email.isBlank()) {
                        try {
                            emailNotificationService.sendAlertEmail(
                                    email.trim(),
                                    "LiveShield Gate Admission: " + visitor.getFullName() + " @ " + farm.getName(),
                                    notifBody
                            );
                            emailSent = true;
                            System.out.println(">>> Smart Gate admission email dispatched to: " + email);
                        } catch (Exception ex) {
                            System.err.println(">>> Smart Gate admission email error for " + email + ": " + ex.getMessage());
                        }
                    }
                }
            }

            // 2. Synchronous Twilio WhatsApp Alert
            boolean whatsappSent = false;
            try {
                whatsAppNotificationService.sendAlertWhatsApp(notifBody);
                whatsappSent = true;
                System.out.println(">>> Smart Gate admission WhatsApp dispatched to administrators.");
            } catch (Exception ex) {
                System.err.println(">>> Smart Gate admission WhatsApp error: " + ex.getMessage());
            }

            return ResponseEntity.ok(new SmartGateAdmitResponse(
                    true,
                    visit.getId(),
                    visit.getPassCode(),
                    visitor.getFullName(),
                    visitor.getVisitorCode(),
                    farm.getName(),
                    checkInStr,
                    validUntilStr,
                    emailSent,
                    whatsappSent,
                    "Visitor admitted successfully."
            ));

        } catch (Exception ex) {
            System.err.println(">>> Smart Gate admission exception: " + ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/smart-gate/checkout-verify")
    @ResponseBody
    public ResponseEntity<?> smartGateCheckoutVerify(@RequestBody SmartGateCheckoutVerifyRequest request) {
        if (request.visitorCode() == null || request.visitorCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("found", false, "message", "Visitor QR code is required."));
        }

        Optional<Visitor> visitorOpt = visitorRepository.findByVisitorCode(request.visitorCode().trim());
        if (visitorOpt.isEmpty()) {
            return ResponseEntity.ok(new SmartGateCheckoutVerifyResponse(
                    false,
                    request.visitorCode(),
                    null, null, null, null, null, null,
                    new FaceVerificationService.FaceMatchResult(false, 0, "Visitor not registered in system."),
                    false, null, null, null, null, null,
                    false,
                    "No visitor record found for QR code: " + request.visitorCode()
            ));
        }

        Visitor visitor = visitorOpt.get();

        // 1. Check for active visit
        Optional<FarmVisit> activeVisitOpt = farmVisitRepository.findTopByVisitorIdOrderByCheckInTimeDesc(visitor.getId())
                .filter(v -> v.getCheckOutTime() == null);

        if (activeVisitOpt.isEmpty()) {
            return ResponseEntity.ok(new SmartGateCheckoutVerifyResponse(
                    true,
                    visitor.getVisitorCode(),
                    visitor.getFullName(),
                    visitor.getCompanyName(),
                    visitor.getVisitorType(),
                    visitor.getMobile(),
                    visitor.getPhoto(),
                    request.currentPhoto(),
                    new FaceVerificationService.FaceMatchResult(false, 0, "No active check-in found."),
                    false,
                    null, null, null, null, null,
                    false,
                    "Visitor does not currently have an active on-site visit to check out from."
            ));
        }

        FarmVisit activeVisit = activeVisitOpt.get();

        // 2. Perform biometric match
        FaceVerificationService.FaceMatchResult faceMatch = faceVerificationService.comparePhotos(
                visitor.getPhoto(),
                request.currentPhoto()
        );

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
        String checkInStr = activeVisit.getCheckInTime() != null ? activeVisit.getCheckInTime().format(dtf) : "—";
        String elapsedStr = "—";
        if (activeVisit.getCheckInTime() != null) {
            java.time.Duration dur = java.time.Duration.between(activeVisit.getCheckInTime(), java.time.LocalDateTime.now());
            long hours = dur.toHours();
            long minutes = dur.toMinutesPart();
            elapsedStr = (hours > 0 ? hours + "h " : "") + minutes + "m";
        }

        boolean canCheckOut = faceMatch.matched();
        String message = canCheckOut
                ? "Identity and active visit verified. Ready for gate checkout."
                : "Gate check-out halted: Facial photo does not match registered visitor photo.";

        return ResponseEntity.ok(new SmartGateCheckoutVerifyResponse(
                true,
                visitor.getVisitorCode(),
                visitor.getFullName(),
                visitor.getCompanyName(),
                visitor.getVisitorType(),
                visitor.getMobile(),
                visitor.getPhoto(),
                request.currentPhoto(),
                faceMatch,
                true,
                activeVisit.getId(),
                activeVisit.getFarm().getName(),
                activeVisit.getPassCode(),
                checkInStr,
                elapsedStr,
                canCheckOut,
                message
        ));
    }

    @PostMapping("/smart-gate/checkout")
    @ResponseBody
    public ResponseEntity<?> smartGateCheckout(@RequestBody SmartGateCheckoutRequest request) {
        try {
            Long visitId = request.visitId();
            if (visitId == null && request.visitorCode() != null && !request.visitorCode().isBlank()) {
                Optional<Visitor> visOpt = visitorRepository.findByVisitorCode(request.visitorCode().trim());
                if (visOpt.isPresent()) {
                    Optional<FarmVisit> activeOpt = farmVisitRepository.findTopByVisitorIdOrderByCheckInTimeDesc(visOpt.get().getId())
                            .filter(v -> v.getCheckOutTime() == null);
                    if (activeOpt.isPresent()) {
                        visitId = activeOpt.get().getId();
                    }
                }
            }

            if (visitId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No active visit found to check out."));
            }

            FarmVisit visit = checkInService.checkOut(visitId);
            Visitor visitor = visit.getVisitor();
            Farm farm = visit.getFarm();

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
            String checkInStr = visit.getCheckInTime() != null ? visit.getCheckInTime().format(dtf) : "—";
            String checkOutStr = visit.getCheckOutTime() != null ? visit.getCheckOutTime().format(dtf) : "—";

            String durationStr = "—";
            if (visit.getCheckInTime() != null && visit.getCheckOutTime() != null) {
                java.time.Duration dur = java.time.Duration.between(visit.getCheckInTime(), visit.getCheckOutTime());
                long hours = dur.toHours();
                long minutes = dur.toMinutesPart();
                durationStr = (hours > 0 ? hours + "h " : "") + minutes + "m";
            }

            // Exit Alert Notification
            String notifBody = String.format(
                    "LiveShield Smart Gate Exit Alert:\n" +
                    "Visitor: %s (%s)\n" +
                    "Farm: %s\n" +
                    "Check-In: %s\n" +
                    "Check-Out: %s\n" +
                    "Total Duration: %s\n" +
                    "Pass Code: %s\n" +
                    "Status: Gate Exit Clearance Granted.",
                    visitor.getFullName(),
                    visitor.getVisitorCode(),
                    farm.getName(),
                    checkInStr,
                    checkOutStr,
                    durationStr,
                    visit.getPassCode()
            );

            // Synchronous Email
            boolean emailSent = false;
            if (adminEmails != null && adminEmails.length > 0) {
                for (String email : adminEmails) {
                    if (email != null && !email.isBlank()) {
                        try {
                            emailNotificationService.sendAlertEmail(
                                    email.trim(),
                                    "LiveShield Gate Exit: " + visitor.getFullName() + " - " + farm.getName(),
                                    notifBody
                            );
                            emailSent = true;
                        } catch (Exception ex) {
                            System.err.println(">>> Smart Gate exit email error for " + email + ": " + ex.getMessage());
                        }
                    }
                }
            }

            // Synchronous WhatsApp
            boolean whatsappSent = false;
            try {
                whatsAppNotificationService.sendAlertWhatsApp(notifBody);
                whatsappSent = true;
            } catch (Exception ex) {
                System.err.println(">>> Smart Gate exit WhatsApp error: " + ex.getMessage());
            }

            return ResponseEntity.ok(new SmartGateCheckoutResponse(
                    true,
                    visit.getId(),
                    visit.getPassCode(),
                    visitor.getFullName(),
                    visitor.getVisitorCode(),
                    farm.getName(),
                    checkInStr,
                    checkOutStr,
                    durationStr,
                    emailSent,
                    whatsappSent,
                    "Visitor checked out successfully. Gate exit clearance granted."
            ));

        } catch (Exception ex) {
            System.err.println(">>> Smart Gate checkout exception: " + ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    // DTO records for Smart Gate
    public record SmartGateVerifyRequest(
            String visitorCode,
            String currentPhoto,
            Long farmId) {
    }

    public record SmartGateVerifyResponse(
            boolean found,
            String visitorCode,
            String fullName,
            String company,
            String visitorType,
            String mobile,
            String registeredPhoto,
            String currentPhoto,
            FaceVerificationService.FaceMatchResult faceMatch,
            VisitorVerificationService.PastWeekExposureResult pastWeekExposure,
            VisitorVerificationService.VerificationResult verificationResult,
            boolean alreadyActiveVisit,
            String activeVisitFarmName,
            boolean canAdmit,
            String message) {
    }

    public record SmartGateAdmitRequest(
            String visitorCode,
            Long farmId,
            String purpose,
            String vehicleNumber,
            String currentPhoto) {
    }

    public record SmartGateAdmitResponse(
            boolean success,
            Long visitId,
            String passCode,
            String visitorName,
            String visitorCode,
            String farmName,
            String checkInTime,
            String passValidUntil,
            boolean emailSent,
            boolean whatsappSent,
            String message) {
    }

    public record SmartGateCheckoutVerifyRequest(
            String visitorCode,
            String currentPhoto,
            Long farmId) {
    }

    public record SmartGateCheckoutVerifyResponse(
            boolean found,
            String visitorCode,
            String fullName,
            String company,
            String visitorType,
            String mobile,
            String registeredPhoto,
            String currentPhoto,
            FaceVerificationService.FaceMatchResult faceMatch,
            boolean hasActiveVisit,
            Long visitId,
            String farmName,
            String passCode,
            String checkInTime,
            String elapsedTime,
            boolean canCheckOut,
            String message) {
    }

    public record SmartGateCheckoutRequest(
            String visitorCode,
            Long visitId,
            String currentPhoto) {
    }

    public record SmartGateCheckoutResponse(
            boolean success,
            Long visitId,
            String passCode,
            String visitorName,
            String visitorCode,
            String farmName,
            String checkInTime,
            String checkOutTime,
            String duration,
            boolean emailSent,
            boolean whatsappSent,
            String message) {
    }
}