package org.camunda.bpm.demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Delegate for reading/logging customer data after conversion
 */
@Component("customerReadingDelegate")
public class CustomerReadingDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(CustomerReadingDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Reading customer data for process instance: {}", execution.getProcessInstanceId());

        try {
            // Get customer data from process variables
            String customerData = (String) execution.getVariable("customerData");
            String dataFormat = (String) execution.getVariable("dataFormat");
            String createdAt = (String) execution.getVariable("createdAt");
            
            // Get original form data
            String firstname = (String) execution.getVariable("firstname");
            String lastname = (String) execution.getVariable("lastname");
            String gender = (String) execution.getVariable("gender");
            Long age = (Long) execution.getVariable("age");
            Boolean isValid = (Boolean) execution.getVariable("isValid");
            String validationDate = (String) execution.getVariable("validationDate");

            // Log customer information
            logger.info("=== Customer Data Processing Complete ===");
            logger.info("Customer Name: {} {}", firstname, lastname);
            logger.info("Customer Details: Gender={}, Age={}, Valid={}, ValidationDate={}", 
                       gender, age, isValid, validationDate);
            logger.info("Data Format: {}", dataFormat);
            logger.info("Created At: {}", createdAt);
            logger.info("Converted Data: {}", customerData);
            logger.info("Process Business Key: {}", execution.getBusinessKey());
            logger.info("=== End Customer Data ===");

            // Set completion flag
            execution.setVariable("customerProcessed", true);
            execution.setVariable("completedAt", java.time.LocalDateTime.now().toString());

            logger.info("Customer data reading completed for: {} {}", firstname, lastname);

        } catch (Exception e) {
            logger.error("Error reading customer data: {}", e.getMessage(), e);
            throw e;
        }
    }
} 