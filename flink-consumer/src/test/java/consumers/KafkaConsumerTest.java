package consumers;

import com.salesforce.kafka.test.KafkaTestUtils;
import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import com.salesforce.kafka.test.listeners.PlainListener;
import org.apache.flink.shaded.guava18.com.google.common.collect.ImmutableMap;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


class TestKafkaConsumer{

    @RegisterExtension
    public static final SharedKafkaTestResource sharedKafkaTestResource = new SharedKafkaTestResource()
            .withBrokers(1)
            .withBrokerProperty("group.id", "default")
            .withBrokerProperty("server", "localhost:9094")
            .registerListener(new PlainListener().onPorts(9094));


    @Test
    public void shouldReadDataFromKafkaAndReturnDataset() throws Exception {
        String topicName = "testTopic";
        Map<String, String> articles
                = ImmutableMap.of("key1", "Value1", "key2", "Value2");


        KafkaTestUtils kafkaTestUtils = sharedKafkaTestResource.getKafkaTestUtils();
        kafkaTestUtils.createTopic(topicName, 1, (short) 1);
        KafkaProducer<String, String> kafkaProducer = kafkaTestUtils.getKafkaProducer(StringSerializer.class, StringSerializer.class);
        articles.forEach((k, v) -> kafkaProducer.send(new ProducerRecord<>(topicName, k, v)));
        StreamExecutionEnvironment environment = StreamExecutionEnvironment
                .getExecutionEnvironment();

        FlinkKafkaConsumer<String> flinkKafkaConsumer = KafkaConsumer
                .getStringConsumerForTopic(topicName, "localhost:9094", "default");

        flinkKafkaConsumer.setStartFromEarliest();
        DataStream<String> stream = environment.addSource(flinkKafkaConsumer);
        stream.print();
        CompletableFuture<Void> handle = CompletableFuture.runAsync(() -> {
            try {
                environment.execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        try {
            handle.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            handle.cancel(true); // this will interrupt the job execution thread, cancel and close the job
        }

    }


}
