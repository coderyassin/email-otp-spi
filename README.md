# Keycloak Email OTP SPI

A custom Keycloak Service Provider Interface (SPI) that adds email-based One-Time Password (OTP) authentication to Keycloak. It extends the standard authentication flow with an additional verification step using a code sent to the user's registered email address.

The project is designed to provide a customizable authentication experience while keeping user authentication and identity management centralized in Keycloak.

## Features

- **Custom authentication SPI** — integrates with Keycloak's authentication flow.
- **Username and password verification** — authenticates the user with their credentials before proceeding to email verification.
- **Email OTP verification** — generates and sends a one-time code to the user's registered email address.
- **Custom login form** — supports a FreeMarker template for entering the OTP.
- **Keycloak theme integration** — allows the authentication pages to be customized through a Keycloak theme.
- **Configurable authentication flow** — can be added to a custom browser authentication flow.
- **Centralized identity management** — relies on Keycloak for user authentication.

## Tech Stack

- Java
- Maven
- Keycloak Authentication SPI
- FreeMarker templates
- SMTP for email delivery
- Docker (optional)

## Prerequisites

Before getting started, make sure you have:

- Java Development Kit (JDK) compatible with your project's Java configuration.
- Maven installed.
- A Keycloak server compatible with the version used to build the SPI.
- Access to the Keycloak Admin Console.
- An SMTP server configured in Keycloak.
- A Keycloak user with a valid email address.

> **Compatibility:** This project was developed for Keycloak 25.0.0. Other versions may require adjustments or a version-specific build.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/coderyassin/email-otp-spi.git
cd email-otp-spi
```

### 2. Build the project

Build the SPI using Maven:

```bash
mvn clean package
```

The generated JAR should be available in the `target/` directory.

### 3. Install the SPI in Keycloak

Copy the generated JAR into the Keycloak providers directory.

For a local Keycloak installation:

```bash
cp target/*.jar /path/to/keycloak/providers/
```

For a Docker-based Keycloak installation, mount the JAR into `/opt/keycloak/providers/` or copy it into a custom Keycloak image.

For example, if your project uses a Docker Compose volume:

```yaml
volumes:
  - ./keycloak/providers:/opt/keycloak/providers
```

Ensure the compiled JAR is present in the mounted directory.

### 4. Register the provider

For Keycloak's optimized startup, rebuild the provider registry after adding the JAR:

```bash
bin/kc.sh build
```

Then start or restart Keycloak:

```bash
bin/kc.sh start-dev
```

For an existing Docker-based deployment, rebuild or restart the container as appropriate for your Keycloak image and startup configuration.

Check the Keycloak logs for provider-loading errors.

## Configure SMTP

Keycloak must be able to send email messages before the OTP authentication step can work.

1. Open the Keycloak Admin Console.
2. Select your realm.
3. Navigate to **Realm settings → Email**.
4. Configure your SMTP host, port, sender address, and authentication credentials.
5. Save the settings and use the available test-email functionality if supported by your configuration.

Use environment variables or a secret manager to supply SMTP credentials. Do not commit passwords or other secrets to the repository.

## Configure the Authentication Flow

The SPI must be added to a browser authentication flow before it can be used.

1. Open the Keycloak Admin Console.
2. Select your realm.
3. Navigate to **Authentication → Flows**.
4. Duplicate the built-in **Browser** flow so that the original flow remains unchanged.
5. Open the duplicated flow.
6. Add the custom execution provided by this SPI. Its configured display name may be **Username + Email OTP**, depending on the implementation.
7. Configure the execution requirement and its position according to the intended login sequence.
8. Save the flow and bind the duplicated flow as the realm's browser flow when ready.

The intended authentication sequence is:

1. The user enters their username and password.
2. Keycloak validates the user's credentials.
3. The authenticator generates an OTP and sends it to the user's registered email address.
4. The user enters the received code.
5. The authenticator validates the code.
6. If verification succeeds, authentication continues; otherwise, the user receives an appropriate error.

**Important:** The exact execution structure depends on how the authenticator is implemented. Ensure that the custom execution does not accidentally bypass password validation or conflict with other required and alternative executions in the browser flow.

## Customize the Login Form

The OTP entry page can be customized using a Keycloak login theme.

A typical theme structure is:

```text
themes/
└── my-theme/
    └── login/
        ├── theme.properties
        ├── login.ftl
        └── login-username-otp.ftl
```

The actual template names and required files depend on the implementation.

To use the custom theme:

1. Place the theme under Keycloak's `themes/` directory.
2. Configure the theme in **Realm settings → Themes → Login theme**.
3. Restart or refresh Keycloak as necessary.
4. Test the complete login flow in a browser.

Refer to the templates and variables used by the authenticator when customizing the form. Keycloak's FreeMarker model and available form attributes can vary by execution and version.

## Error Handling and Troubleshooting

### The authenticator does not appear in the flow

- Verify that the JAR exists in the Keycloak `providers/` directory.
- Check the Keycloak logs for provider-loading errors.
- Rebuild the optimized Keycloak installation if required.
- Confirm that the SPI was compiled against a compatible Keycloak version.

### The OTP email is not received

- Verify the SMTP configuration in the realm.
- Check that the user has a valid email address.
- Inspect Keycloak logs for email-delivery errors.
- Check spam or junk folders.
- Verify connectivity between Keycloak and the SMTP server.

### The OTP form fails to render

- Verify that the required FreeMarker template is present in the selected theme.
- Check the Keycloak logs for template-processing exceptions.
- Ensure that the template uses the variables provided by the authenticator.
- Confirm compatibility between the theme and the installed Keycloak version.

### Authentication flow errors

- Review the execution requirements and ordering.
- Check for incompatible `REQUIRED` and `ALTERNATIVE` executions at the same level.
- Ensure that password verification and OTP verification occur in the intended sequence.

## Security Considerations

- Never log passwords, OTP values, client secrets, or other authentication credentials.
- Use HTTPS in production.
- Use sufficiently random OTP values and enforce a limited validity period.
- Prevent OTP reuse and invalidate codes after successful verification.
- Consider rate limiting and protection against repeated OTP requests or guessing attempts.
- Keep SMTP credentials and other secrets outside source control.
- Use a supported Keycloak version and test provider compatibility before upgrading.

The security properties above should be verified against the actual implementation; not every protection is necessarily provided by the SPI by default.

## Development

To make changes to the provider:

```bash
mvn clean package
```

After rebuilding, deploy the updated JAR to the Keycloak providers directory and restart or rebuild Keycloak as required.

For development, use a dedicated test realm and test users rather than modifying a production authentication flow.

## Contributing

Contributions, bug reports, and suggestions are welcome.

If you encounter an issue, please include:

- The Keycloak version.
- The Java version.
- Relevant error messages with secrets and personal information removed.
- The authentication flow configuration.
- Steps to reproduce the issue.

## License

Add the license that applies to this repository. If no license has been selected, specify one before publishing so users know how they may use and distribute the project.

## Author

**Yassin Mellouki**

GitHub: [@coderyassin](https://github.com/coderyassin)

## Repository

[https://github.com/coderyassin/email-otp-spi](https://github.com/coderyassin/email-otp-spi)
