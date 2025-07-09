package org.camunda.bpm.demo.util;

import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.dto.MessageProcessDto;

import java.util.UUID;

public class TestDataBuilder {

    /**
     * Creates a standard test message for starting a process
     */
    public static CamundaMessageDto createStartProcessMessage() {
        return createStartProcessMessage("test-correlation-" + UUID.randomUUID().toString());
    }

    /**
     * Creates a start process message with specific correlation ID
     */
    public static CamundaMessageDto createStartProcessMessage(String correlationId) {
        return CamundaMessageDto.builder()
            .correlationId(correlationId)
            .dto(MessageProcessDto.builder()
                .requester("test-requester")
                .amount(1000.0)
                .preApproved(true)
                .processed(false)
                .build())
            .build();
    }

    /**
     * Creates a message with custom process data
     */
    public static CamundaMessageDto createCustomMessage(String correlationId, String requester, Double amount, Boolean preApproved) {
        return CamundaMessageDto.builder()
            .correlationId(correlationId)
            .dto(MessageProcessDto.builder()
                .requester(requester)
                .amount(amount)
                .preApproved(preApproved)
                .processed(false)
                .build())
            .build();
    }

    /**
     * Creates a message with minimal data
     */
    public static CamundaMessageDto createMinimalMessage(String correlationId) {
        return CamundaMessageDto.builder()
            .correlationId(correlationId)
            .build();
    }

    /**
     * Creates a processed message (simulating service task output)
     */
    public static CamundaMessageDto createProcessedMessage(String correlationId) {
        return CamundaMessageDto.builder()
            .correlationId(correlationId)
            .dto(MessageProcessDto.builder()
                .requester("test-requester")
                .amount(1000.0)
                .preApproved(true)
                .processed(true)
                .build())
            .build();
    }

    /**
     * Creates a high-value transaction message
     */
    public static CamundaMessageDto createHighValueMessage(String correlationId) {
        return CamundaMessageDto.builder()
            .correlationId(correlationId)
            .dto(MessageProcessDto.builder()
                .requester("vip-customer")
                .amount(50000.0)
                .preApproved(false)
                .processed(false)
                .build())
            .build();
    }

    /**
     * Creates a pre-approved message
     */
    public static CamundaMessageDto createPreApprovedMessage(String correlationId) {
        return CamundaMessageDto.builder()
            .correlationId(correlationId)
            .dto(MessageProcessDto.builder()
                .requester("trusted-customer")
                .amount(500.0)
                .preApproved(true)
                .processed(false)
                .build())
            .build();
    }

    /**
     * Creates a message with null/empty values for edge case testing
     */
    public static CamundaMessageDto createEmptyMessage(String correlationId) {
        return CamundaMessageDto.builder()
            .correlationId(correlationId)
            .dto(MessageProcessDto.builder()
                .requester("")
                .amount(0.0)
                .preApproved(null)
                .processed(null)
                .build())
            .build();
    }

    /**
     * Creates a message with invalid correlation ID (null)
     */
    public static CamundaMessageDto createInvalidCorrelationMessage() {
        return CamundaMessageDto.builder()
            .correlationId(null)
            .dto(MessageProcessDto.builder()
                .requester("test-requester")
                .amount(1000.0)
                .preApproved(true)
                .processed(false)
                .build())
            .build();
    }

    /**
     * Creates test data variations for parameterized tests
     */
    public static Object[][] createTestDataVariations() {
        return new Object[][] {
            {"normal-case", "john.doe", 1000.0, true},
            {"high-value", "jane.smith", 75000.0, false},
            {"minimal", "min.user", 1.0, true},
            {"edge-case", "edge.user", 0.0, null},
            {"special-chars", "user@domain.com", 999.99, false}
        };
    }
} 