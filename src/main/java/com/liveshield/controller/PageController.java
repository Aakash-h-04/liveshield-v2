package com.liveshield.controller;

import com.liveshield.entity.Farm;
import com.liveshield.service.AlertService;
import com.liveshield.service.FarmService;
import com.liveshield.service.LivestockBatchService;
import com.liveshield.service.VeterinaryRecordService;
import com.liveshield.entity.VeterinaryRecord;
import com.liveshield.service.VisitorService;

import com.liveshield.service.NotificationLogService;
import com.liveshield.repository.FarmVisitRepository;
import com.liveshield.repository.VisitorLicenceRepository;
import com.liveshield.repository.VisitorRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class PageController {

    private final FarmService farmService;
    private final AlertService alertService;
    private final LivestockBatchService batchService;
    private final VeterinaryRecordService veterinaryService;
    private final VisitorService visitorService;
    private final VisitorRepository visitorRepository;
    private final FarmVisitRepository farmVisitRepository;
    private final VisitorLicenceRepository licenceRepository;
    private final NotificationLogService notificationLogService;

    public PageController(
            FarmService farmService,
            AlertService alertService,
            LivestockBatchService batchService,
            VeterinaryRecordService veterinaryService,
            VisitorService visitorService,
            VisitorRepository visitorRepository,
            FarmVisitRepository farmVisitRepository,
            VisitorLicenceRepository licenceRepository,
            NotificationLogService notificationLogService) {

        this.farmService = farmService;
        this.alertService = alertService;
        this.batchService = batchService;
        this.veterinaryService = veterinaryService;
        this.visitorService = visitorService;
        this.visitorRepository = visitorRepository;
        this.farmVisitRepository = farmVisitRepository;
        this.licenceRepository = licenceRepository;
        this.notificationLogService = notificationLogService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {

        var farms = farmService.findAll();

        /*
         * Farm metrics
         */
        model.addAttribute("farms", farms);

        model.addAttribute(
                "farmCount",
                farms.size());

        model.addAttribute(
                "livestockTotal",
                farms.stream()
                        .mapToInt(f -> f.getLivestockCount() == null
                                ? 0
                                : f.getLivestockCount())
                        .sum());

        model.addAttribute(
                "averageCompliance",
                farms.isEmpty()
                        ? 0
                        : Math.round(
                                farms.stream()
                                        .mapToInt(f -> f.getCompliancePercent() == null
                                                ? 0
                                                : f.getCompliancePercent())
                                        .average()
                                        .orElse(0)));

        long highRiskCount = farms.stream()
                .filter(f -> "HIGH".equalsIgnoreCase(f.getRiskLevel()))
                .count();
        long mediumRiskCount = farms.stream()
                .filter(f -> "MEDIUM".equalsIgnoreCase(f.getRiskLevel()))
                .count();
        long lowRiskCount = farms.stream()
                .filter(f -> f.getRiskLevel() == null || "LOW".equalsIgnoreCase(f.getRiskLevel()))
                .count();

        model.addAttribute("highRiskCount", highRiskCount);
        model.addAttribute("mediumRiskCount", mediumRiskCount);
        model.addAttribute("lowRiskCount", lowRiskCount);

        List<String> farmNames = farms.stream().map(Farm::getName).toList();
        List<Integer> farmCompliance = farms.stream().map(f -> f.getCompliancePercent() != null ? f.getCompliancePercent() : 0).toList();
        List<Integer> farmLivestock = farms.stream().map(f -> f.getLivestockCount() != null ? f.getLivestockCount() : 0).toList();
        List<String> farmRisks = farms.stream().map(f -> f.getRiskLevel() != null ? f.getRiskLevel() : "LOW").toList();

        model.addAttribute("farmNames", farmNames);
        model.addAttribute("farmCompliance", farmCompliance);
        model.addAttribute("farmLivestock", farmLivestock);
        model.addAttribute("farmRisks", farmRisks);

        /*
         * Alert information
         */

        var activeAlerts = alertService.findActive();

        model.addAttribute(
                "activeAlerts",
                activeAlerts);

        model.addAttribute(
                "activeAlertCount",
                activeAlerts.size());

        /*
         * Recent alerts
         */

        var recentAlerts = alertService.findAll()
                .stream()
                .limit(5)
                .toList();

        model.addAttribute(
                "recentAlerts",
                recentAlerts);

        /*
         * Livestock batch information
         */

        var allBatches = batchService.findAll();
        long sickBatchCount = allBatches.stream().filter(b -> "SICK".equalsIgnoreCase(b.getHealthStatus())).count();
        long isolatedBatchCount = allBatches.stream().filter(b -> "ISOLATED".equalsIgnoreCase(b.getHealthStatus()) || "QUARANTINE".equalsIgnoreCase(b.getHealthStatus())).count();
        long healthyBatchCount = allBatches.stream().filter(b -> !"SICK".equalsIgnoreCase(b.getHealthStatus()) && !"ISOLATED".equalsIgnoreCase(b.getHealthStatus()) && !"QUARANTINE".equalsIgnoreCase(b.getHealthStatus())).count();

        model.addAttribute("healthyBatchCount", healthyBatchCount);
        model.addAttribute("sickBatchCount", sickBatchCount);
        model.addAttribute("isolatedBatchCount", isolatedBatchCount);
        model.addAttribute("totalBatchCount", allBatches.size());

        model.addAttribute("totalVisitorsCount", visitorRepository.count());
        model.addAttribute("totalVisitsCount", farmVisitRepository.count());
        model.addAttribute("recentNotifications", notificationLogService.getRecentNotifications(8));

        /*
         * Recent veterinary records
         */

        List<VeterinaryRecord> recentVeterinaryRecords = new ArrayList<>();

        for (Farm farm : farms) {

            recentVeterinaryRecords.addAll(
                    veterinaryService.findByFarm(
                            farm.getId()));
        }

        recentVeterinaryRecords.sort(
                Comparator.comparing(
                        VeterinaryRecord::getRecordDate,
                        Comparator.nullsLast(
                                Comparator.reverseOrder())));

        model.addAttribute(
                "recentVeterinaryRecords",
                recentVeterinaryRecords
                        .stream()
                        .limit(5)
                        .toList());

        model.addAttribute(
                "activeVisitorCount",
                visitorService.getActiveVisitorCount());

        model.addAttribute(
                "todayCheckInCount",
                visitorService.getTodayCheckInCount());

        model.addAttribute(
                "todayCheckOutCount",
                visitorService.getTodayCheckOutCount());

        return "dashboard";
    }

    @GetMapping("/farms/new")
    public String newFarm(Model model) {

        model.addAttribute(
                "farm",
                new Farm());

        return "farm-form";
    }

    @PostMapping("/farms")
    public String createFarm(
            @Valid @ModelAttribute("farm") Farm farm,
            BindingResult result) {

        if (result.hasErrors()) {
            return "farm-form";
        }
        farmService.save(farm);

        return "redirect:/";
    }

    @GetMapping("/database")
    public String databaseManagement(
            @RequestParam(required = false, defaultValue = "visitors") String tab,
            Model model) {

        var farms = farmService.findAll();
        var visitors = visitorRepository.findAll();
        var batches = batchService.findAll();
        long totalVisits = farmVisitRepository.count();

        var licences = licenceRepository.findAll();
        java.util.Map<Long, com.liveshield.entity.VisitorLicence> licenceMap = new java.util.HashMap<>();
        for (var lic : licences) {
            if (lic.getVisitor() != null) {
                licenceMap.put(lic.getVisitor().getId(), lic);
            }
        }

        model.addAttribute("farms", farms);
        model.addAttribute("visitors", visitors);
        model.addAttribute("batches", batches);
        model.addAttribute("licenceMap", licenceMap);
        model.addAttribute("totalVisits", totalVisits);
        model.addAttribute("activeTab", tab);

        return "database";
    }

    @GetMapping("/farms/{id}/edit")
    public String editFarm(@PathVariable Long id, Model model) {
        Farm farm = farmService.findById(id);
        model.addAttribute("farm", farm);
        model.addAttribute("isEdit", true);
        return "farm-form";
    }

    @PostMapping("/farms/{id}")
    public String updateFarm(
            @PathVariable Long id,
            @Valid @ModelAttribute("farm") Farm farm,
            BindingResult result,
            Model model) {

        if (result.hasErrors()) {
            model.addAttribute("isEdit", true);
            return "farm-form";
        }

        farm.setId(id);
        farmService.save(farm);

        return "redirect:/database?tab=farms";
    }
}