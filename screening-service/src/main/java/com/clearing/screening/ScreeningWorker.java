package com.clearing.screening;

import com.clearing.common.Channels;
import com.clearing.common.ClearingRules;
import com.clearing.common.ScreenCommand;
import com.clearing.common.ScreenEvent;
import com.clearing.common.SettleCommand;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ScreeningWorker {

    private final DecisionRepository decisions;
    private final KafkaTemplate<String, Object> kafka;
    private final RabbitTemplate rabbit;

    public ScreeningWorker(DecisionRepository decisions, KafkaTemplate<String, Object> kafka, RabbitTemplate rabbit) {
        this.decisions = decisions;
        this.kafka = kafka;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = Channels.SCREEN, concurrency = "4")
    @Transactional
    public void onScreen(ScreenCommand command) {
        if (decisions.existsById(command.transferId())) {
            return;
        }
        if (ClearingRules.held(command.amountCents(), command.reference())) {
            Publish.kafka(kafka, Channels.SCREENING, command.transferId(), new ScreenEvent(command.transferId(), "HELD"));
            throw new AmqpRejectAndDontRequeueException("transfer held");
        }
        Publish.kafka(kafka, Channels.SCREENING, command.transferId(), new ScreenEvent(command.transferId(), "CLEARED"));
        rabbit.convertAndSend(Channels.SETTLE, new SettleCommand(command.transferId(), command.fromAccountId(),
                command.toAccountId(), command.toName(), command.amountCents()));
        DecisionEntity decision = new DecisionEntity();
        decision.setTransferId(command.transferId());
        decision.setStatus("CLEARED");
        decisions.save(decision);
    }
}
