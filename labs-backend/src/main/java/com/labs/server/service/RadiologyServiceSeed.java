package com.labs.server.service;

import com.labs.server.entity.LabService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * V14 — radiology catalog seed. Mirrors {@link LabServiceSeed} but for the
 * imaging/cardio disciplines. Curated 20 modalities across XR, USG, CT, MRI,
 * MAMMO, DEXA, CARDIO, FLUORO.
 *
 * Radiology rows are narrative (valueType=TEXT), have no specimen / container /
 * additive, and don't get reference ranges. Prices are synthesized medians
 * (not inherited from HMS hospital_services — that data has 4x spreads on
 * common modalities and isn't trustworthy as a default).
 *
 * LOINC codes are HMS-coordinated; flagged for SME review before fleet roll-out.
 */
@Component
public class RadiologyServiceSeed {

    public List<LabService> defaults(UUID hospitalId) {
        return List.of(
            // ───── XR (~30min TAT, ₹500–₹800) ─────
            radiology(hospitalId, "RAD-XR-CHEST",    "X-Ray Chest PA",                 "XR",     "Digital Radiography", 30,  500, "36643-5", 10),
            radiology(hospitalId, "RAD-XR-ABDOMEN",  "X-Ray Abdomen Erect",            "XR",     "Digital Radiography", 30,  600, "44115-4", 20),
            radiology(hospitalId, "RAD-XR-KUB",      "X-Ray KUB",                      "XR",     "Digital Radiography", 30,  600, "43764-0", 30),
            radiology(hospitalId, "RAD-XR-SPINE-LS", "X-Ray Lumbosacral Spine AP/Lat", "XR",     "Digital Radiography", 30,  800, "36293-9", 40),

            // ───── USG (~45min TAT, ₹1500–₹1800) ─────
            radiology(hospitalId, "RAD-USG-ABD",     "USG Abdomen",                    "USG",    "Ultrasound B-mode",   45, 1500, "30663-0", 50),
            radiology(hospitalId, "RAD-USG-PELVIS",  "USG Pelvis",                     "USG",    "Ultrasound B-mode",   45, 1500, "44115-4", 60),
            radiology(hospitalId, "RAD-USG-OBS",     "USG Obstetric",                  "USG",    "Ultrasound B-mode",   45, 1800, "11525-3", 70),
            radiology(hospitalId, "RAD-USG-KUB",     "USG KUB",                        "USG",    "Ultrasound B-mode",   45, 1500, "49086-2", 80),

            // ───── CT (~60–75min TAT, ₹3500–₹7000) ─────
            radiology(hospitalId, "RAD-CT-BRAIN",    "CT Brain Plain",                 "CT",     "Multi-slice CT",      60, 3500, "24725-4", 90),
            radiology(hospitalId, "RAD-CT-CHEST",    "CT Chest Plain",                 "CT",     "Multi-slice CT",      60, 5000, "24627-2", 100),
            radiology(hospitalId, "RAD-CT-ABD",      "CT Abdomen + Pelvis Contrast",   "CT",     "Multi-slice CT",      75, 7000, "30794-3", 110),

            // ───── MRI (~90min TAT, ₹6500–₹7000) ─────
            radiology(hospitalId, "RAD-MRI-BRAIN",   "MRI Brain Plain",                "MRI",    "1.5T MRI",            90, 6500, "30659-8", 120),
            radiology(hospitalId, "RAD-MRI-LS",      "MRI Lumbosacral Spine",          "MRI",    "1.5T MRI",            90, 7000, "36100-6", 130),
            radiology(hospitalId, "RAD-MRI-KNEE",    "MRI Knee Joint",                 "MRI",    "1.5T MRI",            90, 7000, "30607-7", 140),

            // ───── Specialised ─────
            radiology(hospitalId, "RAD-MAMMO",       "Mammography Bilateral",          "MAMMO",  "Digital Mammography", 45, 2500, "26346-7", 150),
            radiology(hospitalId, "RAD-DEXA",        "DEXA Bone Densitometry",         "DEXA",   "DXA",                 45, 2000, "38260-1", 160),

            // ───── Cardio / Doppler / Fluoro ─────
            radiology(hospitalId, "RAD-ECG",         "ECG 12-Lead",                    "CARDIO", "12-Lead ECG",         15,  200, "11524-6", 170),
            radiology(hospitalId, "RAD-ECHO-2D",     "2D Echocardiogram",              "CARDIO", "2D Echo + Doppler",   30, 1500, "34552-0", 180),
            radiology(hospitalId, "RAD-DOPPLER-CAR", "Carotid Doppler",                "USG",    "Color Doppler",       45, 2000, "39823-5", 190),
            radiology(hospitalId, "RAD-FLUORO-BAR",  "Barium Swallow",                 "FLUORO", "Fluoroscopy",         60, 1800, "37685-0", 200)
        );
    }

    private LabService radiology(UUID hospitalId, String code, String name, String category,
                                 String method, int tatMinutes, int price, String loinc, int displayOrder) {
        return LabService.builder()
                .hospitalId(hospitalId)
                .testCode(code)
                .loincCode(loinc)
                .name(name)
                .category(category)
                .discipline("RADIOLOGY")
                .defaultMethod(method)
                .tatMinutes(tatMinutes)
                .valueType("TEXT")
                .isPanel(false)
                .requiresAuthorisation(false)
                .fastingRequired(false)
                .price(new BigDecimal(price))
                .gstRate(new BigDecimal("5.00"))
                .displayOrder(displayOrder)
                .active(true)
                .build();
    }
}
