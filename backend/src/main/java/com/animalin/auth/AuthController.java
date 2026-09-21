package com.animalin.auth;

import com.animalin.employee.StaffInviteService;
import com.animalin.security.TenantContext;
import com.animalin.signup.ClinicSignupService;
import com.animalin.signup.SignupDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final ClinicSignupService signupService;
    private final StaffInviteService staffInviteService;

    public AuthController(AuthService authService, ClinicSignupService signupService, StaffInviteService staffInviteService) {
        this.authService = authService;
        this.signupService = signupService;
        this.staffInviteService = staffInviteService;
    }


    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterOwnerRequest request) {
        return authService.registerOwner(request);
    }

    @PostMapping("/register-clinic")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.TokenResponse registerClinic(@Valid @RequestBody SignupDtos.RegisterClinicRequest request) {
        return signupService.register(request);
    }

    @PostMapping("/verify-email")
    public SignupDtos.SignupStatusResponse verifyEmail(@Valid @RequestBody SignupDtos.VerifyEmailRequest request) {
        return signupService.verifyEmail(request);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthDtos.MessageResponse resendVerification(@Valid @RequestBody SignupDtos.ResendVerificationRequest request) {
        signupService.resendVerification(request);
        return AuthDtos.MessageResponse.verificationResent();
    }

    @GetMapping("/invite")
    public SignupDtos.InvitePreviewResponse invitePreview(@RequestParam String token) {
        return staffInviteService.preview(token);
    }

    @PostMapping("/accept-invite")
    public AuthDtos.TokenResponse acceptInvite(@Valid @RequestBody SignupDtos.AcceptInviteRequest request) {
        return staffInviteService.accept(request);
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) AuthDtos.RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthDtos.MessageResponse forgot(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return AuthDtos.MessageResponse.passwordReset();
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(request);
    }

    @PostMapping("/switch-tenant")
    public AuthDtos.TokenResponse switchTenant(@Valid @RequestBody AuthDtos.SwitchTenantRequest request) {
        return authService.switchTenant(request);
    }

    @GetMapping("/me")
    public AuthDtos.UserProfile me() {
        return authService.me();
    }

    public record ProfileUpdateRequest(
            @Size(max = 80) String firstName,
            @Size(max = 120) String lastName,
            String phone,
            String locale,
            String theme
    ) {
    }

    @PatchMapping("/me")
    public AuthDtos.UserProfile updateMe(@Valid @RequestBody ProfileUpdateRequest request) {
        TenantContext.get();
        return authService.updateMe(request.firstName(), request.lastName(), request.phone(), request.locale(), request.theme());
    }
}
