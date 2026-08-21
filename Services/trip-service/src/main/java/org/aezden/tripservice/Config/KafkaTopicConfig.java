package org.aezden.tripservice.Config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic tripsTopic(){
        return TopicBuilder
                .name("trip-topic")
                .build();
    }

    @Bean
    public NewTopic matchingTopic(){
        return TopicBuilder
                .name("matching-topic")
                .build();
    }
}
