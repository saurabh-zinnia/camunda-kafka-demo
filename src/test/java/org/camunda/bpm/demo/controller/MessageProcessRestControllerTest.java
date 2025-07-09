package org.camunda.bpm.demo.controller;

import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.util.TestDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageProcessRestControllerTest {

    @Mock
    private KafkaTemplate<String, CamundaMessageDto> kafkaTemplate;

    @InjectMocks
    private MessageProcessRestController messageProcessRestController;

    @Test
    void startMessageProcess_ShouldPublishToStartProcessTopic() {
        // Given
        CamundaMessageDto testMessage = TestDataBuilder.createStartProcessMessage("test-correlation-123");
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startMessageProcess(testMessage);

        // Then
        verify(kafkaTemplate).send(eq("start-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("test-correlation-123", capturedMessage.getCorrelationId());
        assertNotNull(capturedMessage.getDto());
        assertEquals("test-requester", capturedMessage.getDto().getRequester());
        assertEquals(1000.0, capturedMessage.getDto().getAmount());
        assertTrue(capturedMessage.getDto().getPreApproved());
    }

    @Test
    void startMessageProcess_WithHighValueMessage_ShouldPublishCorrectMessage() {
        // Given
        CamundaMessageDto highValueMessage = TestDataBuilder.createHighValueMessage("high-value-123");
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startMessageProcess(highValueMessage);

        // Then
        verify(kafkaTemplate).send(eq("start-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("high-value-123", capturedMessage.getCorrelationId());
        assertEquals("vip-customer", capturedMessage.getDto().getRequester());
        assertEquals(50000.0, capturedMessage.getDto().getAmount());
        assertFalse(capturedMessage.getDto().getPreApproved());
    }

    @Test
    void startMessageProcess_WithPreApprovedMessage_ShouldPublishCorrectMessage() {
        // Given
        CamundaMessageDto preApprovedMessage = TestDataBuilder.createPreApprovedMessage("pre-approved-123");
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startMessageProcess(preApprovedMessage);

        // Then
        verify(kafkaTemplate).send(eq("start-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("pre-approved-123", capturedMessage.getCorrelationId());
        assertEquals("trusted-customer", capturedMessage.getDto().getRequester());
        assertEquals(500.0, capturedMessage.getDto().getAmount());
        assertTrue(capturedMessage.getDto().getPreApproved());
    }

    @Test
    void startMessageProcess_WithCustomMessage_ShouldPublishCorrectMessage() {
        // Given
        CamundaMessageDto customMessage = TestDataBuilder.createCustomMessage(
            "custom-123", "custom-requester", 2500.0, false);
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startMessageProcess(customMessage);

        // Then
        verify(kafkaTemplate).send(eq("start-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("custom-123", capturedMessage.getCorrelationId());
        assertEquals("custom-requester", capturedMessage.getDto().getRequester());
        assertEquals(2500.0, capturedMessage.getDto().getAmount());
        assertFalse(capturedMessage.getDto().getPreApproved());
    }

    @Test
    void startMessageProcess_WithEmptyMessage_ShouldPublishEmptyMessage() {
        // Given
        CamundaMessageDto emptyMessage = TestDataBuilder.createEmptyMessage("empty-123");
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startMessageProcess(emptyMessage);

        // Then
        verify(kafkaTemplate).send(eq("start-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("empty-123", capturedMessage.getCorrelationId());
        assertEquals("", capturedMessage.getDto().getRequester());
        assertEquals(0.0, capturedMessage.getDto().getAmount());
        assertNull(capturedMessage.getDto().getPreApproved());
    }

    @Test
    void startMessageProcess_WithNullDto_ShouldPublishMinimalMessage() {
        // Given
        CamundaMessageDto messageWithNullDto = CamundaMessageDto.builder()
            .correlationId("null-dto-123")
            .dto(null)
            .build();
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startMessageProcess(messageWithNullDto);

        // Then
        verify(kafkaTemplate).send(eq("start-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("null-dto-123", capturedMessage.getCorrelationId());
        assertNull(capturedMessage.getDto());
    }



    @Test
    void startOrderProcess_ShouldPublishToOrderProcessTopic() {
        // Given
        CamundaMessageDto orderMessage = TestDataBuilder.createStartProcessMessage("order-correlation-123");
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startOrderProcess(orderMessage);

        // Then
        verify(kafkaTemplate).send(eq("order-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("order-correlation-123", capturedMessage.getCorrelationId());
        assertNotNull(capturedMessage.getDto());
        assertEquals("test-requester", capturedMessage.getDto().getRequester());
        assertEquals(1000.0, capturedMessage.getDto().getAmount());
        assertTrue(capturedMessage.getDto().getPreApproved());
    }

    @Test
    void startOrderProcess_WithHighValueOrder_ShouldPublishCorrectMessage() {
        // Given
        CamundaMessageDto highValueOrder = TestDataBuilder.createHighValueMessage("high-value-order-123");
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startOrderProcess(highValueOrder);

        // Then
        verify(kafkaTemplate).send(eq("order-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("high-value-order-123", capturedMessage.getCorrelationId());
        assertEquals("vip-customer", capturedMessage.getDto().getRequester());
        assertEquals(50000.0, capturedMessage.getDto().getAmount());
        assertFalse(capturedMessage.getDto().getPreApproved());
    }

    @Test
    void startOrderProcess_WithCustomOrderData_ShouldPublishCorrectMessage() {
        // Given
        CamundaMessageDto customOrder = TestDataBuilder.createCustomMessage(
            "custom-order-123", "order-customer", 750.0, true);
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startOrderProcess(customOrder);

        // Then
        verify(kafkaTemplate).send(eq("order-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("custom-order-123", capturedMessage.getCorrelationId());
        assertEquals("order-customer", capturedMessage.getDto().getRequester());
        assertEquals(750.0, capturedMessage.getDto().getAmount());
        assertTrue(capturedMessage.getDto().getPreApproved());
    }

    @Test
    void startOrderProcess_WithEmptyOrder_ShouldPublishEmptyMessage() {
        // Given
        CamundaMessageDto emptyOrder = TestDataBuilder.createEmptyMessage("empty-order-123");
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startOrderProcess(emptyOrder);

        // Then
        verify(kafkaTemplate).send(eq("order-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("empty-order-123", capturedMessage.getCorrelationId());
        assertEquals("", capturedMessage.getDto().getRequester());
        assertEquals(0.0, capturedMessage.getDto().getAmount());
        assertNull(capturedMessage.getDto().getPreApproved());
    }

    @Test
    void startOrderProcess_WithNullDto_ShouldPublishMinimalMessage() {
        // Given
        CamundaMessageDto orderWithNullDto = CamundaMessageDto.builder()
            .correlationId("null-dto-order-123")
            .dto(null)
            .build();
        ArgumentCaptor<CamundaMessageDto> messageCaptor = ArgumentCaptor.forClass(CamundaMessageDto.class);

        // When
        messageProcessRestController.startOrderProcess(orderWithNullDto);

        // Then
        verify(kafkaTemplate).send(eq("order-process-message-topic"), messageCaptor.capture());
        
        CamundaMessageDto capturedMessage = messageCaptor.getValue();
        assertEquals("null-dto-order-123", capturedMessage.getCorrelationId());
        assertNull(capturedMessage.getDto());
    }
} 