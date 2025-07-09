package org.camunda.bpm.demo.consumer;

import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.dto.MessageProcessDto;
import org.camunda.bpm.demo.util.TestDataBuilder;
import org.camunda.bpm.engine.MismatchingMessageCorrelationException;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.MessageCorrelationBuilder;
import org.camunda.bpm.engine.runtime.MessageCorrelationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private MessageCorrelationBuilder messageCorrelationBuilder;

    @Mock
    private MessageCorrelationResult messageCorrelationResult;

    @InjectMocks
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        when(runtimeService.createMessageCorrelation(any(String.class)))
            .thenReturn(messageCorrelationBuilder);
        lenient().when(messageCorrelationBuilder.setVariables(any(Map.class)))
            .thenReturn(messageCorrelationBuilder);
        when(messageCorrelationBuilder.processInstanceBusinessKey(any(String.class)))
            .thenReturn(messageCorrelationBuilder);
        when(messageCorrelationBuilder.correlateWithResult())
            .thenReturn(messageCorrelationResult);
    }

    @Test
    void correlateMessage_WithValidMessage_ShouldReturnCorrelationResult() {
        // Given
        CamundaMessageDto testMessage = TestDataBuilder.createStartProcessMessage("test-correlation-123");
        String messageName = "MessageKafkaDemo";

        // When
        MessageCorrelationResult result = messageService.correlateMessage(testMessage, messageName);

        // Then
        assertNotNull(result);
        verify(runtimeService).createMessageCorrelation(messageName);
        verify(messageCorrelationBuilder).setVariables(any(Map.class));
        verify(messageCorrelationBuilder).processInstanceBusinessKey("test-correlation-123");
        verify(messageCorrelationBuilder).correlateWithResult();
    }

    @Test
    void correlateMessage_WithHighValueMessage_ShouldSetCorrectVariables() {
        // Given
        CamundaMessageDto highValueMessage = TestDataBuilder.createHighValueMessage("high-value-123");
        String messageName = "MessageKafkaDemo";

        // When
        MessageCorrelationResult result = messageService.correlateMessage(highValueMessage, messageName);

        // Then
        assertNotNull(result);
        verify(messageCorrelationBuilder).setVariables(any(Map.class));
        verify(messageCorrelationBuilder).processInstanceBusinessKey("high-value-123");
    }

    @Test
    void correlateMessage_WithPreApprovedMessage_ShouldCorrelateSuccessfully() {
        // Given
        CamundaMessageDto preApprovedMessage = TestDataBuilder.createPreApprovedMessage("pre-approved-123");
        String messageName = "MessageKafkaDemo";

        // When
        MessageCorrelationResult result = messageService.correlateMessage(preApprovedMessage, messageName);

        // Then
        assertNotNull(result);
        verify(runtimeService).createMessageCorrelation(messageName);
        verify(messageCorrelationBuilder).processInstanceBusinessKey("pre-approved-123");
    }

    @Test
    void correlateMessage_WithNullDto_ShouldSkipSetVariables() {
        // Given
        CamundaMessageDto messageWithNullDto = CamundaMessageDto.builder()
            .correlationId("null-dto-123")
            .dto(null)
            .build();
        String messageName = "MessageKafkaDemo";

        // When
        MessageCorrelationResult result = messageService.correlateMessage(messageWithNullDto, messageName);

        // Then
        assertNotNull(result);
        verify(runtimeService).createMessageCorrelation(messageName);
        verify(messageCorrelationBuilder, never()).setVariables(any(Map.class));
        verify(messageCorrelationBuilder).processInstanceBusinessKey("null-dto-123");
    }

    @Test
    void correlateMessage_WithEmptyMessage_ShouldHandleGracefully() {
        // Given
        CamundaMessageDto emptyMessage = TestDataBuilder.createEmptyMessage("empty-123");
        String messageName = "MessageKafkaDemo";

        // When
        MessageCorrelationResult result = messageService.correlateMessage(emptyMessage, messageName);

        // Then
        assertNotNull(result);
        verify(messageCorrelationBuilder).setVariables(any(Map.class));
        verify(messageCorrelationBuilder).processInstanceBusinessKey("empty-123");
    }

    @Test
    void correlateMessage_WithMismatchingCorrelation_ShouldReturnNull() {
        // Given
        CamundaMessageDto testMessage = TestDataBuilder.createStartProcessMessage("non-existent-123");
        String messageName = "MessageKafkaDemo";
        
        when(messageCorrelationBuilder.correlateWithResult())
            .thenThrow(new MismatchingMessageCorrelationException("No matching process instance"));

        // When
        MessageCorrelationResult result = messageService.correlateMessage(testMessage, messageName);

        // Then
        assertNull(result);
        verify(runtimeService).createMessageCorrelation(messageName);
    }

    @Test
    void correlateMessage_WithGeneralException_ShouldReturnNull() {
        // Given
        CamundaMessageDto testMessage = TestDataBuilder.createStartProcessMessage("error-123");
        String messageName = "MessageKafkaDemo";
        
        when(messageCorrelationBuilder.correlateWithResult())
            .thenThrow(new RuntimeException("Unexpected error"));

        // When
        MessageCorrelationResult result = messageService.correlateMessage(testMessage, messageName);

        // Then
        assertNull(result);
        verify(runtimeService).createMessageCorrelation(messageName);
    }

    @Test
    void correlateMessage_WithCustomMessage_ShouldUseCorrectCorrelationId() {
        // Given
        CamundaMessageDto customMessage = TestDataBuilder.createCustomMessage(
            "custom-123", "custom-requester", 2500.0, false);
        String messageName = "MessageKafkaDemo";

        // When
        MessageCorrelationResult result = messageService.correlateMessage(customMessage, messageName);

        // Then
        assertNotNull(result);
        verify(messageCorrelationBuilder).processInstanceBusinessKey("custom-123");
        verify(messageCorrelationBuilder).setVariables(any(Map.class));
    }

    @Test
    void correlateMessage_WithMinimalMessage_ShouldWorkWithoutDto() {
        // Given
        CamundaMessageDto minimalMessage = TestDataBuilder.createMinimalMessage("minimal-123");
        String messageName = "MessageKafkaDemo";

        // When
        MessageCorrelationResult result = messageService.correlateMessage(minimalMessage, messageName);

        // Then
        assertNotNull(result);
        verify(messageCorrelationBuilder).processInstanceBusinessKey("minimal-123");
        verify(messageCorrelationBuilder, never()).setVariables(any(Map.class));
    }
} 