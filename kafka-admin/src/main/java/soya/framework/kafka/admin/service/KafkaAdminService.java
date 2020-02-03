package soya.framework.kafka.admin.service;

import soya.framework.kafka.admin.model.RecordModel;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class KafkaAdminService {

    private AdminClient adminClient;
    private KafkaProducer kafkaProducer;
    private KafkaConsumer kafkaConsumer;

    public KafkaAdminService(AdminClient adminClient, KafkaProducer kafkaProducer, KafkaConsumer kafkaConsumer) {
        this.adminClient = adminClient;
        this.kafkaProducer = kafkaProducer;
        this.kafkaConsumer = kafkaConsumer;
    }

    public Map<MetricName, ? extends Metric> metrics() {
        return adminClient.metrics();
    }

    // ================================================ Topics:
    public Set<String> topicNames() {
        Future<Set<String>> future = adminClient.listTopics().names();
        while (!future.isDone()) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
            }
        }

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public List<PartitionInfo> topic(String name) {
        return (List<PartitionInfo>) kafkaConsumer.listTopics().get(name);
    }



    public void createTopic(Set<NewTopic> newTopics) {
        adminClient.createTopics(newTopics);
    }

    public void deleteTopic(String topicName) {
        Set<String> set = new HashSet<>();
        set.add(topicName);
        adminClient.deleteTopics(set);
    }

    // ================= Producer:
    public RecordModel publish(String topic, String message) {
        ProducerRecord<String, byte[]> record = RecordModel.builder(topic).generateKey().message(message).create();
        Future<RecordMetadata> future = kafkaProducer.send(record);
        while (!future.isDone()) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            RecordMetadata metadata = future.get();

            return RecordModel.fromProducerRecord(record, metadata);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public Future<RecordMetadata> send(String topic, String key, byte[] value) {
        ProducerRecord<String, byte[]> producerRecord = new ProducerRecord(topic, value);
        return kafkaProducer.send(producerRecord);
    }



    // ================= Consumer:
    public List<ConsumerRecord<String, byte[]>> getLatestRecords(String topic, int count) {

        List<PartitionInfo> partitionInfoSet = kafkaConsumer.partitionsFor(topic);
        Collection<TopicPartition> partitions = partitionInfoSet.stream()
                .map(partitionInfo -> new TopicPartition(partitionInfo.topic(),
                        partitionInfo.partition()))
                .collect(Collectors.toList());
        kafkaConsumer.assign(partitions);

        Map<TopicPartition, Long> latestOffsets = kafkaConsumer.endOffsets(partitions);
        for (TopicPartition partition : partitions) {
            Long latestOffset = Math.max(0, latestOffsets.get(partition) - 1);
            kafkaConsumer.seek(partition, Math.max(0, latestOffset - count));
        }

        int totalCount = count * partitions.size();
        final Map<TopicPartition, List<ConsumerRecord<String, byte[]>>> rawRecords
                = partitions.stream().collect(Collectors.toMap(p -> p, p -> new ArrayList<>(count)));

        boolean moreRecords = true;
        while (rawRecords.size() < totalCount && moreRecords) {
            ConsumerRecords<String, byte[]> polled = kafkaConsumer.poll(Duration.ofMillis(200));

            moreRecords = false;
            for (TopicPartition partition : polled.partitions()) {
                List<ConsumerRecord<String, byte[]>> records = polled.records(partition);
                if (!records.isEmpty()) {
                    rawRecords.get(partition).addAll(records);
                    moreRecords = records.get(records.size() - 1).offset() < latestOffsets.get(partition) - 1;
                }
            }
        }

        return rawRecords
                .values()
                .stream()
                .flatMap(Collection::stream)
                .map(rec -> new ConsumerRecord<String, byte[]>(rec.topic(),
                        rec.partition(),
                        rec.offset(),
                        rec.timestamp(),
                        rec.timestampType(),
                        0L,
                        rec.serializedKeySize(),
                        rec.serializedValueSize(),
                        rec.key(),
                        rec.value(),
                        rec.headers(),
                        rec.leaderEpoch()))
                .collect(Collectors.toList());
    }
}
