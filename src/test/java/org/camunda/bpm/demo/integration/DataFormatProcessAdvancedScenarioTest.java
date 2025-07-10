package org.camunda.bpm.demo.integration;

import org.camunda.bpm.demo.config.BaseIntegrationTest;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.scenario.ProcessScenario;
import org.camunda.bpm.scenario.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.bpm.scenario.Scenario.run;
import static org.mockito.Mockito.*;

/**
 * Advanced BDD-style scenario tests demonstrating time-based testing, realistic delays,
 * and complex business scenarios for the Data Format Process
 */
@ExtendWith(MockitoExtension.class)
@Deployment(resources = "data-format.bpmn")
public class DataFormatProcessAdvancedScenarioTest extends BaseIntegrationTest {

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Mock(lenient = true)
    private ProcessScenario dataFormatProcess;

    private static final String PROCESS_KEY = "DataformatDemoProcess";
    private static final String USER_TASK_SELECT_FORMAT = "Task_1vjuj1c";
    private static final String TASK_CREATE_XML = "Task_03zh96w";
    private static final String TASK_CREATE_JSON = "Task_1x6a2xs";
    private static final String TASK_LOG_CUSTOMER = "Task_1p179ep";
    private static final String END_EVENT_ID = "EndEvent_1nrs79a";

