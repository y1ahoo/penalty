package nosmokepenalty;

import nosmokepenalty.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import nosmokepenalty.external.Prohibit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @Autowired
    PenaltyRepository PenaltyRepository;
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverProhibited_UpdatePenalty(@Payload Prohibited prohibited){

        if(prohibited.isMe()){
            Penalty penalty = new Penalty();
            penalty.setId(prohibited.getPenaltyId());
            PenaltyRepository.save(penalty);
        }
    }

}