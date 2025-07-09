package org.camunda.bpm.demo.integration;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.camunda.bpm.demo.config.BaseIntegrationTest;
import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.util.TestDataBuilder;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;

class KafkaProcessIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private TaskService taskService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<String, CamundaMessageDto> kafkaTemplate;

    private Consumer<String, CamundaMessageDto> testConsumer;

    @BeforeEach
    void setUpKafkaConsumer() {
        // Each test now creates its own consumer with unique group ID
        testConsumer = createTestConsumerForTopic(SERVICE_TASK_TOPIC);
    }
    
    @AfterEach
    void cleanupKafkaConsumer() {
        // Clean up the consumer after each test
        if (testConsumer != null) {
            try {
                testConsumer.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    void completeProcessFlow_ShouldStartAndCompleteProcess() throws InterruptedException {
        // Given
        String correlationId = "integration-test-123";
        CamundaMessageDto startMessage = TestDataBuilder.createStartProcessMessage(correlationId);

        // When - Start the process
        kafkaTemplate.send(START_PROCESS_TOPIC, startMessage);

        // Then - Wait for process to start and verify
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                assertThat(processInstance).isWaitingAt("Activity_0zhduij"); // User Task
            });

        // When - Complete the user task first
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(processInstances).hasSize(1);
                ProcessInstance processInstance = processInstances.get(0);
                
                List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(processInstance.getId())
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(tasks).hasSize(1);
                Task task = tasks.get(0);
                taskService.complete(task.getId(), Map.of("processed", true));
            });

        // Then - Verify process completes and service task publishes message (no intermediate message needed)
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                // Look for active instances first
                List<ProcessInstance> activeInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                    
                if (!activeInstances.isEmpty()) {
                    // Process is still running, fail the assertion to retry
                    fail("Process is still active, expected completed");
                } else {
                    // Process completed successfully (no active instances found)
                    // This is the expected state - test passes
                    assertTrue(true);
                }
            });

        // Verify service task published message to Kafka - poll multiple times if needed
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                ConsumerRecords<String, CamundaMessageDto> records = testConsumer.poll(Duration.ofSeconds(2));
                assertFalse(records.isEmpty(), "Expected service task message but got no records");
                
                // Find the record with matching correlation ID
                CamundaMessageDto serviceTaskMessage = null;
                for (ConsumerRecord<String, CamundaMessageDto> record : records) {
                    if (record.value() != null && correlationId.equals(record.value().getCorrelationId())) {
                        serviceTaskMessage = record.value();
                        break;
                    }
                }
                
                assertNotNull(serviceTaskMessage, "Expected service task message with correlation ID: " + correlationId);
                assertEquals(correlationId, serviceTaskMessage.getCorrelationId());
                assertNotNull(serviceTaskMessage.getDto());
            });
    }

    @Test
    void processFlow_WithHighValueTransaction_ShouldHandleCorrectly() throws InterruptedException {
        // Given
        String correlationId = "high-value-integration-123";
        CamundaMessageDto highValueMessage = TestDataBuilder.createHighValueMessage(correlationId);

        // When
        kafkaTemplate.send(START_PROCESS_TOPIC, highValueMessage);

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                
                // Verify process variables
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(50000.0, amount);
                
                Object preApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                assertEquals(false, preApproved);
            });

        // Complete the user task first
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(processInstances).hasSize(1);
                ProcessInstance processInstance = processInstances.get(0);
                
                List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(processInstance.getId())
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(tasks).hasSize(1);
                Task task = tasks.get(0);
                taskService.complete(task.getId(), Map.of("processed", true));
            });

        // Process should complete automatically after user task completion
        // Verify completion
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> activeInstances = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                    
                if (!activeInstances.isEmpty()) {
                    fail("Process is still active, expected completed");
                } else {
                    assertTrue(true); // Process completed successfully
                }
            });
    }

    @Test
    void processFlow_WithPreApprovedTransaction_ShouldHandleCorrectly() throws InterruptedException {
        // Given
        String correlationId = "pre-approved-integration-123";
        CamundaMessageDto preApprovedMessage = TestDataBuilder.createPreApprovedMessage(correlationId);

        // When
        kafkaTemplate.send(START_PROCESS_TOPIC, preApprovedMessage);

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                
                // Verify process variables
                Object preApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                assertEquals(true, preApproved);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(500.0, amount);
            });
    }

    @ParameterizedTest
    @MethodSource("org.camunda.bpm.demo.util.TestDataBuilder#createTestDataVariations")
    void processFlow_WithVariousInputs_ShouldHandleAllCorrectly(String correlationSuffix, String requester, Double amount, Boolean preApproved) throws InterruptedException {
        // Given
        String correlationId = "param-test-" + correlationSuffix;
        CamundaMessageDto customMessage = TestDataBuilder.createCustomMessage(correlationId, requester, amount, preApproved);

        // When
        kafkaTemplate.send(START_PROCESS_TOPIC, customMessage);

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                
                // Verify process variables match input
                if (requester != null) {
                    Object actualRequester = runtimeService.getVariable(processInstance.getId(), "requester");
                    assertEquals(requester, actualRequester);
                }
                if (amount != null) {
                    Object actualAmount = runtimeService.getVariable(processInstance.getId(), "amount");
                    assertEquals(amount, actualAmount);
                }
                if (preApproved != null) {
                    Object actualPreApproved = runtimeService.getVariable(processInstance.getId(), "preApproved");
                    assertEquals(preApproved, actualPreApproved);
                }
            });

        // Complete the user task first
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(processInstances).hasSize(1);
                ProcessInstance processInstance = processInstances.get(0);
                
                List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(processInstance.getId())
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(tasks).hasSize(1);
                Task task = tasks.get(0);
                taskService.complete(task.getId(), Map.of("processed", true));
            });

        // Process should complete automatically after user task completion
        // Verify process completion
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> activeInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                    
                if (!activeInstances.isEmpty()) {
                    fail("Process is still active, expected completed");
                } else {
                    assertTrue(true); // Process completed successfully
                }
            });
    }



    @Test
    void processFlow_WithEmptyMessage_ShouldStartProcessWithEmptyData() throws InterruptedException {
        // Given
        String correlationId = "empty-integration-123";
        CamundaMessageDto emptyMessage = TestDataBuilder.createEmptyMessage(correlationId);

        // When
        kafkaTemplate.send(START_PROCESS_TOPIC, emptyMessage);

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                
                // Verify empty/null values
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertEquals("", requester);
                
                Object amount = runtimeService.getVariable(processInstance.getId(), "amount");
                assertEquals(0.0, amount);
            });
    }

    @Test
    void processFlow_WithMinimalMessage_ShouldStartProcessWithoutDto() throws InterruptedException {
        // Given
        String correlationId = "minimal-integration-123";
        CamundaMessageDto minimalMessage = TestDataBuilder.createMinimalMessage(correlationId);

        // When
        kafkaTemplate.send(START_PROCESS_TOPIC, minimalMessage);

        // Then
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isNotNull();
                
                // No variables should be set since dto is null
                Object requester = runtimeService.getVariable(processInstance.getId(), "requester");
                assertNull(requester);
            });
    }

    @Test
    void serviceTaskMessage_ShouldContainCorrectData() throws InterruptedException {
        // Given
        String correlationId = "service-task-data-123";
        CamundaMessageDto startMessage = TestDataBuilder.createStartProcessMessage(correlationId);

        // Create and prepare the test consumer BEFORE starting the process
        // This ensures it's ready to capture messages from the service task
        testConsumer.close(); // Close the existing consumer created in @BeforeEach
        testConsumer = createTestConsumerForTopic(SERVICE_TASK_TOPIC);
        
        // Warmup poll to establish consumer assignment  
        testConsumer.poll(Duration.ofMillis(100));
        
        // Now seek to beginning to catch all messages for this test
        testConsumer.seekToBeginning(testConsumer.assignment());
        
        // Clear any existing messages with a quick poll
        ConsumerRecords<String, CamundaMessageDto> initialRecords = testConsumer.poll(Duration.ofMillis(500));
        System.out.println("Initial poll found " + initialRecords.count() + " records - clearing them");

        // When - Start the process
        kafkaTemplate.send(START_PROCESS_TOPIC, startMessage);
        
        // Wait for process to start
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId)
                    .list();
                ProcessInstance processInstance = processInstances.isEmpty() ? null : processInstances.get(0);
                assertThat(processInstance).isWaitingAt("Activity_0zhduij");
            });

        // Complete the user task first
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(processInstances).hasSize(1);
                ProcessInstance processInstance = processInstances.get(0);
                
                List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(processInstance.getId())
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(tasks).hasSize(1);
                Task task = tasks.get(0);
                taskService.complete(task.getId(), Map.of("processed", true));
            });

        // Process should automatically trigger service task after user task completion
        
        // Give some time for the message to be processed and service task to execute
        Thread.sleep(2000);

        // Then - Verify service task message is published correctly
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                ConsumerRecords<String, CamundaMessageDto> records = testConsumer.poll(Duration.ofSeconds(2));
                System.out.println("Polling found " + records.count() + " records");
                
                // Print all records for debugging
                for (ConsumerRecord<String, CamundaMessageDto> record : records) {
                    System.out.println("Found record: key=" + record.key() + 
                                     ", topic=" + record.topic() + 
                                     ", partition=" + record.partition() + 
                                     ", offset=" + record.offset() +
                                     ", value=" + record.value());
                    if (record.value() != null) {
                        System.out.println("  correlationId=" + record.value().getCorrelationId());
                    }
                }
                
                assertFalse(records.isEmpty(), "Expected service task message but got no records");
                
                // Find the record with matching correlation ID
                CamundaMessageDto serviceTaskMessage = null;
                for (ConsumerRecord<String, CamundaMessageDto> record : records) {
                    if (record.value() != null && correlationId.equals(record.value().getCorrelationId())) {
                        serviceTaskMessage = record.value();
                        break;
                    }
                }
                
                assertNotNull(serviceTaskMessage, "Expected service task message with correlation ID: " + correlationId);
                assertEquals(correlationId, serviceTaskMessage.getCorrelationId());
                assertNotNull(serviceTaskMessage.getDto());
                assertEquals("test-requester", serviceTaskMessage.getDto().getRequester());
                assertEquals(1000.0, serviceTaskMessage.getDto().getAmount());
                assertTrue(serviceTaskMessage.getDto().getPreApproved());
                assertTrue(serviceTaskMessage.getDto().getProcessed()); // Should be true since we completed the task with processed=true
            });
            
        // Finally, verify that the process completes after service task
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> activeInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId)
                    .active()
                    .list();
                    
                if (!activeInstances.isEmpty()) {
                    fail("Process is still active, expected completed");
                } else {
                    assertTrue(true); // Process completed successfully
                }
            });
            
        // Manual cleanup for this test - don't let @AfterEach cleanup consume our test message
        // Close the test consumer so it commits the offset and we don't lose the message
        testConsumer.close();
        testConsumer = null; // Prevent @AfterEach from trying to close it again
        
        // Do minimal cleanup
        cleanupProcessData();
    }

    @Test
    void multipleProcessInstances_ShouldHandleIndependently() throws InterruptedException {
        // Given
        String correlationId1 = "multi-process-1";
        String correlationId2 = "multi-process-2";
        
        CamundaMessageDto message1 = TestDataBuilder.createStartProcessMessage(correlationId1);
        CamundaMessageDto message2 = TestDataBuilder.createHighValueMessage(correlationId2);

        // When - Start both processes
        kafkaTemplate.send(START_PROCESS_TOPIC, message1);
        kafkaTemplate.send(START_PROCESS_TOPIC, message2);

        // Then - Verify both processes started independently
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances1 = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId1)
                    .list();
                ProcessInstance process1 = processInstances1.isEmpty() ? null : processInstances1.get(0);
                List<ProcessInstance> processInstances2 = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId2)
                    .list();
                ProcessInstance process2 = processInstances2.isEmpty() ? null : processInstances2.get(0);
                
                assertThat(process1).isNotNull();
                assertThat(process2).isNotNull();
                assertNotEquals(process1.getId(), process2.getId());
                
                // Verify different variables
                Object amount1 = runtimeService.getVariable(process1.getId(), "amount");
                Object amount2 = runtimeService.getVariable(process2.getId(), "amount");
                assertEquals(1000.0, amount1);
                assertEquals(50000.0, amount2);
            });

        // Complete the user task for first process
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> processInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionKey("KafkaDemo")
                    .processInstanceBusinessKey(correlationId1)
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(processInstances).hasSize(1);
                ProcessInstance processInstance = processInstances.get(0);
                
                List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(processInstance.getId())
                    .active()
                    .list();
                org.assertj.core.api.Assertions.assertThat(tasks).hasSize(1);
                Task task = tasks.get(0);
                taskService.complete(task.getId(), Map.of("processed", true));
            });

        // First process should complete automatically after user task completion

        // Verify only first process completes
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .untilAsserted(() -> {
                List<ProcessInstance> activeInstances1 = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId1)
                    .active()
                    .list();
                List<ProcessInstance> instances2 = runtimeService.createProcessInstanceQuery()
                    .processInstanceBusinessKey(correlationId2)
                    .list();
                ProcessInstance instance2 = instances2.isEmpty() ? null : instances2.get(0);
                
                // First process should be completed (no active instances)
                if (!activeInstances1.isEmpty()) {
                    fail("First process is still active, expected completed");
                }
                
                // Second process should still be waiting at user task
                assertThat(instance2).isNotNull().isWaitingAt("Activity_0zhduij");
            });
    }
} 