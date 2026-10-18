package org.yascode.keycloak.otp;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class UsernameEmailOtpAuthenticator implements Authenticator {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String FORM_LOGIN_USERNAME_OTP = "login-username-otp.ftl";
    private static final String FORM_LOGIN_EMAIL_OTP = "login-email-otp.ftl";

    private static final String STEP = "EMAIL_OTP_STEP";

    private static final String USERNAME = "EMAIL_OTP_USERNAME";

    private static final String CODE_HASH = "EMAIL_OTP_CODE_HASH";

    private static final String EXPIRES_AT = "EMAIL_OTP_EXPIRES_AT";

    private static final String ATTEMPTS = "EMAIL_OTP_ATTEMPTS";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void authenticate(AuthenticationFlowContext context) {

        logger.info("Starting authentication");

        Response challenge = context.form()
                .createForm(FORM_LOGIN_USERNAME_OTP);

        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {

        logger.info("Starting action");

        AuthenticationSessionModel authSession  = context.getAuthenticationSession();

        logger.info("Auth session: {}", authSession);

        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();

        logger.info("Form: {}", form);

        String step = authSession.getAuthNote(STEP);

        if(step == null || "USERNAME".equals(step)) {
            logger.info("Handling username");
            handleBasicCredentials(context, form);
            return;
        }

        if ("CODE".equals(step)) {
            logger.info("Handling code");
            handleCode(context, form);
            return;
        }

        logger.info("Unknown step: {}", step);

        context.failure(AuthenticationFlowError.INTERNAL_ERROR);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return user.getEmail() != null && !user.getEmail().isBlank();
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }

    private void handleBasicCredentials(
            AuthenticationFlowContext context,
            MultivaluedMap<String, String> form
    ) {
        logger.info("Handling username");

        String username = form.getFirst("username");
        String password = form.getFirst("password");

        logger.info("Username submitted: {}", username);

        if (username == null || username.isBlank()|| password == null || password.isBlank()) {
            context.challenge(context.form()
                    .setError("Please enter your username and password.")
                    .createForm(FORM_LOGIN_USERNAME_OTP));
            return;
        }

        UserProvider users = context.getSession().users();

        UserModel user = users.getUserByUsername(
                context.getRealm(),
                username.trim()
        );

        logger.info("User found: {}", user != null);

        if (user != null) {
            logger.info("STEP 1: User lookup successful");
            logger.info("User enabled: {}", user.isEnabled());
            logger.info("User has email: {}", user.getEmail() != null && !user.getEmail().isBlank());
            logger.info("Email verified: {}", user.isEmailVerified());
        } else {
            logger.info("STEP 1: User lookup failed");
        }

        String invalidCredentialsMessage = "Invalid username or password, or authentication unavailable.";

        if (user == null || !user.isEnabled()) {
            context.challenge(context.form()
                    .setError(invalidCredentialsMessage)
                    .createForm(FORM_LOGIN_USERNAME_OTP));
            return;
        }

        boolean passwordValid = user.credentialManager()
                .isValid(UserCredentialModel.password(password));

        if (!passwordValid) {
            logger.info("STEP 2: Password validation failed");
            context.challenge(context.form()
                    .setError(invalidCredentialsMessage)
                    .createForm(FORM_LOGIN_USERNAME_OTP));
            return;
        }

        logger.info("STEP 2: Password validation successful");

        String emailAddress = user.getEmail();

        if (emailAddress == null || emailAddress.isBlank()) {
            context.challenge(context.form()
                    .setError(invalidCredentialsMessage)
                    .createForm(FORM_LOGIN_USERNAME_OTP));
            return;
        }

        context.setUser(user);

        logger.info("STEP 3: User set in authentication context");

        String code = generateCode();

        logger.info("STEP 4: OTP generated");

        String hash = hashCode(code);

        logger.info("STEP 5: OTP hashed");

        AuthenticationSessionModel authSession = context.getAuthenticationSession();

        authSession.setAuthNote(USERNAME, user.getUsername());
        authSession.setAuthNote(CODE_HASH, hash);
        authSession.setAuthNote(
                EXPIRES_AT,
                Long.toString(
                        Instant.now().plusSeconds(300).getEpochSecond()
                )
        );
        authSession.setAuthNote(ATTEMPTS, "0");
        authSession.setAuthNote(STEP, "CODE");

        logger.info("STEP 6: OTP session notes stored");

        try {
            EmailTemplateProvider email = context.getSession()
                    .getProvider(EmailTemplateProvider.class);

            logger.info("STEP 7: Email template provider retrieved");

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("code", code);

            email.setRealm(context.getRealm())
                    .setUser(user)
                    .send(
                            "Your login code",
                            FORM_LOGIN_EMAIL_OTP,
                            attributes
                    );

            logger.info("STEP 8: OTP sent to user");

            context.challenge(context.form()
                    .setAttribute("maskedEmail", maskEmail(user.getEmail()))
                    .createForm(FORM_LOGIN_EMAIL_OTP));

            logger.info("STEP 9: Displaying OTP form");

        } catch (Exception e) {

            logger.error("Failed to send OTP email", e);

            authSession.removeAuthNote(USERNAME);
            authSession.removeAuthNote(CODE_HASH);
            authSession.removeAuthNote(EXPIRES_AT);
            authSession.removeAuthNote(ATTEMPTS);
            authSession.removeAuthNote(STEP);

            context.failure(AuthenticationFlowError.INTERNAL_ERROR);
        }
    }

    private void handleCode(
            AuthenticationFlowContext context,
            MultivaluedMap<String, String> form
    ) {

        logger.info("STEP 10: Handling OTP code");

        AuthenticationSessionModel authSession = context.getAuthenticationSession();

        String submittedCode = form.getFirst("code");
        String expectedHash = authSession.getAuthNote(CODE_HASH);
        String expiryValue = authSession.getAuthNote(EXPIRES_AT);

        int attempts = Integer.parseInt(
                authSession.getAuthNote(ATTEMPTS) == null
                        ? "0"
                        : authSession.getAuthNote(ATTEMPTS)
        );

        if (attempts >= 5) {
            logger.info("STEP 11: Too many attempts");
            context.failure(AuthenticationFlowError.ACCESS_DENIED);
            return;
        }

        authSession.setAuthNote(
                ATTEMPTS,
                Integer.toString(attempts + 1)
        );

        boolean expired = expiryValue == null
                || Instant.now().getEpochSecond()
                >= Long.parseLong(expiryValue);

        if (expired
                || submittedCode == null
                || expectedHash == null
                || !constantTimeEquals(expectedHash,hashCode(submittedCode.trim())
        )) {
            logger.info("STEP 12: Invalid or expired code");
            context.challenge(context.form()
                    .setError("Invalid or expired code.")
                    .createForm(FORM_LOGIN_EMAIL_OTP));
            return;
        }

        logger.info("STEP 13: Code verified");

        authSession.removeAuthNote(CODE_HASH);
        authSession.removeAuthNote(EXPIRES_AT);
        authSession.removeAuthNote(STEP);
        authSession.removeAuthNote(ATTEMPTS);

        context.success();
    }

    private String generateCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    private String hashCode(String code) {
        try {
            byte[] bytes = java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(code.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                    ));

            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');

        if (at <= 1) {
            return "***";
        }

        return email.charAt(0) + "***" + email.substring(at);
    }

    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

}
