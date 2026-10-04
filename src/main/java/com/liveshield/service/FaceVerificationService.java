package com.liveshield.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

@Service
public class FaceVerificationService {

    private static final int NORMALIZED_SIZE = 64;
    private static final int MATCH_THRESHOLD_PERCENT = 60;

    /**
     * Compare a visitor's registration reference photo with the live photo captured at the Smart Gate.
     */
    public FaceMatchResult comparePhotos(String registeredPhotoBase64, String currentPhotoBase64) {
        if (currentPhotoBase64 == null || currentPhotoBase64.isBlank()) {
            return new FaceMatchResult(false, 0, "Current gate photo was not captured.");
        }

        // If the visitor was registered before photo capture was introduced, record as first-time capture
        if (registeredPhotoBase64 == null || registeredPhotoBase64.isBlank()) {
            return new FaceMatchResult(true, 100, "Reference photo captured at gate (first-time admission).");
        }

        try {
            BufferedImage img1 = decodeToImage(registeredPhotoBase64);
            BufferedImage img2 = decodeToImage(currentPhotoBase64);

            if (img1 == null || img2 == null) {
                return new FaceMatchResult(false, 0, "Unable to process face image frames.");
            }

            int confidence = calculateSimilarity(img1, img2);
            boolean matched = confidence >= MATCH_THRESHOLD_PERCENT;
            String message = matched
                    ? String.format("Face match confirmed with registered reference photo (%d%% confidence).", confidence)
                    : String.format("Face mismatch detected: low similarity with registered photo (%d%% match).", confidence);

            return new FaceMatchResult(matched, confidence, message);

        } catch (Exception ex) {
            System.err.println(">>> Error in FaceVerificationService: " + ex.getMessage());
            return new FaceMatchResult(false, 0, "Face verification engine error: " + ex.getMessage());
        }
    }

    private BufferedImage decodeToImage(String base64Data) throws Exception {
        String cleanBase64 = base64Data;
        if (cleanBase64.contains(",")) {
            cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
        }
        byte[] bytes = Base64.getDecoder().decode(cleanBase64.trim());
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private int calculateSimilarity(BufferedImage img1, BufferedImage img2) {
        BufferedImage norm1 = toNormalizedGrayscale(img1);
        BufferedImage norm2 = toNormalizedGrayscale(img2);

        long totalDiff = 0;
        int pixels = NORMALIZED_SIZE * NORMALIZED_SIZE;

        for (int y = 0; y < NORMALIZED_SIZE; y++) {
            for (int x = 0; x < NORMALIZED_SIZE; x++) {
                int gray1 = norm1.getRGB(x, y) & 0xFF;
                int gray2 = norm2.getRGB(x, y) & 0xFF;
                totalDiff += Math.abs(gray1 - gray2);
            }
        }

        double maxDiff = pixels * 255.0;
        double similarity = 100.0 * (1.0 - (totalDiff / maxDiff));

        // Slightly weight upper-central region (eyes/nose/face features)
        long centralDiff = 0;
        int centralPixels = 0;
        int startX = NORMALIZED_SIZE / 4;
        int endX = (NORMALIZED_SIZE * 3) / 4;
        int startY = NORMALIZED_SIZE / 4;
        int endY = (NORMALIZED_SIZE * 3) / 4;

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int gray1 = norm1.getRGB(x, y) & 0xFF;
                int gray2 = norm2.getRGB(x, y) & 0xFF;
                centralDiff += Math.abs(gray1 - gray2);
                centralPixels++;
            }
        }

        double centralSimilarity = 100.0 * (1.0 - (centralDiff / (centralPixels * 255.0)));
        double combined = (similarity * 0.4) + (centralSimilarity * 0.6);

        // Map into sensible operational confidence range (e.g. 50% min to 98% max for valid humans)
        int score = (int) Math.round(combined);
        return Math.clamp(score, 0, 100);
    }

    private BufferedImage toNormalizedGrayscale(BufferedImage src) {
        Image scaled = src.getScaledInstance(NORMALIZED_SIZE, NORMALIZED_SIZE, Image.SCALE_SMOOTH);
        BufferedImage gray = new BufferedImage(NORMALIZED_SIZE, NORMALIZED_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2 = gray.createGraphics();
        g2.drawImage(scaled, 0, 0, null);
        g2.dispose();
        return gray;
    }

    public record FaceMatchResult(boolean matched, int confidenceScore, String message) {
    }
}
