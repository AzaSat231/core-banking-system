package com.azizsattarov.corebanking.card;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.customer.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
public class CardEmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(CardEmailNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${app.notifications.card-email.enabled:true}")
    private boolean enabled;

    @Value("${app.notifications.card-email.from:no-reply@corebank.local}")
    private String fromAddress;

    @Value("${app.notifications.card-email.bank-name:Core Banking}")
    private String bankName;

    @Value("${app.notifications.card-email.atm-url:https://atm.local}")
    private String atmUrl;

    @Value("${app.notifications.card-email.logo-classpath:email/bytebridge-logo.png}")
    private String logoClasspath;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public CardEmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCardIssuedEmail(Customer customer, Account account, Card card, boolean hadExistingCards) {
        if (!enabled || customer == null || card == null || account == null) {
            return;
        }
        if (mailHost == null || mailHost.isBlank()) {
            log.info("Card email skipped: spring.mail.host is not configured.");
            return;
        }
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.info("Card email skipped: customer {} has no email.", customer.getCustomerId());
            return;
        }

        String fullName = (customer.getFirstName() + " " + customer.getLastName()).trim();
        String subject = hadExistingCards
                ? bankName + " - New card issued"
                : bankName + " - Your new card details";
        String intro = hadExistingCards
                ? "A new card has been issued for your existing account."
                : "Welcome! Your first card has been issued.";
        String step2 = hadExistingCards
                ? "2) Enter account number and your card number from this email"
                : "2) Choose 'New card setup'";
        String step3 = hadExistingCards
                ? "3) Confirm account number and set your 4-digit PIN"
                : "3) Enter your account number";
        String step4 = hadExistingCards
                ? "4) Complete activation for this card"
                : "4) Set your 4-digit PIN";

        String body = String.format(
                "Hello %s,%n%n" +
                "%s%n%n" +
                "Card details:%n" +
                "- Card number: %s%n" +
                "- Card holder: %s%n" +
                "- Expiry date: %s%n" +
                "- Account number: %s%n%n" +
                "How to set your PIN:%n" +
                "1) Go to %s%n" +
                "%s%n" +
                "%s%n" +
                "%s%n%n" +
                "Keep this card number safe. Do not share your PIN.%n%n" +
                "Regards,%n" +
                "%s",
                fullName,
                intro,
                card.getCardNumber(),
                card.getHolderName(),
                card.getExpiryDate(),
                account.getAccountNumber(),
                atmUrl,
                step2,
                step3,
                step4,
                bankName
        );

        String htmlBody = String.format(
                "<div style=\"font-family:Arial,sans-serif;max-width:640px;color:#182433;line-height:1.5;\">" +
                "<div style=\"margin-bottom:16px;\"><img src='cid:brandLogo' alt='%s' style='max-width:240px;height:auto;'/></div>" +
                "<p>Hello <strong>%s</strong>,</p>" +
                "<p>%s</p>" +
                "<h3 style='margin-bottom:8px;'>Card details</h3>" +
                "<ul>" +
                "<li><strong>Card number:</strong> %s</li>" +
                "<li><strong>Card holder:</strong> %s</li>" +
                "<li><strong>Expiry date:</strong> %s</li>" +
                "<li><strong>Account number:</strong> %s</li>" +
                "</ul>" +
                "<h3 style='margin-bottom:8px;'>How to set your PIN</h3>" +
                "<ol>" +
                "<li>Open <a href='%s'>%s</a></li>" +
                "<li>%s</li>" +
                "<li>%s</li>" +
                "<li>%s</li>" +
                "</ol>" +
                "<p style='margin-top:16px;'>Keep this card number safe. Do not share your PIN.</p>" +
                "<p>Regards,<br/>%s</p>" +
                "</div>",
                bankName,
                fullName,
                intro,
                card.getCardNumber(),
                card.getHolderName(),
                card.getExpiryDate(),
                account.getAccountNumber(),
                atmUrl,
                atmUrl,
                hadExistingCards
                        ? "Enter account number and card number from this email"
                        : "Choose New card setup",
                hadExistingCards
                        ? "Confirm account number"
                        : "Enter your account number",
                hadExistingCards
                        ? "Set your 4-digit PIN to activate this card"
                        : "Set your 4-digit PIN",
                bankName
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(customer.getEmail());
            helper.setSubject(subject);
            helper.setText(body, htmlBody);

            Resource logo = new ClassPathResource(logoClasspath);
            if (logo.exists()) {
                helper.addInline("brandLogo", logo);
                helper.addAttachment("bytebridge-logo.png", logo);
            }

            mailSender.send(message);
            log.info("Card email sent to {} for card {}.", customer.getEmail(), card.getCardId());
        } catch (Exception ex) {
            // Do not break card issuance if outbound email fails.
            log.warn("Card email delivery failed for customer {}: {}", customer.getCustomerId(), ex.getMessage());
        }
    }
}
