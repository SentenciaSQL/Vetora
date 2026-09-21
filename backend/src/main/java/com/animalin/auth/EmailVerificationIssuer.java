package com.animalin.auth;

import com.animalin.config.AnimalinProperties;
import com.animalin.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Service
public class EmailVerificationIssuer {

    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final SecureTokenService tokens;
    private final AnimalinProperties properties;
    private final Clock clock;

    public EmailVerificationIssuer(
            EmailVerificationTokenRepository verificationTokenRepository,
            SecureTokenService tokens,
            AnimalinProperties properties,
            Clock clock
    ) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String issue(User user) {
        verificationTokenRepository.expireUnusedByUserId(user.getId());
        String raw = tokens.randomToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(tokens.sha256(raw));
        token.setExpiresAt(clock.instant().plus(properties.signupOrDefault().verificationHours(), ChronoUnit.HOURS));
        verificationTokenRepository.save(token);
        return raw;
    }

    public int expirationHours() {
        return properties.signupOrDefault().verificationHours();
    }
}
