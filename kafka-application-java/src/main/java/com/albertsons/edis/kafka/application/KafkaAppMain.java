package com.albertsons.edis.kafka.application;

public class KafkaAppMain {
    public static void main(String[] args) {
        System.getProperties().entrySet().forEach(e -> {
            System.out.println(e.getKey() + ": " + e.getValue());
        });
    }


}
