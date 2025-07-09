package org.camunda.bpm.demo.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.Job;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import java.time.Duration;
import java.util.ArrayList;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.camunda.bpm.demo.dto.CamundaMessageDto;
import java.util.Collections;
import java.util.Arrays;

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    controlledShutdown = false,
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:0",
        "port=0"
    },
    topics = {
        "start-process-message-topic",
        "service-task-message-topic",
        "order-process-message-topic"
    }
)
@DirtiesContext
public abstract class BaseIntegrationTest {

    @Autowired
    protected EmbeddedKafkaBroker embeddedKafka;
    
    @Autowired
    protected RuntimeService runtimeService;
    
    @Autowired
    protected TaskService taskService;
    
    @Autowired
    protected RepositoryService repositoryService;
    
    @Autowired
    protected HistoryService historyService;
    
    @Autowired
    protected ManagementService managementService;

    /**
     * Configures the embedded Kafka broker properties for the Spring application context.
     * This method overrides the custom properties used by the application to ensure
     * they point to the embedded broker instead of the default localhost:9092.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Override custom properties used by KafkaProducerConfig and KafkaConsumerConfig
        registry.add("kafka.bootstrap-address", () -> {
            // This will be replaced by the actual embedded broker address after it starts
            return System.getProperty("spring.embedded.kafka.brokers", "localhost:9092");
        });
        
        // Also override standard Spring Boot properties for consistency
        registry.add("spring.kafka.bootstrap-servers", () -> {
            return System.getProperty("spring.embedded.kafka.brokers", "localhost:9092");
        });
        
        registry.add("spring.kafka.consumer.bootstrap-servers", () -> {
            return System.getProperty("spring.embedded.kafka.brokers", "localhost:9092");
        });
        
        registry.add("spring.kafka.producer.bootstrap-servers", () -> {
            return System.getProperty("spring.embedded.kafka.brokers", "localhost:9092");
        });
    }

    protected static final String START_PROCESS_TOPIC = "start-process-message-topic";
    protected static final String SERVICE_TASK_TOPIC = "service-task-message-topic";
    protected static final String ORDER_PROCESS_TOPIC = "order-process-message-topic";
    
    protected static final long TIMEOUT_SECONDS = 10;
    
    // Generate unique consumer group for each test run
    protected final String baseTestConsumerGroup = "testGroup-" + UUID.randomUUID().toString();
    
    /**
     * Creates a unique consumer group for each test method to prevent cross-contamination
     */
    protected String getUniqueConsumerGroup() {
        // Include test class and method name for better traceability
        String testClass = this.getClass().getSimpleName();
        String testMethod = Thread.currentThread().getStackTrace()[2].getMethodName();
        return baseTestConsumerGroup + "-" + testClass + "-" + testMethod + "-" + System.currentTimeMillis();
    }

