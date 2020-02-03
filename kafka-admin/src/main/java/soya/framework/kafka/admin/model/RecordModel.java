package soya.framework.kafka.admin.model;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecordModel {
    private static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss SSS");

    private RecordType type;
    private String topic;
    private Integer partition;
    private long offset;
    private int serializedKeySize;
    private int serializedValueSize;
    private long timestamp;
    private String time;
    private String key;
    private transient byte[] value;
    private String message;
    private Map<String, String> headers;

    private RecordModel(RecordType type, String topic, Integer partition, long timestamp, String key, byte[] value,
                        Map<String, String> headers, long offset, int serializedKeySize, int serializedValueSize) {
        this.type = type;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.timestamp = timestamp;
        this.time = DATE_FORMAT.format(new Date(timestamp));
        this.serializedKeySize = serializedKeySize;
        this.serializedValueSize = serializedValueSize;
        this.key = key;
        this.value = value;

        this.message = new String(value);
        this.headers = headers;
    }

    public static RecordModel fromProducerRecord(ProducerRecord<String, byte[]> record, RecordMetadata metadata) {
        Map<String, String> headers = null;
        if (record.headers() != null) {
            headers = new LinkedHashMap<>();
            for (Header e : record.headers()) {
                headers.put(e.key(), new String(e.value()));
            }
        }

        return new RecordModel(RecordType.PRODUCER_RECORD, record.topic(), record.partition(), record.timestamp(),
                record.key(), record.value(), headers, metadata.offset(), metadata.serializedKeySize(), metadata.serializedValueSize());
    }

    public static RecordModel fromConsumerRecord(ConsumerRecord<String, byte[]> record) {
        Map<String, String> headers = null;
        if (record.headers() != null) {
            headers = new LinkedHashMap<>();
            for (Header e : record.headers()) {
                headers.put(e.key(), new String(e.value()));
            }
        }

        return new RecordModel(RecordType.CONSUMER_RECORD, record.topic(), record.partition(), record.timestamp(),
                record.key(), record.value(), headers, record.offset(), record.serializedKeySize(), record.serializedValueSize());
    }

    static enum RecordType {
        PRODUCER_RECORD, CONSUMER_RECORD;
    }

    public static Builder builder(String topic) {
        return new Builder(topic);
    }

    public static class Builder {
        private String topic;
        private Integer partition = 0;
        private Long timestamp;
        private String key;
        private byte[] value;
        private Map<String, String> headers = new HashMap<>();

        private RecordMetadata metadata;

        public Builder(String topic) {
            this.topic = topic;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder partition(int partition) {
            this.partition = partition;
            return this;
        }

        public Builder generateKey() {
            this.key = UUID.randomUUID().toString();
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder value(byte[] value) {
            this.value = value;
            return this;
        }

        public Builder message(String msg) {
            this.value = msg.getBytes();
            return this;
        }

        public Builder header(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public Builder metadata(RecordMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public ProducerRecord<String, byte[]> create() {
            RecordHeaders recordHeaders = new RecordHeaders();
            headers.entrySet().forEach(e -> {
                recordHeaders.add(new RecordHeader(e.getKey(), e.getValue().getBytes()));
            });

            return new ProducerRecord<String, byte[]>(topic, partition, key, value, recordHeaders);
        }
    }
}
