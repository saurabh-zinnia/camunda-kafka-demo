package org.camunda.bpm.demo.consumer;

import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.util.TestDataBuilder;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.MessageCorrelationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageProcessConsumerTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private MessageService messageService;

    @Mock
    private MessageCorrelationResult messageCorrelationResult;

    @InjectMocks
    private MessageProcessConsumer messageProcessConsumer;

    @BeforeEach
    void setUp() {
        // Setup common mock behaviors
        when(messageService.correlateMessage(any(CamundaMessageDto.class), any(String.class)))
            .thenReturn(messageCorrelationResult);
    }

    @Test
    void startMessageProcess_ShouldCorrelateStartMessage() {
        // Given
        CamundaMessageDto testMessage = TestDataBuilder.createStartProcessMessage("test-correlation-123");

        // When
        messageProcessConsumer.startMessageProcess(testMessage);

        // Then
        verify(messageService).correlateMessage(testMessage, "MessageKafkaDemo");
    }

    @Test
    void startMessageProcess_WithHighValueMessage_ShouldCorrelateStartMessage() {
        // Given
        CamundaMessageDto highValueMessage = TestDataBuilder.createHighValueMessage("high-value-123");

        // When
        messageProcessConsumer.startMessageProcess(highValueMessage);

        // Then
        verify(messageService).correlateMessage(highValueMessage, "MessageKafkaDemo");
    }

    @Test
    void startMessageProcess_WithPreApprovedMessage_ShouldCorrelateStartMessage() {
        // Given
        CamundaMessageDto preApprovedMessage = TestDataBuilder.createPreApprovedMessage("pre-approved-123");

        // When
        messageProcessConsumer.startMessageProcess(preApprovedMessage);

        // Then
        verify(messageService).correlateMessage(preApprovedMessage, "MessageKafkaDemo");
    }



    @Test
    void startMessageProcess_WithEmptyMessage_ShouldStillCorrelate() {
        // Given
        CamundaMessageDto emptyMessage = TestDataBuilder.createEmptyMessage("empty-123");

        // When
        messageProcessConsumer.startMessageProcess(emptyMessage);

        // Then
        verify(messageService).correlateMessage(emptyMessage, "MessageKafkaDemo");
    }

    @Test
    void startMessageProcess_ShouldHandleNullDto() {
        // Given
        CamundaMessageDto messageWithNullDto = CamundaMessageDto.builder()
            .correlationId("null-dto-123")
            .dto(null)
            .build();

        // When
        messageProcessConsumer.startMessageProcess(messageWithNullDto);

        // Then
        verify(messageService).correlateMessage(messageWithNullDto, "MessageKafkaDemo");
    }



    @Test
    void startOrderProcess_ShouldCorrelateOrderMessage() {
        // Given
        CamundaMessageDto orderMessage = TestDataBuilder.createStartProcessMessage("order-correlation-123");

        // When
        messageProcessConsumer.startOrderProcess(orderMessage);

        // Then
        verify(messageService).correlateMessage(orderMessage, "MessageOrderDemo");
    }

    @Test
    void startOrderProcess_WithHighValueOrder_ShouldCorrelateOrderMessage() {
        // Given
        CamundaMessageDto highValueOrder = TestDataBuilder.createHighValueMessage("high-value-order-123");

        // When
        messageProcessConsumer.startOrderProcess(highValueOrder);

        // Then
        verify(messageService).correlateMessage(highValueOrder, "MessageOrderDemo");
    }

    @Test
    void startOrderProcess_WithCustomOrderData_ShouldCorrelateOrderMessage() {
        // Given
        CamundaMessageDto customOrder = TestDataBuilder.createCustomMessage(
            "custom-order-123", "order-customer", 750.0, true);

        // When
        messageProcessConsumer.startOrderProcess(customOrder);

        // Then
        verify(messageService).correlateMessage(customOrder, "MessageOrderDemo");
    }

    @Test
    void startOrderProcess_WithEmptyOrder_ShouldStillCorrelate() {
        // Given
        CamundaMessageDto emptyOrder = TestDataBuilder.createEmptyMessage("empty-order-123");

        // When
        messageProcessConsumer.startOrderProcess(emptyOrder);

        // Then
        verify(messageService).correlateMessage(emptyOrder, "MessageOrderDemo");
    }

    @Test
    void startOrderProcess_WithNullDto_ShouldHandleGracefully() {
        // Given
        CamundaMessageDto orderWithNullDto = CamundaMessageDto.builder()
            .correlationId("null-dto-order-123")
            .dto(null)
            .build();

        // When
        messageProcessConsumer.startOrderProcess(orderWithNullDto);

        // Then
        verify(messageService).correlateMessage(orderWithNullDto, "MessageOrderDemo");
    }
} 