    /**
     * Creates a Kafka consumer for tests with unique consumer group
     */
    protected Consumer<String, CamundaMessageDto> createTestConsumer() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, getUniqueConsumerGroup());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "org.camunda.bpm.demo.dto");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // Start from latest instead of earliest
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        consumerProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        consumerProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        
        return new KafkaConsumer<>(consumerProps);
    }
    
    /**
     * Creates a Kafka consumer for tests with specific topics subscribed and waits for partition assignment
     */
    protected Consumer<String, CamundaMessageDto> createTestConsumerForTopic(String topic) {
        Consumer<String, CamundaMessageDto> consumer = createTestConsumer();
        consumer.subscribe(Collections.singletonList(topic));
        
        // Wait for partition assignment to complete
        waitForPartitionAssignment(consumer);
        return consumer;
    }
    
    /**
     * Wait for consumer to be assigned partitions before proceeding
     */
    private void waitForPartitionAssignment(Consumer<String, CamundaMessageDto> consumer) {
        long timeout = System.currentTimeMillis() + 5000; // 5 second timeout
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < timeout) {
            consumer.poll(Duration.ofMillis(100));
        }
        if (consumer.assignment().isEmpty()) {
            throw new RuntimeException("Consumer failed to get partition assignment within timeout");
        }
    }
    
    /**
     * Clean up process instances and tasks before each test to ensure clean state
     */
    @BeforeEach
    void cleanupBeforeTest() {
        // Aggressive cleanup sequence to ensure no interference from previous tests
        cleanupProcessData();
        
        // NOTE: Disabling aggressive Kafka cleanup in @BeforeEach to prevent race conditions
        // with integration tests. Cleanup will run in @AfterEach instead.
        
        waitForProcessEngineStabilization();
    }
    
    /**
     * Clean up process instances and tasks after each test
     */
    @AfterEach
    void cleanupAfterTest() {
        // Let Kafka consumers finish processing before cleanup
        waitForKafkaConsumersToFinish();
        
        // Clean up process data
        cleanupProcessData();
        
        // Always run aggressive Kafka cleanup after test to ensure clean state for next test
        // Remove the method name check since we're only running this after tests complete
        cleanupKafkaTopicsAfterTest();
    }
    
    /**
     * Wait for process engine to stabilize after cleanup
     */
    private void waitForProcessEngineStabilization() {
        try {
            // Give the process engine time to process any remaining operations
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for stabilization", e);
        }
    }
    
    /**
     * Wait for Kafka consumers to finish processing current messages
     */
    private void waitForKafkaConsumersToFinish() {
        try {
            // Give consumers time to finish processing any in-flight messages
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for consumers", e);
        }
    }
    
    /**
     * Comprehensive cleanup of process data
     */
    protected void cleanupProcessData() {
        try {
            // Clean up jobs first to prevent interference
            managementService.createJobQuery()
                .list()
                .forEach(job -> {
                    try {
                        managementService.deleteJob(job.getId());
                    } catch (Exception e) {
                        // Ignore errors during cleanup
                    }
                });
            
            // Clean up all running process instances (including suspended ones)
            runtimeService.createProcessInstanceQuery()
                .list()
                .forEach(processInstance -> {
                    try {
                        runtimeService.deleteProcessInstance(processInstance.getId(), "Test cleanup", true, true);
                    } catch (Exception e) {
                        // Ignore errors during cleanup
                    }
                });
            
            // Clean up any remaining executions
            runtimeService.createExecutionQuery()
                .list()
                .forEach(execution -> {
                    try {
                        if (!execution.getProcessInstanceId().equals(execution.getId())) {
                            // Only delete child executions, not process instances
                            runtimeService.deleteProcessInstance(execution.getProcessInstanceId(), "Test cleanup", true, true);
                        }
                    } catch (Exception e) {
                        // Ignore errors during cleanup
                    }
                });
            
            // Clean up any remaining tasks (including completed ones)
            taskService.createTaskQuery()
                .list()
                .forEach(task -> {
                    try {
                        taskService.deleteTask(task.getId(), true);
                    } catch (Exception e) {
                        // Ignore errors during cleanup
                    }
                });
            
            // Clean up historic process instances
            try {
                historyService.createHistoricProcessInstanceQuery()
                    .list()
                    .forEach(historicProcessInstance -> {
                        try {
                            historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
                        } catch (Exception e) {
                            // Ignore errors during cleanup
                        }
                    });
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
            
            // Clean up historic task instances
            try {
                historyService.createHistoricTaskInstanceQuery()
                    .list()
                    .forEach(historicTaskInstance -> {
                        try {
                            historyService.deleteHistoricTaskInstance(historicTaskInstance.getId());
                        } catch (Exception e) {
                            // Ignore errors during cleanup
                        }
                    });
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
            
            // Clean up historic activity instances
            try {
                historyService.createHistoricActivityInstanceQuery()
                    .list()
                    .forEach(historicActivityInstance -> {
                        try {
                            // Note: Individual activity instance deletion might not be available
                            // This is covered by process instance deletion
                        } catch (Exception e) {
                            // Ignore errors during cleanup
                        }
                    });
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
            
            // Clean up historic variable instances
            try {
                historyService.createHistoricVariableInstanceQuery()
                    .list()
                    .forEach(historicVariableInstance -> {
                        try {
                            // Note: Individual variable instance deletion might not be available
                            // This is covered by process instance deletion
                        } catch (Exception e) {
                            // Ignore errors during cleanup
                        }
                    });
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
            
        } catch (Exception e) {
            // Ignore all cleanup errors to prevent test failures
        }
    }
    
    /**
     * Cleanup deployments to prevent version conflicts between tests
     * This method is more selective and only cleans up excessive deployments
     */
    private void cleanupDeployments() {
        try {
            // Only clean up if we have more than 2 deployments per process definition
            // This prevents removing the main application deployments
            Map<String, List<String>> processDefinitionDeployments = new HashMap<>();
            
            repositoryService.createProcessDefinitionQuery()
                .list()
                .forEach(processDefinition -> {
                    String key = processDefinition.getKey();
                    processDefinitionDeployments.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(processDefinition.getDeploymentId());
                });
            
            // For each process definition, keep only the latest 2 deployments
            processDefinitionDeployments.forEach((key, deploymentIds) -> {
                if (deploymentIds.size() > 2) {
                    // Sort by deployment ID (usually chronological) and remove older ones
                    deploymentIds.sort(String::compareTo);
                    List<String> toRemove = deploymentIds.subList(0, deploymentIds.size() - 2);
                    
                    toRemove.forEach(deploymentId -> {
                        try {
                            repositoryService.deleteDeployment(deploymentId, true);
                        } catch (Exception e) {
                            // Ignore errors during cleanup
                        }
                    });
                }
            });
        } catch (Exception e) {
            // Ignore all cleanup errors to prevent test failures
        }
    }

    /**
     * Creates a Kafka producer for test purposes using the embedded broker
     */
    protected <K, V> KafkaTemplate<K, V> createKafkaTemplate(EmbeddedKafkaBroker embeddedKafka, Class<V> valueType) {
        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(embeddedKafka));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        ProducerFactory<K, V> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Creates a Kafka consumer for test purposes using the embedded broker
     */
    protected <K, V> ConsumerFactory<K, V> createConsumerFactory(EmbeddedKafkaBroker embeddedKafka, Class<V> valueType) {
        Map<String, Object> consumerProps = new HashMap<>(KafkaTestUtils.consumerProps(getUniqueConsumerGroup(), "true", embeddedKafka));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        return new DefaultKafkaConsumerFactory<>(consumerProps);
    }

    /**
     * Wait for a specified duration - useful for async operations
     */
    protected void waitForAsync(long seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting", e);
        }
    }
    
    /**
     * Get a configured awaitility condition factory with standard timeout
     */
    protected ConditionFactory await() {
        return Awaitility.await()
            .atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
            .pollInterval(Duration.ofMillis(500))
            .pollDelay(Duration.ofMillis(100));
    }
    
    /**
     * Wait for a process instance to be created with the given business key
     */
    protected void waitForProcessInstanceCreation(String businessKey) {
        await().untilAsserted(() -> {
            List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .list();
            if (instances.isEmpty()) {
                throw new AssertionError("Process instance with business key " + businessKey + " not found");
            }
        });
    }
    
    /**
     * Wait for a process instance to reach a specific activity
     */
    protected void waitForProcessInstanceAtActivity(String businessKey, String activityId) {
        await().untilAsserted(() -> {
            List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .active()
                .list();
            if (instances.isEmpty()) {
                throw new AssertionError("No active process instance with business key " + businessKey);
            }
            
            ProcessInstance instance = instances.get(0);
            List<String> activeActivities = runtimeService.getActiveActivityIds(instance.getId());
            if (!activeActivities.contains(activityId)) {
                throw new AssertionError("Process instance not at activity " + activityId + ". Current activities: " + activeActivities);
            }
        });
    }
    
    /**
     * Wait for a process instance to complete
     */
    protected void waitForProcessInstanceCompletion(String businessKey) {
        await().untilAsserted(() -> {
            List<ProcessInstance> activeInstances = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .active()
                .list();
            if (!activeInstances.isEmpty()) {
                throw new AssertionError("Process instance with business key " + businessKey + " is still active");
            }
        });
    }
    
    /**
     * Wait for and complete all active tasks for a process instance
     */
    protected void waitForAndCompleteUserTasks(String businessKey, Map<String, Object> variables) {
        await().untilAsserted(() -> {
            List<ProcessInstance> instances = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .active()
                .list();
            if (instances.isEmpty()) {
                throw new AssertionError("No active process instance with business key " + businessKey);
            }
            
            ProcessInstance instance = instances.get(0);
            List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(instance.getId())
                .active()
                .list();
            
            if (tasks.isEmpty()) {
                throw new AssertionError("No active tasks found for process instance " + instance.getId());
            }
            
            // Complete all active tasks
            for (Task task : tasks) {
                taskService.complete(task.getId(), variables);
            }
        });
    }



    /**
     * More aggressive Kafka cleanup that ensures all topics are completely drained
     */
    private void cleanupKafkaTopicsAggressively() {
        // Skip cleanup if we're in a test that might have race conditions
        String testMethodName = Thread.currentThread().getStackTrace()[3].getMethodName();
        if (testMethodName.contains("Complete")) {
            System.out.println("Skipping aggressive cleanup for test: " + testMethodName);
            return;
        }
        
        // Check if there are active processes that might be waiting for messages
        try {
            long activeProcessCount = runtimeService.createProcessInstanceQuery().active().count();
            if (activeProcessCount > 0) {
                System.out.println("Skipping aggressive cleanup - " + activeProcessCount + " active processes found");
                return;
            }
        } catch (Exception e) {
            // If we can't check active processes, skip cleanup to be safe
            System.out.println("Skipping aggressive cleanup - cannot check active processes: " + e.getMessage());
            return;
        }
        
        try {
            // Create a high-priority cleanup consumer that will consume ALL messages
            String aggressiveCleanupGroupId = "aggressive-cleanup-" + UUID.randomUUID().toString();
            Map<String, Object> consumerProps = new HashMap<>();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, aggressiveCleanupGroupId);
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000); // Process many records at once
            consumerProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
            consumerProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100); // Don't wait long
            
            ConsumerFactory<String, String> aggressiveCleanupConsumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
            Consumer<String, String> aggressiveCleanupConsumer = aggressiveCleanupConsumerFactory.createConsumer();
            
            try {
                // Subscribe to all topics and consume everything
                List<String> allTopics = Arrays.asList(
                    START_PROCESS_TOPIC, 
                    SERVICE_TASK_TOPIC, 
                    ORDER_PROCESS_TOPIC
                );
                aggressiveCleanupConsumer.subscribe(allTopics);
                
                // Aggressive polling - consume everything multiple times to be sure
                int maxCleanupAttempts = 5;
                int totalConsumed = 0;
                
                for (int attempt = 0; attempt < maxCleanupAttempts; attempt++) {
                    ConsumerRecords<String, String> records = aggressiveCleanupConsumer.poll(Duration.ofMillis(500));
                    totalConsumed += records.count();
                    
                    if (records.count() == 0) {
                        // No more records, but try once more to be absolutely sure
                        ConsumerRecords<String, String> finalCheck = aggressiveCleanupConsumer.poll(Duration.ofMillis(100));
                        if (finalCheck.count() == 0) {
                            break; // Definitely clean
                        }
                        totalConsumed += finalCheck.count();
                    }
                }
                
                System.out.println("Aggressive cleanup consumed " + totalConsumed + " messages");
                
            } finally {
                aggressiveCleanupConsumer.close();
            }
            
            // Additional wait to ensure cleanup consumer doesn't interfere
            Thread.sleep(200);
            
        } catch (Exception e) {
            System.err.println("Error during aggressive Kafka cleanup: " + e.getMessage());
            // Don't fail the test due to cleanup issues
        }
    }

    /**
     * Kafka cleanup that runs after tests complete to ensure clean state for next test
     */
    private void cleanupKafkaTopicsAfterTest() {
        try {
            // Create a high-priority cleanup consumer that will consume ALL messages
            String aggressiveCleanupGroupId = "after-test-cleanup-" + UUID.randomUUID().toString();
            Map<String, Object> consumerProps = new HashMap<>();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, aggressiveCleanupGroupId);
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000); // Process many records at once
            consumerProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
            consumerProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100); // Don't wait long
            
            ConsumerFactory<String, String> aggressiveCleanupConsumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
            Consumer<String, String> aggressiveCleanupConsumer = aggressiveCleanupConsumerFactory.createConsumer();
            
            try {
                // Subscribe to all topics and consume everything
                List<String> allTopics = Arrays.asList(
                    START_PROCESS_TOPIC, 
                    SERVICE_TASK_TOPIC, 
                    ORDER_PROCESS_TOPIC
                );
                aggressiveCleanupConsumer.subscribe(allTopics);
                
                // Aggressive polling - consume everything multiple times to be sure
                int maxCleanupAttempts = 5;
                int totalConsumed = 0;
                
                for (int attempt = 0; attempt < maxCleanupAttempts; attempt++) {
                    ConsumerRecords<String, String> records = aggressiveCleanupConsumer.poll(Duration.ofMillis(500));
                    totalConsumed += records.count();
                    
                    if (records.count() == 0) {
                        // No more records, but try once more to be absolutely sure
                        ConsumerRecords<String, String> finalCheck = aggressiveCleanupConsumer.poll(Duration.ofMillis(100));
                        if (finalCheck.count() == 0) {
                            break; // Definitely clean
                        }
                        totalConsumed += finalCheck.count();
                    }
                }
                
                System.out.println("After-test cleanup consumed " + totalConsumed + " messages");
                
            } finally {
                aggressiveCleanupConsumer.close();
            }
            
            // Additional wait to ensure cleanup consumer doesn't interfere
            Thread.sleep(200);
            
        } catch (Exception e) {
            System.err.println("Error during after-test Kafka cleanup: " + e.getMessage());
            // Don't fail the test due to cleanup issues
        }
    }
} 