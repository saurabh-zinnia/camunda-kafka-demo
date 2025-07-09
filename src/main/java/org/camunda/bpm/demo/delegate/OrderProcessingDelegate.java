package org.camunda.bpm.demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Delegate for processing order data and setting process variables
 */
@Component("orderProcessingDelegate")
public class OrderProcessingDelegate implements JavaDelegate {

    private static final Logger logger = LoggerFactory.getLogger(OrderProcessingDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        logger.info("Processing order data for process instance: {}", execution.getProcessInstanceId());

        // Get the DTO from process variables (set by Kafka consumer)
        Object dtoObject = execution.getVariable("dto");
        
        if (dtoObject != null && dtoObject instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dto = (Map<String, Object>) dtoObject;
            
            // Extract order details from the DTO
            String customerId = extractStringValue(dto.get("customerId"));
            Object orderValueObj = dto.get("orderValue");
            String customerEmail = extractStringValue(dto.get("customerEmail"));
            
            // Set process variables for the form
            if (customerId != null) {
                execution.setVariable("customerId", customerId);
                logger.info("Set customerId: {}", customerId);
            }
            
            if (orderValueObj != null) {
                Long orderValue = extractLongValue(orderValueObj);
                if (orderValue != null) {
                    execution.setVariable("orderValue", orderValue);
                    logger.info("Set orderValue: {}", orderValue);
                    
                    // Set default orderOk based on order value (auto-approve orders under 1000)
                    boolean defaultOrderOk = orderValue < 1000L;
                    execution.setVariable("orderOk", defaultOrderOk);
                    logger.info("Set default orderOk: {} (based on order value: {})", defaultOrderOk, orderValue);
                }
            }
            
            if (customerEmail != null) {
                execution.setVariable("customerEmail", customerEmail);
                logger.info("Set customerEmail: {}", customerEmail);
            }
            
            // Set processing timestamp
            execution.setVariable("orderProcessedAt", java.time.LocalDateTime.now().toString());
            
        } else {
            logger.warn("No DTO found in process variables or DTO is not a Map");
            // Set default values
            execution.setVariable("orderOk", true);
            execution.setVariable("customerId", "unknown");
            execution.setVariable("orderValue", 0L);
            execution.setVariable("customerEmail", "unknown@example.com");
        }
        
        logger.info("Order processing delegate completed for process instance: {}", execution.getProcessInstanceId());
    }
    
    private String extractStringValue(Object value) {
        if (value == null) return null;
        return value.toString();
    }
    
    private Long extractLongValue(Object value) {
        if (value == null) return null;
        
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Double) {
            return ((Double) value).longValue();
        } else if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                logger.warn("Could not parse order value as Long: {}", value);
                return null;
            }
        }
        
        logger.warn("Unexpected order value type: {}", value.getClass().getSimpleName());
        return null;
    }
} 