package com.eventflow.ingestion.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * ⚠️ BEHAVIOR CHANGE (Phase 4) — READ BEFORE TOUCHING
 *
 * Previously this sent plain text only (SimpleMailMessage + setText(body)).
 * The Phase 4 email-open tracking pixel (see TrackingController and
 * NotificationNodeHandler#appendTrackingPixel) embeds an <img> tag into the
 * body of EMAIL-channel workflow notifications — that tag is inert (shows
 * as literal visible text) in a plain-text email, so the pixel can only
 * work at all if this service sends HTML.
 *
 * Fix applied: switched to MimeMessage/MimeMessageHelper with
 * setText(html, true), while keeping the exact same public method signature
 * — sendEmail(to, subject, body) — so the two existing callers
 * (EmailNotificationConsumer, used by every email the platform has ever
 * sent, and the new NotificationNodeHandler) need ZERO call-site changes.
 *
 * This IS a real behavior change for every email sent through this
 * service, not just workflow ones: bodies now render as HTML rather than
 * plain text. To avoid silently altering the appearance of bodies that
 * predate this change (plain text using literal "\n" for line breaks, e.g.
 * existing NotificationTemplate rows), any body that doesn't already look
 * like HTML (contains no "<" character at all) is auto-wrapped: newlines
 * become <br> and the text otherwise passes through unchanged. A body that
 * already contains HTML markup (e.g. one with the tracking pixel appended)
 * is sent through as-is.
 *
 * Flagging this explicitly per the project's standing rule against
 * changing established, working behavior without disclosure — this is the
 * single real email-dispatch choke point in the whole codebase.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:dinarisaad06@gmail.com}")
    private String fromAddress;

    public void sendEmail(String to, String subject, String body) {
        log.info("Preparing to send email to: {}, subject: {}", to, subject);
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject != null ? subject : "Notification");
            helper.setText(toHtml(body), true);

            mailSender.send(mimeMessage);
            log.info("Successfully sent email to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    /** See class-level note: only wraps bodies that don't already look like
     *  HTML, so a plain-text body still reads the same as before this
     *  change (just rendered by an HTML-capable mail client instead of a
     *  plain-text one), while a body someone already built as HTML (or one
     *  with a tracking pixel appended) passes through untouched. */
    private String toHtml(String body) {
        if (body == null) {
            return "";
        }
        if (body.contains("<")) {
            return body;
        }
        return body.replace("\n", "<br>");
    }
}
