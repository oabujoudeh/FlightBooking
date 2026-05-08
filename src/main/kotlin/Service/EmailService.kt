package com.flightbooking

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import java.util.Properties

object EmailService {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)
    private const val USERNAME = "9bfac39223924d"
    private const val PASSWORD = "404922bf818847"

    /**
     * Sends an email using the mail server settings.
     *
     * @param to the email address to send to
     * @param subject the subject of the email
     * @param body the main email text
     */
    fun sendEmail(
        to: String,
        subject: String,
        body: String,
    ) {
        val props =
            Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", "sandbox.smtp.mailtrap.io")
                put("mail.smtp.port", "2525")
            }

        val session =
            Session.getInstance(
                props,
                object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(USERNAME, PASSWORD)
                },
            )

        logger.info("Sending email to {} with subject '{}'", to, subject)

        try {
            val message =
                MimeMessage(session).apply {
                    setFrom(InternetAddress("no-reply@ejoAir.com"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                    setSubject(subject)
                    setText(body)
                }

            Transport.send(message)
            logger.info("Email successfully sent to {}", to)
        } catch (e: MessagingException) {
            logger.error("Failed to send email to {}", to, e)
            e.printStackTrace()
        }
    }
}
