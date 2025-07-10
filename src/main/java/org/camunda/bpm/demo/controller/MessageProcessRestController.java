package org.camunda.bpm.demo.controller;

import lombok.RequiredArgsConstructor;
import org.camunda.bpm.demo.dto.CamundaMessageDto;
import org.camunda.bpm.demo.dto.MessageProcessDto;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/message-process")
@RequiredArgsConstructor
public class MessageProcessRestController {

    private final KafkaTemplate<String, CamundaMessageDto> kafkaTemplate;

    @PostMapping("/start")
    public void startMessageProcess(@RequestBody CamundaMessageDto camundaMessageDto){
        kafkaTemplate.send("start-process-message-topic", camundaMessageDto);
    }

    @PostMapping("/order")
    public void startOrderProcess(@RequestBody CamundaMessageDto camundaMessageDto){
        kafkaTemplate.send("order-process-message-topic", camundaMessageDto);
    }

    @PostMapping("/dataformat")
    public void startDataFormatProcess(@RequestBody CamundaMessageDto camundaMessageDto){
        kafkaTemplate.send("data-format-process-message-topic", camundaMessageDto);
    }
}
