package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

    @Value("${rabbitmq.queue.name}")
    private String queue;

    @RabbitListener(queues = "activity.queue" )
    public void processActivity(Activity activity) {
        log.info("Received activity for processing : {} " + activity.getId());
    }
}
