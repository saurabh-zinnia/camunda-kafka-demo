package org.camunda.bpm.demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Delegate for sending delivery confirmation email after order delivery.
 * This delegate is triggered by a timer event 2 minutes after order delivery.
 */
@Component("emailDeliveryDelegate")
public class EmailDeliveryDelegate implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailDeliveryDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        LOGGER.info("Starting email delivery process for order: {}", execution.getBusinessKey());
        
        // Get order details from process variables
        String customerEmail = getCustomerEmail(execution);
        String orderId = getOrderId(execution);
        String customerName = getCustomerName(execution);
        
        // Send delivery confirmation email
        sendDeliveryConfirmationEmail(customerEmail, orderId, customerName);
        
        // Set process variable to indicate email was sent
        execution.setVariable("emailSent", true);
        execution.setVariable("emailSentTimestamp", java.time.Instant.now().toString());
        
        LOGGER.info("Delivery confirmation email sent successfully for order: {}", orderId);
    }

    /**
     * Extract customer email from process variables or use default
     */
    private String getCustomerEmail(DelegateExecution execution) {
        String email = (String) execution.getVariable("customerEmail");
        if (email == null || email.trim().isEmpty()) {
            email = "customer@example.com"; // Default email for demo
        }
        return email;
    }

    /**
     * Extract order ID from process variables or use business key
     */
    private String getOrderId(DelegateExecution execution) {
        String orderId = (String) execution.getVariable("orderId");
        if (orderId == null || orderId.trim().isEmpty()) {
            orderId = execution.getBusinessKey(); // Use business key as fallback
        }
        if (orderId == null) {
            orderId = "ORDER-" + execution.getProcessInstanceId(); // Generate from process instance
        }
        return orderId;
    }

    /**
     * Extract customer name from process variables or use default
     */
    private String getCustomerName(DelegateExecution execution) {
        String name = (String) execution.getVariable("customerName");
        if (name == null || name.trim().isEmpty()) {
            name = "Valued Customer"; // Default name for demo
        }
        return name;
    }

    /**
     * Send delivery confirmation email.
     * In a real implementation, this would integrate with an email service like SendGrid, AWS SES, etc.
     */
    private void sendDeliveryConfirmationEmail(String customerEmail, String orderId, String customerName) {
        LOGGER.info("Sending delivery confirmation email to: {}", customerEmail);
        LOGGER.info("Email content:");
        LOGGER.info("  To: {}", customerEmail);
        LOGGER.info("  Subject: Your Order {} Has Been Delivered!", orderId);
        LOGGER.info("  Body:");
        LOGGER.info("    Dear {},", customerName);
        LOGGER.info("    ");
        LOGGER.info("    Great news! Your order {} has been successfully delivered.", orderId);
        LOGGER.info("    ");
        LOGGER.info("    Thank you for choosing our service. We hope you enjoy your purchase!");
        LOGGER.info("    ");
        LOGGER.info("    If you have any questions or concerns, please don't hesitate to contact us.");
        LOGGER.info("    ");
        LOGGER.info("    Best regards,");
        LOGGER.info("    The Order Management Team");
        
        // Simulate email sending delay
        try {
            Thread.sleep(500); // Simulate email service call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Email sending simulation interrupted", e);
        }
        
        LOGGER.info("Email delivery simulation completed for order: {}", orderId);
    }
} 