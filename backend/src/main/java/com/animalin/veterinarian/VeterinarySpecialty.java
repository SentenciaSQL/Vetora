package com.animalin.veterinarian;

import com.animalin.common.exception.ApiException;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stable specialty codes. Translated labels live in the client; persistence stores the code.
 */
public final class VeterinarySpecialty {

    public static final String GENERAL_MEDICINE = "GENERAL_MEDICINE";
    public static final String INTERNAL_MEDICINE = "INTERNAL_MEDICINE";
    public static final String SURGERY = "SURGERY";
    public static final String DERMATOLOGY = "DERMATOLOGY";
    public static final String CARDIOLOGY = "CARDIOLOGY";
    public static final String NEUROLOGY = "NEUROLOGY";
    public static final String ONCOLOGY = "ONCOLOGY";
    public static final String OPHTHALMOLOGY = "OPHTHALMOLOGY";
    public static final String DENTISTRY = "DENTISTRY";
    public static final String ANESTHESIOLOGY = "ANESTHESIOLOGY";
    public static final String DIAGNOSTIC_IMAGING = "DIAGNOSTIC_IMAGING";
    public static final String EMERGENCY_CRITICAL_CARE = "EMERGENCY_CRITICAL_CARE";
    public static final String FELINE_MEDICINE = "FELINE_MEDICINE";
    public static final String EXOTIC_ANIMALS = "EXOTIC_ANIMALS";
    public static final String ORTHOPEDICS_TRAUMATOLOGY = "ORTHOPEDICS_TRAUMATOLOGY";
    public static final String REPRODUCTION = "REPRODUCTION";
    public static final String PATHOLOGY = "PATHOLOGY";
    public static final String OTHER = "OTHER";

    public static final List<String> CODES = List.of(
            GENERAL_MEDICINE,
            INTERNAL_MEDICINE,
            SURGERY,
            DERMATOLOGY,
            CARDIOLOGY,
            NEUROLOGY,
            ONCOLOGY,
            OPHTHALMOLOGY,
            DENTISTRY,
            ANESTHESIOLOGY,
            DIAGNOSTIC_IMAGING,
            EMERGENCY_CRITICAL_CARE,
            FELINE_MEDICINE,
            EXOTIC_ANIMALS,
            ORTHOPEDICS_TRAUMATOLOGY,
            REPRODUCTION,
            PATHOLOGY,
            OTHER
    );

    private static final Set<String> CODE_SET = Set.copyOf(CODES);

    private VeterinarySpecialty() {
    }

    public static boolean isCode(String value) {
        return value != null && CODE_SET.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    public static String codeOrNull(String stored) {
        if (!StringUtils.hasText(stored)) {
            return null;
        }
        String normalized = stored.trim().toUpperCase(Locale.ROOT);
        return CODE_SET.contains(normalized) ? normalized : null;
    }

    public static String normalize(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public static void requireForVeterinarian(String roleCode, String specialtyCode, String specialtyOther) {
        if (!"VETERINARIAN".equals(roleCode)) {
            return;
        }
        String code = normalize(specialtyCode);
        if (!isCode(code)) {
            throw ApiException.badRequest("Seleccione una especialidad veterinaria");
        }
        if (OTHER.equals(code) && !StringUtils.hasText(specialtyOther)) {
            throw ApiException.badRequest("Indique la otra especialidad");
        }
    }

    public static String otherText(String specialtyCode, String specialtyOther) {
        if (!OTHER.equals(normalize(specialtyCode)) || !StringUtils.hasText(specialtyOther)) {
            return null;
        }
        return specialtyOther.trim();
    }
}
