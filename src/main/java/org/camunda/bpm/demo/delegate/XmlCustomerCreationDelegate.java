package org.camunda.bpm.demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Delegate for creating customer data in XML format
 */
@Component("xmlCustomerCreationDelegate")
public class XmlCustomerCreationDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(XmlCustomerCreationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Processing XML customer creation for process instance: {}", execution.getProcessInstanceId());

        try {
            // Get customer data from process variables
            String firstname = (String) execution.getVariable("firstname");
            String lastname = (String) execution.getVariable("lastname");
            String gender = (String) execution.getVariable("gender");
            Long age = (Long) execution.getVariable("age");
            Boolean isValid = (Boolean) execution.getVariable("isValid");
            String validationDate = (String) execution.getVariable("validationDate");

            // Create XML representation of customer
            String customerXml = createCustomerXml(firstname, lastname, gender, age, isValid, validationDate);

            // Set variables in process
            execution.setVariable("customerData", customerXml);
            execution.setVariable("dataFormat", "xml");
            execution.setVariable("createdAt", java.time.LocalDateTime.now().toString());

            logger.info("Successfully created customer XML for: {} {}", firstname, lastname);
            logger.debug("Customer XML: {}", customerXml);

        } catch (Exception e) {
            logger.error("Error processing XML customer creation: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Creates XML representation of customer data
     */
    private String createCustomerXml(String firstname, String lastname, String gender, Long age, Boolean isValid, String validationDate) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<customer>\n");
        xml.append("  <firstname>").append(escapeXml(firstname)).append("</firstname>\n");
        xml.append("  <lastname>").append(escapeXml(lastname)).append("</lastname>\n");
        xml.append("  <gender>").append(escapeXml(gender)).append("</gender>\n");
        xml.append("  <age>").append(age != null ? age : "").append("</age>\n");
        xml.append("  <isValid>").append(isValid != null ? isValid : "false").append("</isValid>\n");
        xml.append("  <validationDate>").append(escapeXml(validationDate)).append("</validationDate>\n");
        xml.append("</customer>");
        return xml.toString();
    }

    /**
     * Simple XML escaping for special characters
     */
    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
} 