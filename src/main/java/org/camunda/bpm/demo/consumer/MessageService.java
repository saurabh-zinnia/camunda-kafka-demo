package org.camunda.bpm.demo.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.util.VariablesUtil;
import org.camunda.bpm.engine.MismatchingMessageCorrelationException;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.rest.dto.message.MessageCorrelationResultDto;
import org.camunda.bpm.engine.runtime.MessageCorrelationBuilder;
import org.camunda.bpm.engine.runtime.MessageCorrelationResult;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final RuntimeService runtimeService;

    public MessageCorrelationResult correlateMessage(CamundaMessageDto camundaMessageDto, String messageName) {
        try {
            log.info("Consuming message {}", messageName);

            MessageCorrelationBuilder messageCorrelationBuilder = runtimeService.createMessageCorrelation(messageName);

            if (camundaMessageDto.getDto() != null) {
                Map<String, Object> variables = VariablesUtil.toVariableMap(camundaMessageDto.getDto());
                messageCorrelationBuilder.setVariables(VariablesUtil.toVariableMap(camundaMessageDto.getDto()));
            }

            // Only set business key if correlationId is not null
            if (camundaMessageDto.getCorrelationId() != null) {
                messageCorrelationBuilder.processInstanceBusinessKey(camundaMessageDto.getCorrelationId());
            }

            MessageCorrelationResult messageResult = messageCorrelationBuilder.correlateWithResult();

            String messageResultJson = new ObjectMapper().writeValueAsString(MessageCorrelationResultDto.fromMessageCorrelationResult(messageResult));

            log.info("Correlation successful. Process Instance Id: {}", messageResultJson);
            log.info("Correlation key used: {}", camundaMessageDto.getCorrelationId());

            return messageResult;
        } catch (MismatchingMessageCorrelationException e) {
            log.error("Issue when correlating the message: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unknown issue occurred", e);
        }
        return null;
    }
}