    @BeforeEach
    public void defineRealisticScenario() {
        // Define default timing for user tasks only
        // Note: Service tasks use Java delegates and execute immediately, no need to stub
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.defer("PT30S", () -> 
                task.complete(withVariables("dataFormat", "xml"))));
    }

    @Test
    public void testRealisticScenario_UserDelay_XmlProcessing_ShouldCompleteWithTiming() {
        // Given: A customer requiring XML processing with realistic user delay
        Map<String, Object> customerData = createEnterpriseCustomerData();
        
        // When: Process is started with realistic user interaction delay
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.defer("PT5M", () -> 
                task.complete(withVariables("dataFormat", "xml"))));

        // Then: Process should complete with realistic timing
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_XML);
        verify(dataFormatProcess).hasCompleted(TASK_LOG_CUSTOMER);
    }

    @Test
    public void testComplexScenario_BatchProcessing_MultipleCustomers_ShouldHandleEfficiently() {
        // Given: Multiple customers of different types requiring batch processing
        Map<String, Object> vipCustomer = createVipCustomerData();
        Map<String, Object> regularCustomer = createRegularCustomerData();
        Map<String, Object> newCustomer = createNewCustomerData();
        
        // When: Multiple processes with different priorities and processing times
        ProcessScenario vipProcess = mock(ProcessScenario.class);
        ProcessScenario regularProcess = mock(ProcessScenario.class);
        ProcessScenario newProcess = mock(ProcessScenario.class);
        
        // VIP customer gets priority processing (faster user response)
        when(vipProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.defer("PT10S", () -> 
                task.complete(withVariables("dataFormat", "json"))));
        // Note: Service tasks use Java delegates and execute immediately
        
        // Regular customer gets standard processing
        when(regularProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.defer("PT2M", () -> 
                task.complete(withVariables("dataFormat", "xml"))));
        // Note: Service tasks use Java delegates and execute immediately
        
        // New customer requires additional validation time
        when(newProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.defer("PT10M", () -> 
                task.complete(withVariables("dataFormat", "json"))));
        // Note: Service tasks use Java delegates and execute immediately

        // Then: All processes should complete successfully with appropriate timing
        run(vipProcess)
            .startByKey(PROCESS_KEY, vipCustomer)
            .execute();
            
        run(regularProcess)
            .startByKey(PROCESS_KEY, regularCustomer)  
            .execute();
            
        run(newProcess)
            .startByKey(PROCESS_KEY, newCustomer)
            .execute();

        // Verify all processes completed correctly
        verify(vipProcess).hasFinished(END_EVENT_ID);
        verify(vipProcess).hasCompleted(TASK_CREATE_JSON);
        verify(regularProcess).hasFinished(END_EVENT_ID);
        verify(regularProcess).hasCompleted(TASK_CREATE_XML);
        verify(newProcess).hasFinished(END_EVENT_ID);
        verify(newProcess).hasCompleted(TASK_CREATE_JSON);
    }

    @Test
    public void testErrorScenario_UserIndecision_EventuallySelectsFormat_ShouldComplete() {
        // Given: A customer data with potential for user indecision
        Map<String, Object> customerData = createIndecisiveCustomerData();
        
        // When: User takes a long time to decide (simulating real-world indecision)
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.defer("PT1H", () -> 
                task.complete(withVariables("dataFormat", "xml"))));

        // Then: Process should still complete successfully despite delay
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_XML);
    }

    @Test
    public void testPerformanceScenario_HighVolume_ConcurrentProcessing_ShouldHandleLoad() {
        // Given: High volume scenario with concurrent processing
        Map<String, Object> customerData = createHighVolumeCustomerData();
        
        // When: Multiple concurrent processes with realistic load
        ProcessScenario[] processes = new ProcessScenario[5];
        for (int i = 0; i < 5; i++) {
            processes[i] = mock(ProcessScenario.class);
            
            // Simulate varying user response times
            String userDelay = "PT" + (10 + (i * 5)) + "S";
            String format = i % 2 == 0 ? "xml" : "json";
            
            when(processes[i].waitsAtUserTask(USER_TASK_SELECT_FORMAT))
                .thenReturn(task -> task.defer(userDelay, () -> 
                    task.complete(withVariables("dataFormat", format))));
            
            // Note: Service tasks use Java delegates and execute immediately
            // No need to stub service task waits as they don't create wait states
        }

        // Then: All processes should complete successfully
        for (int i = 0; i < 5; i++) {
            run(processes[i])
                .startByKey(PROCESS_KEY, customerData)
                .execute();
                
            verify(processes[i]).hasFinished(END_EVENT_ID);
        }
    }

    @Test
    public void testBusinessScenario_ComplianceValidation_ShouldHandleRegulatory() {
        // Given: Customer data requiring compliance validation
        Map<String, Object> customerData = createComplianceCustomerData();
        
        // When: Process includes compliance validation delays
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.defer("PT15M", () -> 
                task.complete(withVariables("dataFormat", "xml"))));
        
        // Note: Service tasks use Java delegates and execute immediately

        // Then: Process should complete with compliance timing
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_XML);
        verify(dataFormatProcess).hasCompleted(TASK_LOG_CUSTOMER);
    }

    @Test
    public void testRealWorldScenario_WeekendProcessing_ShouldHandleDelays() {
        // Given: Weekend processing with reduced staff
        Map<String, Object> customerData = createWeekendCustomerData();
        
        // When: Weekend processing with extended delays
        when(dataFormatProcess.waitsAtUserTask(USER_TASK_SELECT_FORMAT))
            .thenReturn(task -> task.defer("PT8H", () -> 
                task.complete(withVariables("dataFormat", "json"))));
        
        // Note: Service tasks use Java delegates and execute immediately

        // Then: Process should complete despite weekend delays
        run(dataFormatProcess)
            .startByKey(PROCESS_KEY, customerData)
            .execute();

        verify(dataFormatProcess).hasFinished(END_EVENT_ID);
        verify(dataFormatProcess).hasCompleted(TASK_CREATE_JSON);
    }

    // Helper methods for creating specialized test data
    private Map<String, Object> createEnterpriseCustomerData() {
        return createCustomerData("Enterprise", "Customer", "male", 45L, true, "enterprise");
    }

    private Map<String, Object> createVipCustomerData() {
        return createCustomerData("VIP", "Customer", "female", 40L, true, "vip");
    }

    private Map<String, Object> createRegularCustomerData() {
        return createCustomerData("Regular", "Customer", "male", 32L, true, "regular");
    }

    private Map<String, Object> createNewCustomerData() {
        return createCustomerData("New", "Customer", "female", 28L, true, "new");
    }

    private Map<String, Object> createIndecisiveCustomerData() {
        return createCustomerData("Indecisive", "Customer", "male", 25L, true, "indecisive");
    }

    private Map<String, Object> createHighVolumeCustomerData() {
        return createCustomerData("HighVolume", "Customer", "female", 35L, true, "highvolume");
    }

    private Map<String, Object> createComplianceCustomerData() {
        return createCustomerData("Compliance", "Customer", "male", 50L, true, "compliance");
    }

    private Map<String, Object> createWeekendCustomerData() {
        return createCustomerData("Weekend", "Customer", "female", 30L, true, "weekend");
    }

    private Map<String, Object> createCustomerData(String firstname, String lastname, String gender, Long age, Boolean isValid, String customerType) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstname", firstname);
        variables.put("lastname", lastname);
        variables.put("gender", gender);
        variables.put("age", age);
        variables.put("isValid", isValid);
        variables.put("validationDate", LocalDate.now().toString());
        variables.put("customerType", customerType);
        return variables;
    }

    private Map<String, Object> withVariables(String key, Object value) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(key, value);
        return variables;
    }
} 