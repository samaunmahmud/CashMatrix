package com.expensetracker.expensetracker.service.delivery;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends plain-text emails. Does nothing useful until an SMTP server is configured (spring.mail.host). */
@Component
public class EmailSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mailSender,
                       @Value("${app.mail.from:CashMatrix <no-reply@cashmatrix.local>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /** Spring only creates a mail sender when SMTP settings exist, so its presence means email is set up. */
    public boolean isConfigured() {
        return mailSender.getIfAvailable() != null;
    }

    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("Email is not configured");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
