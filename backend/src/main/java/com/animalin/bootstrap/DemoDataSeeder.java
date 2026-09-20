package com.animalin.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.animalin.appointment.Appointment;
import com.animalin.appointment.AppointmentRepository;
import com.animalin.branch.Branch;
import com.animalin.branch.BranchHour;
import com.animalin.branch.BranchRepository;
import com.animalin.catalog.ClinicService;
import com.animalin.catalog.ClinicServiceRepository;
import com.animalin.catalog.Medication;
import com.animalin.catalog.MedicationRepository;
import com.animalin.medical.Consultation;
import com.animalin.medical.ConsultationRepository;
import com.animalin.medical.Prescription;
import com.animalin.medical.PrescriptionItem;
import com.animalin.medical.PrescriptionRepository;
import com.animalin.medical.Treatment;
import com.animalin.medical.TreatmentItem;
import com.animalin.medical.TreatmentRepository;
import com.animalin.medical.Vaccination;
import com.animalin.medical.VaccinationRepository;
import com.animalin.owner.Owner;
import com.animalin.owner.OwnerRepository;
import com.animalin.pet.Pet;
import com.animalin.pet.PetRepository;
import com.animalin.plan.Plan;
import com.animalin.plan.PlanRepository;
import com.animalin.tenant.Subscription;
import com.animalin.tenant.SubscriptionRepository;
import com.animalin.tenant.Tenant;
import com.animalin.tenant.TenantMembership;
import com.animalin.tenant.TenantMembershipRepository;
import com.animalin.tenant.TenantRepository;
import com.animalin.tenant.TenantSettings;
import com.animalin.tenant.TenantSettingsRepository;
import com.animalin.user.Role;
import com.animalin.user.RoleRepository;
import com.animalin.user.User;
import com.animalin.user.UserRepository;
import com.animalin.veterinarian.Veterinarian;
import com.animalin.veterinarian.VeterinarianRepository;
import com.animalin.veterinarian.VeterinarianSchedule;
import com.animalin.veterinarian.VeterinarianScheduleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Component
@Profile("!test")
public class DemoDataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final TenantSettingsRepository settingsRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantMembershipRepository membershipRepository;
    private final BranchRepository branchRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final VeterinarianScheduleRepository scheduleRepository;
    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;
    private final ClinicServiceRepository serviceRepository;
    private final MedicationRepository medicationRepository;
    private final AppointmentRepository appointmentRepository;
    private final ConsultationRepository consultationRepository;
    private final VaccinationRepository vaccinationRepository;
    private final TreatmentRepository treatmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository userRepository, RoleRepository roleRepository,
            TenantRepository tenantRepository, PlanRepository planRepository,
            TenantSettingsRepository settingsRepository, SubscriptionRepository subscriptionRepository,
            TenantMembershipRepository membershipRepository, BranchRepository branchRepository,
            VeterinarianRepository veterinarianRepository, VeterinarianScheduleRepository scheduleRepository,
            OwnerRepository ownerRepository, PetRepository petRepository, ClinicServiceRepository serviceRepository,
            MedicationRepository medicationRepository, AppointmentRepository appointmentRepository,
            ConsultationRepository consultationRepository, VaccinationRepository vaccinationRepository,
            TreatmentRepository treatmentRepository, PrescriptionRepository prescriptionRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.planRepository = planRepository;
        this.settingsRepository = settingsRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.membershipRepository = membershipRepository;
        this.branchRepository = branchRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.scheduleRepository = scheduleRepository;
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
        this.serviceRepository = serviceRepository;
        this.medicationRepository = medicationRepository;
        this.appointmentRepository = appointmentRepository;
        this.consultationRepository = consultationRepository;
        this.vaccinationRepository = vaccinationRepository;
        this.treatmentRepository = treatmentRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.findByEmailIgnoreCase("leo.a@example.org").isPresent()) {
            return;
        }
        log.info("Seeding Animalin demo data");
        Role superAdmin = role("SUPER_ADMIN");
        Role tenantAdmin = role("TENANT_ADMIN");
        Role vetRole = role("VETERINARIAN");
        Role reception = role("RECEPTIONIST");
        Role ownerRole = role("PET_OWNER");

        User platform = user("leo.a@example.org", "Elena", "Vega", superAdmin, "es");
        Plan professional = planRepository.findByCode("PROFESSIONAL").orElseThrow();
        Plan basic = planRepository.findByCode("BASIC").orElseThrow();

        Tenant sanMartin = tenant("san-martin", "Clínica Veterinaria San Martín", "San Martín Vet",
                "ACTIVE", professional, "Madrid", "ES");
        Tenant huellitas = tenant("huellitas", "Centro Veterinario Huellitas", "Huellitas",
                "TRIAL", basic, "Valencia", "ES");

        User adminA = user("tina.r@example.net", "Laura", "Martín", tenantAdmin, "es");
        User vetA = user("emma.t@example.net", "María", "López", vetRole, "es");
        User recA = user("nathan.k@example.net", "Carlos", "Ruiz", reception, "es");
        User ownerA = user("juan.owner@animalin.app", "Juan", "Pérez", ownerRole, "es");
        User adminB = user("rachel.c@example.org", "Sofía", "Navarro", tenantAdmin, "es");
        User ownerB = user("walt.e@example.net", "Ana", "Gil", ownerRole, "es");
        User ownerBoth = user("xavier.y@example.org", "Diego", "Ortega", ownerRole, "es");

        membership(sanMartin, adminA, tenantAdmin);
        membership(sanMartin, vetA, vetRole);
        membership(sanMartin, recA, reception);
        membership(sanMartin, ownerA, ownerRole);
        membership(sanMartin, ownerBoth, ownerRole);
        membership(huellitas, adminB, tenantAdmin);
        membership(huellitas, ownerB, ownerRole);
        membership(huellitas, ownerBoth, ownerRole);

        Branch branchA = branch(sanMartin, "San Martín Centro", "Calle de Alcalá 120", "Madrid");
        Branch branchB = branch(huellitas, "Huellitas Colón", "Avenida de Aragón 18", "Valencia");

        Veterinarian vet = veterinarian(sanMartin, vetA, branchA, "Medicina interna");
        Owner juan = owner(sanMartin, ownerA, "Juan", "Pérez", "600111222", "juan.owner@animalin.app");
        Owner ana = owner(huellitas, ownerB, "Ana", "Gil", "600555666", "walt.e@example.net");
        Owner diegoA = owner(sanMartin, ownerBoth, "Diego", "Sanchez", "600333478", "xavier.z@example.org");
        Owner diegoB = owner(huellitas, ownerBoth, "Diego", "Ortega", "600333444", "xavier.y@example.org");

        Pet luna = pet(sanMartin, juan, vet, branchA, "Luna", "DOG", "Golden Retriever", "FEMALE",
                LocalDate.now().minusYears(4), new BigDecimal("28.40"), "Penicilina", "Diabetes");
        Pet michi = pet(sanMartin, diegoA, vet, branchA, "Michi", "CAT", "Europeo", "MALE",
                LocalDate.now().minusYears(2), new BigDecimal("4.20"), null, null);
        Pet kira = pet(huellitas, ana, null, branchB, "Kira", "DOG", "Border Collie", "FEMALE",
                LocalDate.now().minusYears(3), new BigDecimal("18.00"), null, "Displasia de cadera");
        Pet nala = pet(huellitas, diegoB, null, branchB, "Nala", "CAT", "Siamés", "FEMALE",
                LocalDate.now().minusMonths(11), new BigDecimal("3.10"), "Pollo", null);

        ClinicService consulta = service(sanMartin, "Consulta general", "General consultation", "CONSULTATION", 30, 42);
        service(sanMartin, "Vacunación", "Vaccination", "VACCINATION", 20, 28);
        service(sanMartin, "Control", "Follow-up", "CONTROL", 20, 32);
        service(huellitas, "Consulta general", "General consultation", "CONSULTATION", 30, 38);

        medication(sanMartin, "Amoxicilina", "Amoxicillin", "Comprimidos");
        Instant start = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        Appointment appointment = new Appointment();
        appointment.setTenantId(sanMartin.getId());
        appointment.setOwner(juan);
        appointment.setPet(luna);
        appointment.setVeterinarian(vet);
        appointment.setService(consulta);
        appointment.setBranchId(branchA.getId());
        appointment.setStartAt(start);
        appointment.setEndAt(start.plus(30, ChronoUnit.MINUTES));
        appointment.setDurationMin(30);
        appointment.setReason("Control de glucosa y revisión general");
        appointment.setStatus("CONFIRMED");
        appointmentRepository.save(appointment);

        Consultation consultation = new Consultation();
        consultation.setTenantId(sanMartin.getId());
        consultation.setPet(luna);
        consultation.setVeterinarian(vet);
        consultation.setConsultedAt(Instant.now().minus(12, ChronoUnit.DAYS));
        consultation.setReason("Polidipsia y cansancio");
        consultation.setSymptoms("Aumento de sed, leve apatía");
        consultation.setAnamnesis("Dieta controlada. Convivencia con otro perro.");
        consultation.setPhysicalExam("Mucosas rosadas, auscultación normal");
        consultation.setDiagnosis("Diabetes mellitus estable");
        consultation.setTreatmentPlan("Ajuste de insulina y control de peso");
        consultation.setRecommendations("Paseos suaves y agua siempre disponible");
        consultation.setStatus("COMPLETED");
        consultationRepository.save(consultation);

        Vaccination rabies = new Vaccination();
        rabies.setTenantId(sanMartin.getId());
        rabies.setPet(luna);
        rabies.setVeterinarian(vet);
        rabies.setVaccineName("Rabia");
        rabies.setBrand("Rabisin");
        rabies.setLot("RB-2044");
        rabies.setAppliedAt(LocalDate.now().minusMonths(10));
        rabies.setNextDoseAt(LocalDate.now().plusDays(18));
        vaccinationRepository.save(rabies);

        Treatment treatment = new Treatment();
        treatment.setTenantId(sanMartin.getId());
        treatment.setPet(luna);
        treatment.setVeterinarian(vet);
        treatment.setName("Control glucémico");
        treatment.setDescription("Insulina de acción intermedia");
        treatment.setStartDate(LocalDate.now().minusMonths(2));
        treatment.setStatus("ACTIVE");
        TreatmentItem item = new TreatmentItem();
        item.setTreatment(treatment);
        item.setMedicationName("Caninsulin");
        item.setDose("8 UI");
        item.setFrequency("Cada 12 horas");
        item.setRoute("SC");
        treatment.getItems().add(item);
        treatmentRepository.save(treatment);

        Prescription prescription = new Prescription();
        prescription.setTenantId(sanMartin.getId());
        prescription.setPet(luna);
        prescription.setOwner(juan);
        prescription.setVeterinarian(vet);
        prescription.setIssuedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        PrescriptionItem pitem = new PrescriptionItem();
        pitem.setPrescription(prescription);
        pitem.setMedicationName("Caninsulin");
        pitem.setPresentation("Vial 40 UI/ml");
        pitem.setDose("8 UI");
        pitem.setFrequency("Cada 12 h");
        pitem.setRoute("SC");
        pitem.setDuration("30 días");
        prescription.getItems().add(pitem);
        prescriptionRepository.save(prescription);

        log.info("Demo users ready. Super admin leo.a@example.org / Admin123!");
        log.info("San Martín tina.r@example.net / Admin123!  Vet emma.t@example.net  Owner juan.owner@animalin.app");
        log.info("Huellitas rachel.c@example.org / Admin123!  Owner isolation walt.e@example.net");
    }

    private Role role(String code) {
        return roleRepository.findByCode(code).orElseThrow();
    }

    private User user(String email, String first, String last, Role role, String locale) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName(first);
        user.setLastName(last);
        user.setPasswordHash(passwordEncoder.encode("Admin123!"));
        user.setLocale(locale);
        user.setTheme("system");
        user.setEmailVerified(true);
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private Tenant tenant(String slug, String name, String commercial, String status, Plan plan, String city,
            String country) {
        Tenant tenant = new Tenant();
        tenant.setSlug(slug);
        tenant.setName(name);
        tenant.setCommercialName(commercial);
        tenant.setEmail("hola@" + slug + ".animalin.app");
        tenant.setPhone("910000000");
        tenant.setCity(city);
        tenant.setCountry(country);
        tenant.setTimezone("Europe/Madrid");
        tenant.setCurrency("EUR");
        tenant.setDefaultLocale("es");
        tenant.setStatus(status);
        tenant.setPlan(plan);
        tenant.setTrialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS));
        tenantRepository.save(tenant);
        TenantSettings settings = new TenantSettings();
        settings.setTenant(tenant);
        settingsRepository.save(settings);
        Subscription subscription = new Subscription();
        subscription.setTenant(tenant);
        subscription.setPlan(plan);
        subscription.setStatus(status.equals("TRIAL") ? "TRIAL" : "ACTIVE");
        subscription.setTrial(status.equals("TRIAL"));
        subscription.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);
        return tenant;
    }

    private void membership(Tenant tenant, User user, Role role) {
        TenantMembership membership = new TenantMembership();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(role);
        membership.setStatus("ACTIVE");
        membershipRepository.save(membership);
    }

    private Branch branch(Tenant tenant, String name, String address, String city) {
        Branch branch = new Branch();
        branch.setTenantId(tenant.getId());
        branch.setName(name);
        branch.setAddress(address);
        branch.setCity(city);
        branch.setCountry("ES");
        branch.setTimezone("Europe/Madrid");
        for (int d = 1; d <= 5; d++) {
            BranchHour hour = new BranchHour();
            hour.setTenantId(tenant.getId());
            hour.setBranch(branch);
            hour.setDayOfWeek(d);
            hour.setOpenTime(LocalTime.of(9, 0));
            hour.setCloseTime(LocalTime.of(19, 0));
            branch.getHours().add(hour);
        }
        return branchRepository.save(branch);
    }

    private Veterinarian veterinarian(Tenant tenant, User user, Branch branch, String specialty) {
        Veterinarian vet = new Veterinarian();
        vet.setTenantId(tenant.getId());
        vet.setUser(user);
        vet.setBranchId(branch.getId());
        vet.setSpecialty(specialty);
        vet.setLicenseNumber("COL-28441");
        vet.setBio("Especialista en medicina interna y endocrinología de pequeños animales.");
        veterinarianRepository.save(vet);
        for (int d = 1; d <= 5; d++) {
            VeterinarianSchedule schedule = new VeterinarianSchedule();
            schedule.setTenantId(tenant.getId());
            schedule.setVeterinarianId(vet.getId());
            schedule.setDayOfWeek(d);
            schedule.setStartTime(LocalTime.of(9, 0));
            schedule.setEndTime(LocalTime.of(17, 0));
            schedule.setBreakStart(LocalTime.of(14, 0));
            schedule.setBreakEnd(LocalTime.of(15, 0));
            scheduleRepository.save(schedule);
        }
        return vet;
    }

    private Owner owner(Tenant tenant, User user, String first, String last, String phone, String email) {
        Owner owner = new Owner();
        owner.setTenantId(tenant.getId());
        owner.setUser(user);
        owner.setFirstName(first);
        owner.setLastName(last);
        owner.setPhone(phone);
        owner.setEmail(email);
        owner.setCity(tenant.getCity());
        owner.setCountry(tenant.getCountry());
        return ownerRepository.save(owner);
    }

    private Pet pet(Tenant tenant, Owner owner, Veterinarian vet, Branch branch, String name, String species,
            String breed, String sex, LocalDate birth, BigDecimal weight, String allergies, String conditions) {
        Pet pet = new Pet();
        pet.setTenantId(tenant.getId());
        pet.setOwner(owner);
        pet.setPrimaryVeterinarian(vet);
        pet.setBranchId(branch.getId());
        pet.setName(name);
        pet.setSpecies(species);
        pet.setBreed(breed);
        pet.setSex(sex);
        pet.setBirthDate(birth);
        pet.setWeightKg(weight);
        pet.setAllergies(allergies);
        pet.setMedicalConditions(conditions);
        pet.setSterilized(true);
        return petRepository.save(pet);
    }

    private ClinicService service(Tenant tenant, String es, String en, String category, int minutes, int price) {
        ClinicService service = new ClinicService();
        service.setTenantId(tenant.getId());
        service.setNameEs(es);
        service.setNameEn(en);
        service.setCategory(category);
        service.setDurationMin(minutes);
        service.setPrice(BigDecimal.valueOf(price));
        return serviceRepository.save(service);
    }

    private void medication(Tenant tenant, String name, String principle, String presentation) {
        Medication medication = new Medication();
        medication.setTenantId(tenant.getId());
        medication.setName(name);
        medication.setActivePrinciple(principle);
        medication.setPresentation(presentation);
        medicationRepository.save(medication);
    }
}
