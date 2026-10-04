package com.liveshield.service;

import com.liveshield.entity.Farm;
import com.liveshield.entity.FarmVisit;
import com.liveshield.entity.LivestockBatch;
import com.liveshield.entity.VeterinaryRecord;
import com.liveshield.entity.Visitor;
import com.liveshield.entity.VisitorBiosecurityProfile;
import com.liveshield.entity.VisitorLicence;
import com.liveshield.repository.FarmVisitRepository;
import com.liveshield.repository.LivestockBatchRepository;
import com.liveshield.repository.VeterinaryRecordRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VisitorVerificationService {

    /*
     * Evaluate whether a visitor can proceed
     * to farm check-in.
     */

    private final FarmVisitRepository farmVisitRepository;
    private final LivestockBatchRepository livestockBatchRepository;
    private final VeterinaryRecordRepository veterinaryRecordRepository;

    public VisitorVerificationService(
            FarmVisitRepository farmVisitRepository,
            LivestockBatchRepository livestockBatchRepository,
            VeterinaryRecordRepository veterinaryRecordRepository) {
        this.farmVisitRepository = farmVisitRepository;
        this.livestockBatchRepository = livestockBatchRepository;
        this.veterinaryRecordRepository = veterinaryRecordRepository;
    }

    /**
     * Check all farms visited by this visitor within the last 7 days (1 week).
     * If any farm visited reports sick livestock or recent disease, visitor is flagged as not safe.
     */
    public PastWeekExposureResult checkPastWeekExposure(Long visitorId) {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
        List<FarmVisit> recentVisits = farmVisitRepository.findByVisitorIdOrderByCheckInTimeDesc(visitorId)
                .stream()
                .filter(v -> v.getCheckInTime() != null && v.getCheckInTime().isAfter(oneWeekAgo))
                .toList();

        List<VisitedFarmExposure> exposures = new ArrayList<>();
        boolean anySick = false;

        // Group by farm to avoid duplicate evaluations
        Map<Long, List<FarmVisit>> visitsByFarm = recentVisits.stream()
                .collect(Collectors.groupingBy(v -> v.getFarm().getId()));

        for (Map.Entry<Long, List<FarmVisit>> entry : visitsByFarm.entrySet()) {
            Long farmId = entry.getKey();
            List<FarmVisit> fVisits = entry.getValue();
            Farm farm = fVisits.get(0).getFarm();

            // Check livestock batches for sick or isolated status
            List<LivestockBatch> batches = livestockBatchRepository.findByFarmIdOrderByArrivalDateDesc(farmId);
            long sickBatches = batches.stream()
                    .filter(b -> "SICK".equalsIgnoreCase(b.getHealthStatus()) || "ISOLATED".equalsIgnoreCase(b.getHealthStatus()))
                    .count();

            // Check veterinary records for disease in the past 7 days
            List<VeterinaryRecord> vetRecords = veterinaryRecordRepository.findByFarmIdOrderByRecordDateDesc(farmId);
            long diseaseRecords = vetRecords.stream()
                    .filter(r -> "DISEASE".equalsIgnoreCase(r.getRecordType()) &&
                            r.getRecordDate() != null &&
                            r.getRecordDate().isAfter(LocalDate.now().minusDays(7)))
                    .count();

            boolean farmHasSickAnimals = (sickBatches > 0) || (diseaseRecords > 0);
            if (farmHasSickAnimals) {
                anySick = true;
            }

            LocalDateTime latestVisit = fVisits.stream()
                    .map(FarmVisit::getCheckInTime)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            exposures.add(new VisitedFarmExposure(
                    farm.getId(),
                    farm.getName(),
                    farm.getFarmType(),
                    farm.getLocation(),
                    latestVisit,
                    farmHasSickAnimals,
                    (int) sickBatches,
                    (int) diseaseRecords
            ));
        }

        boolean safe = !anySick;
        String message;
        if (recentVisits.isEmpty()) {
            message = "No farm visits recorded in the past 7 days (Safe).";
        } else if (safe) {
            message = String.format("All %d farms visited in the past 7 days report healthy livestock.", exposures.size());
        } else {
            message = "Critical biosecurity risk: 1 or more farms visited in the past 7 days currently report sick livestock/disease.";
        }

        return new PastWeekExposureResult(safe, exposures, message);
    }

    public VerificationResult verify(
            VisitorService.VisitorProfile profile) {

        Visitor visitor = profile.visitor();
        VisitorLicence licence = profile.licence();
        VisitorBiosecurityProfile biosecurity = profile.biosecurityProfile();

        List<String> reasons = new ArrayList<>();

        /*
         * ------------------------------------------------
         * 1. ACCOUNT STATUS
         * ------------------------------------------------
         */

        if (!"ACTIVE".equalsIgnoreCase(
                visitor.getAccountStatus())) {

            return new VerificationResult(
                    "DENIED",
                    "Visitor account is not active.",
                    List.of(
                            "Visitor account status: "
                                    + visitor.getAccountStatus()));
        }

        /*
         * ------------------------------------------------
         * 2. LICENCE
         * ------------------------------------------------
         */

        if (licence == null) {

            return new VerificationResult(
                    "DENIED",
                    "No professional licence is registered.",
                    List.of(
                            "A valid visitor licence is required."));
        }

        if (!"VERIFIED".equalsIgnoreCase(
                licence.getVerificationStatus())) {

            return new VerificationResult(
                    "DENIED",
                    "Visitor licence has not been verified.",
                    List.of(
                            "Licence status: "
                                    + licence.getVerificationStatus()));
        }

        /*
         * ------------------------------------------------
         * 3. BIOSECURITY PROFILE
         * ------------------------------------------------
         */

        if (biosecurity == null) {

            return new VerificationResult(
                    "REVIEW_REQUIRED",
                    "Biosecurity profile is incomplete.",
                    List.of(
                            "Visitor biosecurity profile "
                                    + "requires verification."));
        }

        if (!"VERIFIED".equalsIgnoreCase(
                biosecurity.getBiosecurityStatus())) {

            return new VerificationResult(
                    "REVIEW_REQUIRED",
                    "Visitor biosecurity profile requires verification.",
                    List.of(
                            "Biosecurity status: "
                                    + biosecurity.getBiosecurityStatus()));
        }

        /*
         * ------------------------------------------------
         * 4. LICENCE EXPIRY
         * ------------------------------------------------
         */

        if (licence.getExpiryDate() != null
                && licence.getExpiryDate()
                        .isBefore(LocalDate.now())) {

            return new VerificationResult(
                    "DENIED",
                    "Visitor licence has expired.",
                    List.of(
                            "Licence expired on: "
                                    + licence.getExpiryDate()));
        }

        /*
         * ------------------------------------------------
         * 5. RECENT SICK-ANIMAL CONTACT
         * ------------------------------------------------
         */

        if (biosecurity.isRecentSickAnimalContact()) {

            reasons.add(
                    "Recent contact with a sick animal was declared.");
        }

        /*
         * ------------------------------------------------
         * 6. RECENT POULTRY CONTACT
         * ------------------------------------------------
         */

        if (biosecurity.isRecentPoultryContact()) {

            reasons.add(
                    "Recent poultry-farm contact was declared.");
        }

        /*
         * ------------------------------------------------
         * 7. RECENT LIVESTOCK CONTACT
         * ------------------------------------------------
         */

        if (biosecurity.isRecentLivestockContact()) {

            reasons.add(
                    "Recent livestock contact was declared.");
        }

        /*
         * ------------------------------------------------
         * 8. HEALTH DECLARATION
         * ------------------------------------------------
         */

        if ("DECLARED_SYMPTOMS".equalsIgnoreCase(
                biosecurity.getHealthDeclaration())) {

            reasons.add(
                    "Visitor has declared relevant symptoms.");
        }

        /*
         * ------------------------------------------------
         * 9. PAST 1-WEEK FARM SICKNESS CHECK
         * ------------------------------------------------
         */
        PastWeekExposureResult pastWeek = checkPastWeekExposure(visitor.getId());
        if (!pastWeek.safe()) {
            for (VisitedFarmExposure exp : pastWeek.exposures()) {
                if (exp.reportingSick()) {
                    reasons.add(String.format(
                            "Visited farm '%s' in the past 7 days which reports sick livestock / active disease.",
                            exp.farmName()
                    ));
                }
            }
        }

        /*
         * ------------------------------------------------
         * 10. PROFILE STATUS
         * ------------------------------------------------
         */

        if ("RESTRICTED".equalsIgnoreCase(
                biosecurity.getBiosecurityStatus())) {

            return new VerificationResult(
                    "DENIED",
                    "Visitor biosecurity profile is restricted.",
                    reasons);
        }

        /*
         * ------------------------------------------------
         * 11. FINAL DECISION
         * ------------------------------------------------
         */

        if (!reasons.isEmpty()) {

            return new VerificationResult(
                    "REVIEW_REQUIRED",
                    "Additional biosecurity review is required.",
                    reasons);
        }

        /*
         * Everything required for basic verification
         * is currently satisfied.
         */

        return new VerificationResult(
                "ALLOWED",
                "Visitor passed all biosecurity and health exposure checks.",
                List.of());
    }

    public record VisitedFarmExposure(
            Long farmId,
            String farmName,
            String farmType,
            String location,
            LocalDateTime latestVisitTime,
            boolean reportingSick,
            int sickBatchesCount,
            int diseaseRecordCount) {
    }

    public record PastWeekExposureResult(
            boolean safe,
            List<VisitedFarmExposure> exposures,
            String message) {
    }

    /*
     * Result returned by the verification engine.
     */
    public record VerificationResult(
            String decision,
            String message,
            List<String> reasons) {
    }
}