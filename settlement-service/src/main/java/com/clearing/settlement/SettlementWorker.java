package com.clearing.settlement;

import com.clearing.common.Channels;
import com.clearing.common.ClearingRules;
import com.clearing.common.SettleCommand;
import com.clearing.common.SettleEvent;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SettlementWorker {

    private final PostingRepository postings;
    private final KafkaTemplate<String, Object> kafka;

    public SettlementWorker(PostingRepository postings, KafkaTemplate<String, Object> kafka) {
        this.postings = postings;
        this.kafka = kafka;
    }

    @RabbitListener(queues = Channels.SETTLE, concurrency = "4")
    @Transactional
    public void onSettle(SettleCommand command) {
        if (postings.existsById(command.transferId())) {
            return;
        }
        if (ClearingRules.frozen(command.toName())) {
            Publish.kafka(kafka, Channels.SETTLEMENT, command.transferId(), new SettleEvent(command.transferId(), "RETURNED"));
            throw new AmqpRejectAndDontRequeueException("destination is frozen");
        }
        Publish.kafka(kafka, Channels.SETTLEMENT, command.transferId(), new SettleEvent(command.transferId(), "POSTED"));
        PostingEntity posting = new PostingEntity();
        posting.setTransferId(command.transferId());
        posting.setToAccountId(command.toAccountId());
        posting.setAmountCents(command.amountCents());
        posting.setStatus("POSTED");
        postings.save(posting);
    }
}
