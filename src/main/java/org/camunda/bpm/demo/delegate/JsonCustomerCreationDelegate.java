package org.camunda.bpm.demo.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Delegate for creating customer data in JSON format
 */
@Component("jsonCustomerCreationDelegate")
public class JsonCustomerCreationDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(JsonCustomerCreationDelegate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Processing JSON customer creation for process instance: {}", execution.getProcessInstanceId());

        try {
            // Get customer data from process variables
            String firstname = (String) execution.getVariable("firstname");
            String lastname = (String) execution.getVariable("lastname");
            String gender = (String) execution.getVariable("gender");
            Long age = (Long) execution.getVariable("age");
            Boolean isValid = (Boolean) execution.getVariable("isValid");
            String validationDate = (String) execution.getVariable("validationDate");

            // Create JSON representation of customer
            String customerJson = createCustomerJson(firstname, lastname, gender, age, isValid, validationDate);

            // Set variables in process
            execution.setVariable("customerData", customerJson);
            execution.setVariable("dataFormat", "json");
            execution.setVariable("createdAt", java.time.LocalDateTime.now().toString());

            logger.info("Successfully created customer JSON for: {} {}", firstname, lastname);
            logger.debug("Customer JSON: {}", customerJson);

        } catch (Exception e) {
            logger.error("Error processing JSON customer creation: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Creates JSON representation of customer data
     */
    private String createCustomerJson(String firstname, String lastname, String gender, Long age, Boolean isValid, String validationDate) {
        try {
            ObjectNode customerNode = objectMapper.createObjectNode();
            customerNode.put("firstname", firstname != null ? firstname : "");
            customerNode.put("lastname", lastname != null ? lastname : "");
            customerNode.put("gender", gender != null ? gender : "");
            customerNode.put("age", age != null ? age : 0);
            customerNode.put("isValid", isValid != null ? isValid : false);
            customerNode.put("validationDate", validationDate != null ? validationDate : "");
            
            return objectMapper.writeValueAsString(customerNode);
        } catch (Exception e) {
            logger.error("Error creating JSON for customer: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create customer JSON", e);
        }
    }
} 