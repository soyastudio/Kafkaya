package soya.framework.kafka.stream;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;

import java.util.Properties;

public class SimpleStream {

    public static void execute(Properties config) {
        StreamsBuilder builder = new StreamsBuilder();

        final KStream<String, String> textLines = builder.stream("TOPIC_NAME");



        KafkaStreams streams = new KafkaStreams(builder.build(), config);
        streams.start();
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        streams.close();

    }
}
