package soya.framework.kafka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import groovy.lang.GroovyShell;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class KafkaAdmin {
    private static Logger logger = LoggerFactory.getLogger("KafkaAdmin");
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private static Properties defaultProperties;

    private static Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Properties producerProperties;
    private Properties consumerProperties;
    private Properties adminProperties;
    private Properties streamProperties;

    static {
        defaultProperties = new Properties();
        // common
        defaultProperties.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        defaultProperties.setProperty(CommonClientConfigs.CLIENT_ID_CONFIG, "test_client");
        defaultProperties.setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, CommonClientConfigs.DEFAULT_SECURITY_PROTOCOL);

        // producer
        defaultProperties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        defaultProperties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");

        // consumer
        defaultProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "test_group");
        defaultProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        defaultProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        defaultProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    private KafkaAdmin() {

        // Producer properties:
        this.producerProperties = new Properties();
        producerProperties.setProperty(ProducerConfig.CLIENT_ID_CONFIG, getProperty(ProducerConfig.CLIENT_ID_CONFIG));
        producerProperties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, getProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        producerProperties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, getProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));

        producerProperties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        producerProperties.setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));

        if ("SSL".equalsIgnoreCase(producerProperties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            producerProperties.setProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
            producerProperties.setProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));
            producerProperties.setProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
            producerProperties.setProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG));

        } else if ("SASL_SSL".equalsIgnoreCase(producerProperties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            producerProperties.setProperty(SaslConfigs.SASL_MECHANISM, getProperty(SaslConfigs.SASL_MECHANISM));
            producerProperties.setProperty(SaslConfigs.SASL_JAAS_CONFIG, getProperty(SaslConfigs.SASL_JAAS_CONFIG));
        }

        // Consumer properties:
        this.consumerProperties = new Properties();

        consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        consumerProperties.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, getProperty(ConsumerConfig.CLIENT_ID_CONFIG));
        consumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, getProperty(ConsumerConfig.GROUP_ID_CONFIG));
        consumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, getProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        consumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, getProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        consumerProperties.setProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "60000");

        consumerProperties.setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        if ("SSL".equalsIgnoreCase(consumerProperties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            consumerProperties.setProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
            consumerProperties.setProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));
            consumerProperties.setProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
            consumerProperties.setProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG));

        } else if ("SASL_SSL".equalsIgnoreCase(consumerProperties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            consumerProperties.setProperty(SaslConfigs.SASL_MECHANISM, getProperty(SaslConfigs.SASL_MECHANISM));
            consumerProperties.setProperty(SaslConfigs.SASL_JAAS_CONFIG, getProperty(SaslConfigs.SASL_JAAS_CONFIG));
        }

        // Admin properties:
        this.adminProperties = new Properties();
        adminProperties.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG));
        adminProperties.setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));

        if ("SSL".equalsIgnoreCase(adminProperties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            adminProperties.setProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
            adminProperties.setProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));
            adminProperties.setProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
            adminProperties.setProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG));

        } else if ("SASL_SSL".equalsIgnoreCase(adminProperties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            adminProperties.setProperty(SaslConfigs.SASL_MECHANISM, getProperty(SaslConfigs.SASL_MECHANISM));
            adminProperties.setProperty(SaslConfigs.SASL_JAAS_CONFIG, getProperty(SaslConfigs.SASL_JAAS_CONFIG));
        }

        // Stream properties:
        this.streamProperties = new Properties();
        streamProperties.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, getProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG));
        streamProperties.setProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));

        if ("SSL".equalsIgnoreCase(streamProperties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            streamProperties.setProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
            streamProperties.setProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));
            streamProperties.setProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
            streamProperties.setProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG));

        } else if ("SASL_SSL".equalsIgnoreCase(streamProperties.getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            streamProperties.setProperty(SaslConfigs.SASL_MECHANISM, getProperty(SaslConfigs.SASL_MECHANISM));
            streamProperties.setProperty(SaslConfigs.SASL_JAAS_CONFIG, getProperty(SaslConfigs.SASL_JAAS_CONFIG));
        }
    }

    private KafkaProducer createKafkaProducer() {
        return new KafkaProducer(producerProperties);
    }

    private KafkaConsumer createKafkaConsumer() {
        return new KafkaConsumer(consumerProperties);
    }

    private KafkaConsumer createKafkaAvroConsumer() {
        Properties  properties = new Properties(consumerProperties);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroDeserializer");
        return new KafkaConsumer(properties);
    }

    private AdminClient createAdminClient() {
        return AdminClient.create(adminProperties);
    }

    private Properties createStreamConfiguration(String applicationId) {

        Properties streamsConfiguration = new Properties(streamProperties);
        streamsConfiguration.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, streamProperties.getProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG));
        streamsConfiguration.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        streamsConfiguration.setProperty(StreamsConfig.CLIENT_ID_CONFIG, applicationId + "-client");
        streamsConfiguration.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        streamsConfiguration.setProperty(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, "" + 10 * 1000);
        streamsConfiguration.setProperty(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, "" + 0);

        return streamsConfiguration;
    }

    private static String env = "LOCAL";
    private static File home;
    private static File workspace;
    private static File log;
    private static boolean enableLog;

    private static Writer logWriter;

    private static Set<URL> urls = new HashSet<>();

    private static Properties configuration;

    private static Options options = new Options();
    private static Method[] commands;

    public static void main(String[] args) {

        File bin = new File(Paths.get("").toAbsolutePath().toString());
        home = bin.getParentFile();
        System.setProperty("kafkaya.home", home.getAbsolutePath());

        File lib = new File(home, "lib");
        if (lib.exists() && lib.isDirectory()) {
            try {

                URLClassLoader ucl = (URLClassLoader) ClassLoader.getSystemClassLoader();
                Method m = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
                m.setAccessible(true);

                for (File jarFile : lib.listFiles()) {
                    if (jarFile.isFile() && jarFile.getName().endsWith(".jar")) {
                        URL url = jarFile.toURI().toURL();
                        m.invoke(ucl, url);
                        urls.add(url);

                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        workspace = new File(home, "workspace");
        if (!workspace.exists()) {
            workspace.mkdirs();
        }

        log = new File(home, "log");
        if(!log.exists()) {
            log.mkdirs();
        }

        configuration = new Properties(defaultProperties);
        File configFile = new File(bin, "kafka-config.properties");
        if (configFile.exists()) {
            try {
                configuration.load(new FileInputStream(configFile));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Command line definition:
        options.addOption(Option.builder("a")
                .longOpt("action")
                .hasArg(true)
                .desc("Task to execute.")
                .required(false)
                .build());

        options.addOption(Option.builder("b")
                .longOpt("businessObject")
                .hasArg(true)
                .desc("Business object.")
                .required(false)
                .build());

        options.addOption(Option.builder("c")
                .longOpt("consumerTopic")
                .hasArg(true)
                .desc("Consumer Topic")
                .required(false)
                .build());

        options.addOption(Option.builder("d")
                .longOpt("dir")
                .hasArg(true)
                .desc("Directory")
                .required(false)
                .build());

        options.addOption(Option.builder("e")
                .longOpt("action")
                .hasArg(true)
                .desc("Environment, default 'local', case insensitive.")
                .required(false)
                .build());

        options.addOption(Option.builder("f")
                .longOpt("file")
                .hasArg(true)
                .desc("File related to the action specified.")
                .required(false)
                .build());

        options.addOption(Option.builder("H")
                .longOpt("header")
                .hasArg(true)
                .desc("Kafka message header")
                .required(false)
                .build());

        options.addOption(Option.builder("h")
                .longOpt("help")
                .hasArg(false)
                .desc("Help ([OPTIONAL])")
                .required(false)
                .build());

        options.addOption(Option.builder("k")
                .longOpt("key")
                .hasArg(true)
                .desc("Kafka message key")
                .required(false)
                .build());

        options.addOption(Option.builder("l")
                .longOpt("log")
                .hasArg(false)
                .desc("Enable log")
                .required(false)
                .build());

        options.addOption(Option.builder("m")
                .longOpt("msg")
                .hasArg(true)
                .desc("Message for Kafka producer")
                .required(false)
                .build());

        options.addOption(Option.builder("n")
                .longOpt("count")
                .hasArg(true)
                .desc("Number of message to print out.")
                .required(false)
                .build());

        options.addOption(Option.builder("O")
                .longOpt("Offset")
                .hasArg(true)
                .desc("Offset.")
                .required(false)
                .build());

        options.addOption(Option.builder("P")
                .longOpt("partition")
                .hasArg(true)
                .desc("Partition.")
                .required(false)
                .build());

        options.addOption(Option.builder("p")
                .longOpt("producerTopic")
                .hasArg(true)
                .desc("Producer Topic.")
                .required(false)
                .build());

        options.addOption(Option.builder("Q")
                .longOpt("Query")
                .hasArg(false)
                .desc("Find All Topics.")
                .required(false)
                .build());

        options.addOption(Option.builder("q")
                .longOpt("query")
                .hasArg(true)
                .desc("Query.")
                .required(false)
                .build());

        options.addOption(Option.builder("s")
                .longOpt("streamClass")
                .hasArg(true)
                .desc("Stream class to execute.")
                .required(false)
                .build());

        options.addOption(Option.builder("S")
                .longOpt("schema")
                .hasArg(true)
                .desc("Avro schema file name")
                .required(false)
                .build());

        options.addOption(Option.builder("t")
                .longOpt("timestamp")
                .hasArg(true)
                .desc("Timestamp for polling records after or before")
                .required(false)
                .build());

        options.addOption(Option.builder("T")
                .longOpt("timeout")
                .hasArg(true)
                .desc("Timeout ")
                .required(false)
                .build());

        options.addOption(Option.builder("x")
                .longOpt("cleanHistory")
                .hasArg(false)
                .desc("Clean log history during process.")
                .required(false)
                .build());

        // Parse command line and dispatch to method:
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);

        } catch (ParseException e) {
            log(e.getMessage());
            System.exit(1);
        }

        // Command methods:
        List<Method> cmdMethods = new ArrayList<>();
        Method[] methods = KafkaAdmin.class.getMethods();
        for (Method m : methods) {
            if (m.getAnnotation(Command.class) != null) {
                cmdMethods.add(m);
            }
        }
        Collections.sort(cmdMethods, new MethodComparator());
        commands = cmdMethods.toArray(new Method[cmdMethods.size()]);

        // Environment:
        if (cmd.hasOption("e")) {
            env = cmd.getOptionValue("e").toUpperCase();
        }
        System.setProperty("kafka." + CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, getProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
        System.setProperty("kafka." + CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
        System.setProperty("kafka." + CommonClientConfigs.CLIENT_ID_CONFIG, getProperty(CommonClientConfigs.CLIENT_ID_CONFIG));

        System.setProperty("kafka." + ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, getProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        System.setProperty("kafka." + ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, getProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));

        System.setProperty("kafka." + ConsumerConfig.GROUP_ID_CONFIG, getProperty(ConsumerConfig.GROUP_ID_CONFIG));
        System.setProperty("kafka." + ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, getProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        System.setProperty("kafka." + ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, getProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        System.setProperty("kafka." + ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));

        if ("SSL".equalsIgnoreCase(getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            System.setProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
            System.setProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));
            System.setProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
            System.setProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, getProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG));

        } else if ("SASL_SSL".equalsIgnoreCase(getProperty(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))) {
            System.setProperty(SaslConfigs.SASL_MECHANISM, getProperty(SaslConfigs.SASL_MECHANISM));
            System.setProperty(SaslConfigs.SASL_JAAS_CONFIG, getProperty(SaslConfigs.SASL_JAAS_CONFIG));
        }

        // Context:
        if(cmd.hasOption("l")) {
            enableLog = true;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyy-MM-dd-hhmm");
            File logFile = new File(log, sdf.format(new Date()) + ".log");
            if(!logFile.exists()) {
                try {
                    logFile.createNewFile();
                    logWriter = new FileWriter(logFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        executeCommandLine(cmd, new KafkaAdmin());

        if(enableLog && logWriter != null) {
            try {
                logWriter.flush();
                logWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getProperty(String propName) {
        if (System.getProperty("kafka." + propName) != null) {
            return System.getProperty("kafka." + propName);
        }

        String key = env + "." + propName;
        if (configuration.containsKey(key)) {
            return configuration.getProperty(key);
        }

        return defaultProperties.getProperty(propName);
    }

    // Global methods:
    @Command(desc = "Help",
            options = {
                    "-a help"
            },
            cases = {
                    "java -jar kafkaya.jar -h",
                    "java -jar kafkaya.jar -h -a ?",
                    "java -jar kafkaya.jar -a help",
                    "java -jar kafkaya.jar -a metrics -h"
            })
    public static void help(CommandLine cmd, KafkaAdmin kafkaAdmin) {
        if (cmd.hasOption("h")) {
            if (cmd.hasOption("a")) {
                try {
                    Method method = KafkaAdmin.class.getMethod(cmd.getOptionValue("a"), new Class[]{CommandLine.class, KafkaAdmin.class});
                    log("Usage of action '" + method.getName() + "':");
                    printCommandMethod(method);

                } catch (NoSuchMethodException e) {
                    log("Method '" + cmd.getOptionValue("a") + "' is not found.");
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < commands.length; i++) {
                        if (i > 0) {
                            builder.append(", ");
                        }
                        builder.append("'").append(commands[i].getName() + "'");
                    }
                    log("The value of option '-a' should be one of " + builder.toString());
                }
            } else {
                printHelp();
            }

        } else {
            printHelp();
        }
    }

    @Command(desc = "Get metrics information about kafka server.",
            options = {
                    "-a metrics",
                    "-e environment"
            },
            cases = {
                    "java -jar kafkaya.jar -a metrics -h",
                    "java -jar kafkaya.jar -a metrics",
                    "java -jar kafkaya.jar -a metrics -e dev"
            })
    public static void metrics(CommandLine cmd, KafkaAdmin kafkaAdmin) {
        List<Metric> metricList = new ArrayList<>(kafkaAdmin.createAdminClient().metrics().values());
        log(GSON.toJson(metricList));
    }

    @Command(desc = "List kafka topic names of special environment, default environment is local",
            options = {
                    "-a topics",
                    "-e environment",
                    "-q prefix of the topic name"
            },
            cases = {
                    "java -jar kafkaya.jar -a topics -h",
                    "java -jar kafkaya.jar -a topics",
                    "java -jar kafkaya.jar -a topics -e dev",
                    "java -jar kafkaya.jar -a topics -e dev -q ESED_C01_"
            })
    public static void topics(CommandLine cmd, KafkaAdmin kafkaAdmin) {
        String q = null;
        try {
            q = cmd.getOptionValue("q");

        } catch (Exception e) {

        }

        String query = q;

        AdminClient adminClient = kafkaAdmin.createAdminClient();
        Future<Set<String>> future = adminClient.listTopics().names();
        while (!future.isDone()) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
            }
        }

        try {
            List<String> topics = new ArrayList<>(future.get());
            Collections.sort(topics);
            topics.forEach(e -> {
                if (query == null) {
                    log(e);

                } else if (e.startsWith(query)) {
                    log(e);

                }
            });

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Command(desc = "Topic information of special environment, default environment is local",
            options = {
                    "-a topic",
                    "-c topic name",
                    "-e environment"
            },
            cases = {
                    "java -jar kafkaya.jar -h",
                    "java -jar kafkaya.jar -a topic",
                    "java -jar kafkaya.jar -a topics -e dev",
                    "java -jar kafkaya.jar -a topics -e dev -q ESED_C01_"
            })
    public static void topic(CommandLine cmd, KafkaAdmin kafkaAdmin) {
        if (!cmd.hasOption("c")) {
            throw new IllegalArgumentException("Please specify topic name using command line argument '-c'");
        }

        String topicName = cmd.getOptionValue("c");
        KafkaConsumer<String, byte[]> kafkaConsumer = kafkaAdmin.createKafkaConsumer();

        List<PartitionInfo> partitionInfoSet = kafkaConsumer.partitionsFor(topicName);
        Collection<PartitionStatus> partitionStatuses = partitionInfoSet.stream()
                .map(partitionInfo -> new PartitionStatus(partitionInfo))
                .collect(Collectors.toList());

        Collection<TopicPartition> partitions = partitionStatuses.stream()
                .map(e -> e.topicPartition)
                .collect(Collectors.toList());
        kafkaConsumer.assign(partitions);

        Map<TopicPartition, Long> beginOffsets = kafkaConsumer.beginningOffsets(partitions);
        Map<TopicPartition, Long> endOffsets = kafkaConsumer.endOffsets(partitions);

        partitionStatuses.forEach(e -> {
            if (beginOffsets.containsKey(e.topicPartition)) {
                e.offset.begin = beginOffsets.get(e.topicPartition);
            }

            if (endOffsets.containsKey(e.topicPartition)) {
                e.offset.end = endOffsets.get(e.topicPartition);
            }

        });

        log(GSON.toJson(partitionStatuses));
    }

    @Command(desc = "List consumer groups of specified environment.",
            options = {
                    "-a groups",
                    "-e environment",
                    "-q query"
            },
            cases = {
                    "java -jar kafkaya.jar -h",
                    "java -jar kafkaya.jar -a groups",
                    "java -jar kafkaya.jar -a groups -e dev",
                    "java -jar kafkaya.jar -a groups -q xxx -e qa"
            })
    public static void groups(CommandLine cmd, KafkaAdmin kafkaAdmin) throws ExecutionException, InterruptedException {
        ListConsumerGroupsResult result = kafkaAdmin.createAdminClient().listConsumerGroups();
        long timestamp = System.currentTimeMillis();
        KafkaFuture<Collection<ConsumerGroupListing>> future = result.all();
        while (!future.isDone()) {
            if (System.currentTimeMillis() - timestamp > 150000) {
                throw new RuntimeException("Fail to get consumerGroups in 150s.");
            }

            Thread.sleep(100l);
        }

        Collection<ConsumerGroupListing> results = future.get();
        List<ConsumerGroupListing> list = new ArrayList<>();
        if(cmd.hasOption("q")) {
            String prefix = cmd.getOptionValue("q");
            results.forEach(e -> {
                if(e.groupId().startsWith(prefix)) {
                    list.add(e);
                }
            });

        } else {
            list.addAll(results);
        }

        log(GSON.toJson(list));
    }

    @Command(desc = "Publish a message to a topic and then consume the latest message from another topic afterwards.",
            options = {
                    "-a pubAndSub",
                    "-b business object name, if provided, options '-p', '-c', '-f' will be read from the bod.properties file.",
                    "-c consumer topic name",
                    "-p producer topic name",
                    "-f message file name",
                    "-k key for producer message",
                    "-H headers of the producer message",
                    "-e environment",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a pubAndSub -h",
                    "java -jar kafkaya.jar -a pubAndSub -p PRODUCER_TOPIC -c CONSUMER_TOPIC -f bo/xxx/input.json -H [a=A, b=B] -e dev -T 5000",
                    "java -jar kafkaya.jar -a pubAndSub -b BOD_NAME -e dev",
                    "java -jar kafkaya.jar -a pubAndSub -b BOD_NAME -p PRODUCER_TOPIC_QA -e qa -T 60000"
            })
    public static void pubAndSub(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        long timestamp = System.currentTimeMillis();

        log("Producing message...");
        produce(cmd, kafkaAdmin);
        log("Message produced.");

        log("");
        log("......");
        log("");

        log("Consuming message...");

        Thread.sleep(300l);
        String topicName = null;

        if (cmd.hasOption("b")) {
            File dir = new File(workspace, cmd.getOptionValue("b"));
            File bod = new File(dir, "bod.properties");
            if (!bod.exists()) {
                log("File '" + bod + "' does not exit.");
            }

            Properties properties = new Properties();
            properties.load(new FileInputStream(bod));

            topicName = properties.containsKey(env + ".c") ? properties.getProperty(env + ".c") : properties.getProperty("c");
        }

        if (cmd.hasOption("c")) {
            topicName = cmd.getOptionValue("c");
        }

        if (topicName == null) {
            log("ERROR: Topic is not set. Please set topic name using parameter 'c'.");
            System.exit(1);
        }

        long timeout = 1000L;
        if (cmd.hasOption("T")) {
            timeout = Long.parseLong(cmd.getOptionValue("T"));
        }

        KafkaConsumer<String, byte[]> kafkaConsumer = kafkaAdmin.createKafkaConsumer();
        List<PartitionInfo> partitionInfoSet = kafkaConsumer.partitionsFor(topicName);
        Collection<TopicPartition> partitions = partitionInfoSet.stream()
                .map(partitionInfo -> new TopicPartition(partitionInfo.topic(),
                        partitionInfo.partition()))
                .collect(Collectors.toList());

        List<ConsumerRecord<String, byte[]>> results = new ArrayList<>();
        Map<TopicPartition, Long> latestOffsets = kafkaConsumer.endOffsets(partitions);
        for (TopicPartition partition : partitions) {
            if (results.isEmpty()) {
                List<TopicPartition> assignments = new ArrayList<>();
                assignments.add(partition);
                kafkaConsumer.assign(assignments);

                Long latestOffset = Math.max(0, latestOffsets.get(partition) - 1);
                kafkaConsumer.seek(partition, Math.max(0, latestOffset));
                ConsumerRecords<String, byte[]> polled = kafkaConsumer.poll(Duration.ofMillis(timeout));

                polled.forEach(rc -> {
                    if (rc.timestamp() > timestamp) {
                        results.add(0, rc);
                    }
                });

            }
        }

        if (results.isEmpty()) {
            log("No message consumed.");

        } else {
            ConsumerRecord<String, byte[]> rc = results.get(0);
            log("Timestamp: " + DATE_FORMAT.format(new Date(rc.timestamp())));
            log("Topic: " + rc.topic());
            log("Partition: " + rc.partition());
            log("Offset: " + rc.offset());
            log("Headers: " + toString(rc.headers()));
            log("Key: " + rc.key());
            log("Message: ");
            log(prettyPrint(new String(rc.value())));

            log("Message consumed.");

        }
    }

    @Command(desc = "Publish a message from a file or messages from directory to a topic ",
            options = {
                    "-a pub",
                    "-b business object name, if provided, options '-p', '-f', '-d' will be read from the bod.properties file.",
                    "-p producer topic name",
                    "-f message file name",
                    "-d directory of message files.",
                    "-k key for producer message",
                    "-H headers of the producer message",
                    "-e environment",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a pub -h",
                    "java -jar kafkaya.jar -a pub -p PRODUCER_TOPIC -f bo/xxx/input.json -H [a=A, b=B] -e dev -T 5000",
                    "java -jar kafkaya.jar -a pub -p PRODUCER_TOPIC -d bo/xxx/test -H [a=A, b=B] -e dev -T 5000",
                    "java -jar kafkaya.jar -a pub -b BOD_NAME -e dev",
                    "java -jar kafkaya.jar -a pub -b BOD_NAME -p PRODUCER_TOPIC_QA -e qa -T 60000"
            })
    public static void pub(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        produce(cmd, kafkaAdmin);
    }

    @Command(desc = "Publish a message from a file or messages from directory to a topic ",
            options = {
                    "-a publish",
                    "-b business object name, if provided, options '-p', '-f', '-d' will be read from the bod.properties file.",
                    "-p producer topic name",
                    "-f message file name",
                    "-d directory of message files.",
                    "-k key for producer message",
                    "-H headers of the producer message",
                    "-e environment",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a publish -h",
                    "java -jar kafkaya.jar -a publish -p PRODUCER_TOPIC -f bo/xxx/input.json -H [a=A, b=B] -e dev -T 5000",
                    "java -jar kafkaya.jar -a publish -p PRODUCER_TOPIC -d bo/xxx/test -H [a=A, b=B] -e dev -T 5000",
                    "java -jar kafkaya.jar -a publish -b BOD_NAME -e dev",
                    "java -jar kafkaya.jar -a publish -b BOD_NAME -p PRODUCER_TOPIC_QA -e qa -T 60000"
            })
    public static void publish(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        produce(cmd, kafkaAdmin);
    }

    @Command(desc = "Publish a message from a file or messages from directory to a topic ",
            options = {
                    "-a produce",
                    "-b business object name, if provided, options '-p', '-f', '-d' will be read from the bod.properties file.",
                    "-p producer topic name",
                    "-f message file name",
                    "-d directory of message files.",
                    "-k key for producer message",
                    "-H headers of the producer message",
                    "-e environment",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a produce -h",
                    "java -jar kafkaya.jar -a produce -p PRODUCER_TOPIC -f bo/xxx/input.json -H [a=A, b=B] -e dev -T 5000",
                    "java -jar kafkaya.jar -a produce -p PRODUCER_TOPIC -d bo/xxx/test -H [a=A, b=B] -e dev -T 5000",
                    "java -jar kafkaya.jar -a produce -b BOD_NAME -e dev",
                    "java -jar kafkaya.jar -a produce -b BOD_NAME -p PRODUCER_TOPIC_QA -e qa -T 60000"
            })
    public static RecordMetadata produce(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        String topicName = null;
        String fileName = null;

        if (cmd.hasOption("b")) {
            Properties properties = getBodConfig(cmd);
            topicName = getBodProperty("p", properties);
            fileName = getBodProperty("f", properties);
            if (fileName == null) {
                fileName = "input.json";
            }
            fileName = "workspace/" + cmd.getOptionValue("b") + "/" + fileName;
        }

        if (cmd.hasOption("p")) {
            topicName = cmd.getOptionValue("p");
        }

        if (topicName == null) {
            log("ERROR: Topic is not set. Please set topic name using parameter 'p'.");
            System.exit(1);
        }

        if (cmd.hasOption("f")) {
            fileName = cmd.getOptionValue("f");
        }

        if (fileName == null) {
            log("ERROR: Message is not set. Please set file name using parameter 'f'.");
            System.exit(1);
        }

        File file = new File(home, fileName);
        if (!file.exists()) {
            log("File does not exist: " + file.getAbsolutePath());
            System.exit(1);
        }

        byte[] msg = FileUtils.readFileToByteArray(file);
        String hs = cmd.hasOption("H") ? cmd.getOptionValue("H") : null;
        String key = cmd.hasOption("k") ? cmd.getOptionValue("k") : UUID.randomUUID().toString();

        return send(kafkaAdmin, topicName, file, hs, key);

    }

    @Command(desc = "List latest messages from kafka topic of all or special partition",
            options = {
                    "-a list",
                    "-b business object name, if provided, options '-c' will be read from the bod.properties file.",
                    "-c consumer topic name",
                    "-e environment",
                    "-P partition",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a latest -h",
                    "java -jar kafkaya.jar -a latest -c CONSUMER_TOPIC -n 500 -P 1 -e dev -T 5000",
                    "java -jar kafkaya.jar -a latest -b BOD_NAME -n 300 -e dev"
            })
    public static List<RecordInfo> latest(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        Set<RecordInfo> results = new HashSet<>();
        String topicName = null;

        if (cmd.hasOption("b")) {
            File dir = new File(workspace, cmd.getOptionValue("b"));
            File bod = new File(dir, "bod.properties");
            if (!bod.exists()) {
                log("File '" + bod + "' does not exit.");
            }

            Properties properties = new Properties();
            properties.load(new FileInputStream(bod));

            topicName = properties.containsKey(env + ".c") ? properties.getProperty(env + ".c") : properties.getProperty("c");
        }

        if (cmd.hasOption("c")) {
            topicName = cmd.getOptionValue("c");
        }

        if (topicName == null) {
            log("ERROR: Topic is not set. Please set topic name using parameter 'c'.");
            System.exit(1);
        }

        KafkaConsumer<String, byte[]> kafkaConsumer = kafkaAdmin.createKafkaConsumer();
        Collection<TopicPartition> partitions = null;
        if (cmd.hasOption("P")) {
            partitions = new ArrayList<>();
            partitions.add(new TopicPartition(topicName, Integer.parseInt(cmd.getOptionValue("P"))));

        } else {
            List<PartitionInfo> partitionInfoSet = kafkaConsumer.partitionsFor(topicName);
            partitions = partitionInfoSet.stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(),
                            partitionInfo.partition()))
                    .collect(Collectors.toList());

        }

        long num = cmd.hasOption("n") ? Long.parseLong(cmd.getOptionValue("n")) : 100;

        long timeout = 500L;
        if (cmd.hasOption("T")) {
            timeout = Long.parseLong(cmd.getOptionValue("T"));
        }

        Map<TopicPartition, Long> endOffsets = kafkaConsumer.endOffsets(partitions);

        for (TopicPartition partition : partitions) {
            List<TopicPartition> assignments = new ArrayList<>();
            assignments.add(partition);
            kafkaConsumer.assign(assignments);

            for (TopicPartition tp: assignments) {
                long endOffset = endOffsets.get(tp);
                long offset = Math.max(0, endOffset -  num);
                kafkaConsumer.seek(tp, offset);

            }

            ConsumerRecords<String, byte[]> polled = kafkaConsumer.poll(Duration.ofMillis(timeout));

            polled.forEach(rc -> {
                results.add(new RecordInfo(rc));
            });
        }

        List<RecordInfo> list = new ArrayList<>(results);
        Collections.sort(list);
        Collections.reverse(list);

        if(list.size() > num) {
            list = list.subList(0, (int)(num));
        }

        log("Total number: " + list.size());
        log("");

        log("| " + cellFormat("Timestamp", 24) + " | "
                + cellFormat("Partition", 9) + " | "
                + cellFormat("Offset", 6) + " | "
                + cellFormat("Size", 8) + " | "
                + cellFormat("Key", 64) + " | "
                + cellFormat("Headers", 40) + " |");
        log("  " + "------------------------"
                + "   " + "---------"
                + "   " + "------"
                + "   " + "--------"
                + "   " + "----------------------------------------------------------------"
                + "   " + "----------------------------------------");

        list.forEach(e -> {
            log("| " + cellFormat(e.timestamp, 24) + " | " + cellFormat("" + e.partition, 9) + " | "
                    + cellFormat("" + e.offset, 6) + " | "
                    + cellFormat("" + e.size, 8) + " | "
                    + cellFormat(e.key, 64) + " | "
                    + cellFormat(e.headers, 40) + " |");
        });

        return list;

    }

    @Command(desc = "List messages from kafka topic of all or special partition",
            options = {
                    "-a list",
                    "-b business object name, if provided, options '-c' will be read from the bod.properties file.",
                    "-c consumer topic name",
                    "-e environment",
                    "-P partition",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a list -h",
                    "java -jar kafkaya.jar -a list -c CONSUMER_TOPIC -P 1 -e dev -T 5000",
                    "java -jar kafkaya.jar -a list -b BOD_NAME -e dev"
            })
    public static List<RecordInfo> list(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        Set<RecordInfo> results = new HashSet<>();
        String topicName = null;

        if (cmd.hasOption("b")) {
            File dir = new File(workspace, cmd.getOptionValue("b"));
            File bod = new File(dir, "bod.properties");
            if (!bod.exists()) {
                log("File '" + bod + "' does not exit.");
            }

            Properties properties = new Properties();
            properties.load(new FileInputStream(bod));

            topicName = properties.containsKey(env + ".c") ? properties.getProperty(env + ".c") : properties.getProperty("c");
        }

        if (cmd.hasOption("c")) {
            topicName = cmd.getOptionValue("c");
        }

        if (topicName == null) {
            log("ERROR: Topic is not set. Please set topic name using parameter 'c'.");
            System.exit(1);
        }

        KafkaConsumer<String, byte[]> kafkaConsumer = kafkaAdmin.createKafkaConsumer();
        Collection<TopicPartition> partitions = null;
        if (cmd.hasOption("P")) {
            partitions = new ArrayList<>();
            partitions.add(new TopicPartition(topicName, Integer.parseInt(cmd.getOptionValue("P"))));

        } else {
            List<PartitionInfo> partitionInfoSet = kafkaConsumer.partitionsFor(topicName);
            partitions = partitionInfoSet.stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(),
                            partitionInfo.partition()))
                    .collect(Collectors.toList());

        }

        long timeout = 500L;
        if (cmd.hasOption("T")) {
            timeout = Long.parseLong(cmd.getOptionValue("T"));
        }

        for (TopicPartition partition : partitions) {
            List<TopicPartition> assignments = new ArrayList<>();
            assignments.add(partition);
            kafkaConsumer.assign(assignments);
            kafkaConsumer.seekToBeginning(assignments);
            ConsumerRecords<String, byte[]> polled = kafkaConsumer.poll(Duration.ofMillis(timeout));

            polled.forEach(rc -> {
                results.add(new RecordInfo(rc));
            });
        }

        List<RecordInfo> list = new ArrayList<>(results);
        Collections.sort(list);

        log("Total number: " + list.size());
        log("");

        log("| " + cellFormat("Timestamp", 24) + " | "
                + cellFormat("Partition", 9) + " | "
                + cellFormat("Offset", 6) + " | "
                + cellFormat("Size", 8) + " | "
                + cellFormat("Key", 64) + " | "
                + cellFormat("Headers", 40) + " |");
        log("  " + "------------------------"
                + "   " + "---------"
                + "   " + "------"
                + "   " + "--------"
                + "   " + "----------------------------------------------------------------"
                + "   " + "----------------------------------------");

        list.forEach(e -> {
            log("| " + cellFormat(e.timestamp, 24) + " | " + cellFormat("" + e.partition, 9) + " | "
                    + cellFormat("" + e.offset, 6) + " | "
                    + cellFormat("" + e.size, 8) + " | "
                    + cellFormat(e.key, 64) + " | "
                    + cellFormat(e.headers, 40) + " |");
        });

        return list;

    }

    @Command(desc = "List messages from kafka topic of all or special partition",
            options = {
                    "-a list",
                    "-b business object name, if provided, options '-c' will be read from the bod.properties file.",
                    "-c consumer topic name",
                    "-e environment",
                    "-P partition",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a list -h",
                    "java -jar kafkaya.jar -a list -c CONSUMER_TOPIC -P 1 -e dev -T 5000",
                    "java -jar kafkaya.jar -a list -b BOD_NAME -e dev"
            })
    public static List<RecordInfo> pollToEnd(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        Set<RecordInfo> results = new HashSet<>();
        String topicName = null;

        if (cmd.hasOption("b")) {
            File dir = new File(workspace, cmd.getOptionValue("b"));
            File bod = new File(dir, "bod.properties");
            if (!bod.exists()) {
                log("File '" + bod + "' does not exit.");
            }

            Properties properties = new Properties();
            properties.load(new FileInputStream(bod));

            topicName = properties.containsKey(env + ".c") ? properties.getProperty(env + ".c") : properties.getProperty("c");
        }

        if (cmd.hasOption("c")) {
            topicName = cmd.getOptionValue("c");
        }

        if (topicName == null) {
            log("ERROR: Topic is not set. Please set topic name using parameter 'c'.");
            System.exit(1);
        }

        KafkaConsumer<String, byte[]> kafkaConsumer = kafkaAdmin.createKafkaConsumer();
        Collection<TopicPartition> partitions = null;
        if (cmd.hasOption("P")) {
            partitions = new ArrayList<>();
            partitions.add(new TopicPartition(topicName, Integer.parseInt(cmd.getOptionValue("P"))));

        } else {
            List<PartitionInfo> partitionInfoSet = kafkaConsumer.partitionsFor(topicName);
            partitions = partitionInfoSet.stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(),
                            partitionInfo.partition()))
                    .collect(Collectors.toList());

        }

        long timeout = 500L;
        if (cmd.hasOption("T")) {
            timeout = Long.parseLong(cmd.getOptionValue("T"));
        }

        for (TopicPartition partition : partitions) {
            List<TopicPartition> assignments = new ArrayList<>();
            assignments.add(partition);
            kafkaConsumer.assign(assignments);
            kafkaConsumer.seekToBeginning(assignments);
            ConsumerRecords<String, byte[]> polled = kafkaConsumer.poll(Duration.ofMillis(timeout));

            polled.forEach(rc -> {
                results.add(new RecordInfo(rc));
            });
        }

        List<RecordInfo> list = new ArrayList<>(results);
        Collections.sort(list);

        log("Total number: " + list.size());
        log("");

        log("| " + cellFormat("Timestamp", 24) + " | "
                + cellFormat("Partition", 9) + " | "
                + cellFormat("Offset", 6) + " | "
                + cellFormat("Size", 8) + " | "
                + cellFormat("Key", 64) + " | "
                + cellFormat("Headers", 40) + " |");
        log("  " + "------------------------"
                + "   " + "---------"
                + "   " + "------"
                + "   " + "--------"
                + "   " + "----------------------------------------------------------------"
                + "   " + "----------------------------------------");

        list.forEach(e -> {
            log("| " + cellFormat(e.timestamp, 24) + " | " + cellFormat("" + e.partition, 9) + " | "
                    + cellFormat("" + e.offset, 6) + " | "
                    + cellFormat("" + e.size, 8) + " | "
                    + cellFormat(e.key, 64) + " | "
                    + cellFormat(e.headers, 40) + " |");
        });

        return list;

    }

    @Command(desc = "List messages from kafka topic of all or special partition",
            options = {
                    "-a list",
                    "-b business object name, if provided, options '-c' will be read from the bod.properties file.",
                    "-c consumer topic name",
                    "-e environment",
                    "-P partition",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a list -h",
                    "java -jar kafkaya.jar -a list -c CONSUMER_TOPIC -P 1 -e dev -T 5000",
                    "java -jar kafkaya.jar -a list -b BOD_NAME -e dev"
            })
    public static List<RecordInfo> since(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        Set<RecordInfo> results = new HashSet<>();
        String topicName = null;

        if (cmd.hasOption("b")) {
            File dir = new File(workspace, cmd.getOptionValue("b"));
            File bod = new File(dir, "bod.properties");
            if (!bod.exists()) {
                log("File '" + bod + "' does not exit.");
            }

            Properties properties = new Properties();
            properties.load(new FileInputStream(bod));

            topicName = properties.containsKey(env + ".c") ? properties.getProperty(env + ".c") : properties.getProperty("c");
        }

        if (cmd.hasOption("c")) {
            topicName = cmd.getOptionValue("c");
        }

        if (topicName == null) {
            log("ERROR: Topic is not set. Please set topic name using parameter 'c'.");
            System.exit(1);
        }

        KafkaConsumer<String, byte[]> kafkaConsumer = kafkaAdmin.createKafkaConsumer();
        Collection<TopicPartition> partitions = null;
        if (cmd.hasOption("P")) {
            partitions = new ArrayList<>();
            partitions.add(new TopicPartition(topicName, Integer.parseInt(cmd.getOptionValue("P"))));

        } else {
            List<PartitionInfo> partitionInfoSet = kafkaConsumer.partitionsFor(topicName);
            partitions = partitionInfoSet.stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(),
                            partitionInfo.partition()))
                    .collect(Collectors.toList());

        }

        long timeout = 500L;
        if (cmd.hasOption("T")) {
            timeout = Long.parseLong(cmd.getOptionValue("T"));
        }

        for (TopicPartition partition : partitions) {
            List<TopicPartition> assignments = new ArrayList<>();
            assignments.add(partition);
            kafkaConsumer.assign(assignments);
            kafkaConsumer.seekToBeginning(assignments);
            ConsumerRecords<String, byte[]> polled = kafkaConsumer.poll(Duration.ofMillis(timeout));

            polled.forEach(rc -> {
                results.add(new RecordInfo(rc));
            });
        }

        List<RecordInfo> list = new ArrayList<>(results);
        Collections.sort(list);

        log("Total number: " + list.size());
        log("");

        log("| " + cellFormat("Timestamp", 24) + " | "
                + cellFormat("Partition", 9) + " | "
                + cellFormat("Offset", 6) + " | "
                + cellFormat("Size", 8) + " | "
                + cellFormat("Key", 64) + " | "
                + cellFormat("Headers", 40) + " |");
        log("  " + "------------------------"
                + "   " + "---------"
                + "   " + "------"
                + "   " + "--------"
                + "   " + "----------------------------------------------------------------"
                + "   " + "----------------------------------------");

        list.forEach(e -> {
            log("| " + cellFormat(e.timestamp, 24) + " | " + cellFormat("" + e.partition, 9) + " | "
                    + cellFormat("" + e.offset, 6) + " | "
                    + cellFormat("" + e.size, 8) + " | "
                    + cellFormat(e.key, 64) + " | "
                    + cellFormat(e.headers, 40) + " |");
        });

        return list;

    }

    @Command(desc = "Get latest message from kafka topic of all or specified partition",
            options = {
                    "-a consume",
                    "-b business object name, if provided, options '-c' will be read from the bod.properties file.",
                    "-c consumer topic name",
                    "-e environment",
                    "-P partition",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a consume -h",
                    "java -jar kafkaya.jar -a consume -c CONSUMER_TOPIC -P 1 -O 1234 -e dev -T 5000",
                    "java -jar kafkaya.jar -a consume -b BOD_NAME -e dev"
            })
    public static ConsumerRecord<String, byte[]> consume(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        String topicName = null;

        if (cmd.hasOption("b")) {
            File dir = new File(workspace, cmd.getOptionValue("b"));
            File bod = new File(dir, "bod.properties");
            if (!bod.exists()) {
                log("File '" + bod + "' does not exit.");
            }

            Properties properties = new Properties();
            properties.load(new FileInputStream(bod));

            topicName = properties.containsKey(env + ".c") ? properties.getProperty(env + ".c") : properties.getProperty("c");
        }

        if (cmd.hasOption("c")) {
            topicName = cmd.getOptionValue("c");
        }

        if (topicName == null) {
            log("ERROR: Topic is not set. Please set topic name using parameter 'c'.");
            System.exit(1);
        }

        KafkaConsumer<String, byte[]> kafkaConsumer = kafkaAdmin.createKafkaConsumer();
        List<String> topics = new ArrayList<>();
        topics.add(topicName);

        Collection<TopicPartition> partitions = null;
        if (cmd.hasOption("P")) {
            partitions = new ArrayList<>();
            partitions.add(new TopicPartition(topicName, Integer.parseInt(cmd.getOptionValue("P"))));

        } else {
            List<PartitionInfo> partitionInfoSet = kafkaConsumer.partitionsFor(topicName);
            partitions = partitionInfoSet.stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(),
                            partitionInfo.partition()))
                    .collect(Collectors.toList());

        }

        ConsumerRecord<String, byte[]> rc = latest(kafkaConsumer, topicName, partitions);
        if (rc != null) {
            log("Timestamp: " + DATE_FORMAT.format(new Date(rc.timestamp())));
            log("Topic: " + rc.topic());
            log("Partition: " + rc.partition());
            log("Offset: " + rc.offset());
            log("Key: " + rc.key());
            log("Headers: " + toString(rc.headers()));
            log("Message: ");
            log(prettyPrint(new String(rc.value())));

        } else {
            log("Cannot get message from topic: " + topicName);
        }

        return rc;
    }

    @Command(desc = "Get latest avro message from kafka topic of all or specified partition",
            options = {
                    "-a avro",
                    "-b business object name, if provided, options '-c' will be read from the bod.properties file.",
                    "-c consumer topic name",
                    "-e environment",
                    "-P partition",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a avro -h",
                    "java -jar kafkaya.jar -a avro -c CONSUMER_TOPIC -P 1 -O 1234 -e dev -T 5000",
                    "java -jar kafkaya.jar -a avro -b BOD_NAME -e dev"
            })
    public static ConsumerRecord<String, byte[]> avro(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        String topicName = null;

        if(!cmd.hasOption("S")) {
            throw new IllegalArgumentException("Option 'S' is required for specifying schema file.");
        }

        String avsc = "avsc/" + cmd.getOptionValue("S");
        if(!avsc.endsWith(".avsc")) {
            avsc = avsc + ".avsc";
        }

        File schemaFile = new File(home, avsc);
        if(!schemaFile.exists()) {
            throw new FileNotFoundException("File not found: " + avsc);
        }

        Schema schema = new Schema.Parser().parse(schemaFile);

        if (cmd.hasOption("b")) {
            File dir = new File(workspace, cmd.getOptionValue("b"));
            File bod = new File(dir, "bod.properties");

            if (!bod.exists()) {
                log("File '" + bod + "' does not exit.");
            }

            Properties properties = new Properties();
            properties.load(new FileInputStream(bod));

            topicName = properties.containsKey(env + ".c") ? properties.getProperty(env + ".c") : properties.getProperty("c");
        }

        if (cmd.hasOption("c")) {
            topicName = cmd.getOptionValue("c");
        }

        if (topicName == null) {
            log("ERROR: Topic is not set. Please set topic name using parameter 'c'.");
            System.exit(1);
        }

        KafkaConsumer<String, byte[]> kafkaConsumer = kafkaAdmin.createKafkaConsumer();
        List<String> topics = new ArrayList<>();
        topics.add(topicName);

        Collection<TopicPartition> partitions = null;
        if (cmd.hasOption("P")) {
            partitions = new ArrayList<>();
            partitions.add(new TopicPartition(topicName, Integer.parseInt(cmd.getOptionValue("P"))));

        } else {
            List<PartitionInfo> partitionInfoSet = kafkaConsumer.partitionsFor(topicName);
            partitions = partitionInfoSet.stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(),
                            partitionInfo.partition()))
                    .collect(Collectors.toList());

        }

        ConsumerRecord<String, byte[]> rc = latest(kafkaConsumer, topicName, partitions);
        if (rc != null) {

            byte[] data = rc.value();
            GenericRecord record = read(data, schema);

            log("Timestamp: " + DATE_FORMAT.format(new Date(rc.timestamp())));
            log("Topic: " + rc.topic());
            log("Partition: " + rc.partition());
            log("Offset: " + rc.offset());
            log("Key: " + rc.key());
            log("Headers: " + toString(rc.headers()));
            log("Message: ");
            log(prettyPrint(record.toString()));

        } else {
            log("Cannot get message from topic: " + topicName);
        }

        return rc;
    }

    private static GenericRecord read(byte[] data, Schema schema) throws Exception {
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>();
        datumReader.setSchema(schema);
        Decoder decoder = DecoderFactory.get().binaryDecoder(data, null);
        return datumReader.read(null, decoder);
    }

    @Command(desc = "Get the message from kafka topic of from specified partition and offset",
            options = {
                    "-a get",
                    "-b business object name, if provided, options '-c' will be read from the bod.properties file.",
                    "-c consumer topic name",
                    "-e environment",
                    "-O Offset",
                    "-P partition",
                    "-T timeout in millisecond"
            },
            cases = {
                    "java -jar kafkaya.jar -a get -h",
                    "java -jar kafkaya.jar -a get -c CONSUMER_TOPIC -P 1 -O 1234 -e dev -T 5000",
                    "java -jar kafkaya.jar -a get -b -P 0 -O 1234 -e qa"
            })
    public static void get(CommandLine cmd, KafkaAdmin kafkaAdmin) throws Exception {
        KafkaConsumer kafkaConsumer = kafkaAdmin.createKafkaConsumer();
        String topicName = null;

        if (cmd.hasOption("b")) {
            File dir = new File(workspace, cmd.getOptionValue("b"));
            File bod = new File(dir, "bod.properties");
            if (!bod.exists()) {
                log("File '" + bod + "' does not exit.");
            }

            Properties properties = new Properties();
            properties.load(new FileInputStream(bod));

            topicName = properties.containsKey(env + ".c") ? properties.getProperty(env + ".c") : properties.getProperty("c");
        }

        if (cmd.hasOption("c")) {
            topicName = cmd.getOptionValue("c");
        }

        if (topicName == null) {
            log("ERROR: Topic is not set. Please set topic name using parameter 'c'.");
            System.exit(1);
        }

        long timeout = 1000;
        if (cmd.hasOption("T")) {
            timeout = Long.parseLong(cmd.getOptionValue("T"));
        }

        ConsumerRecord<String, byte[]> record = null;
        if (cmd.hasOption("P") && cmd.hasOption("O")) {
            record = consume(kafkaConsumer, topicName, Integer.parseInt(cmd.getOptionValue("P")), Long.parseLong(cmd.getOptionValue("O")), timeout);

        } else {
            consume(cmd, kafkaAdmin);
        }

        if (record != null) {
            log("Timestamp: " + DATE_FORMAT.format(new Date(record.timestamp())));
            log("Topic: " + record.topic());
            log("Partition: " + record.partition());
            log("Offset: " + record.offset());
            log("Key: " + record.key());
            log("Headers: " + toString(record.headers()));
            log("Message: ");
            log(prettyPrint(new String(record.value())));

        } else {
            log("Cannot get message from topic: " + topicName);
        }
    }

    @Command(desc = "Run java application in jar files",
            options = {
                    "-a application",
                    "-e environment",
                    "-f jar files, separated by ';', if not specified, find main class from lib",
                    "-s main class name"
            },
            cases = {
                    "java -jar kafkaya.jar -a application -h",
                    "java -jar kafkaya.jar -a application -f stream/app.jar -s com.some.KafkaAppMain -e qa"
            })
    public static void application(CommandLine cmd, KafkaAdmin kafkaAdmin) {
        if (!cmd.hasOption("s")) {
            log("Main class for Kafka application need to be specified using argument 's'");
            System.exit(1);
        }

        try {
            if (cmd.hasOption("f")) {
                URLClassLoader ucl = (URLClassLoader) ClassLoader.getSystemClassLoader();
                Method m = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
                m.setAccessible(true);

                String f = cmd.hasOption("f") ? cmd.getOptionValue("f") : null;
                String[] files = f.split(";");
                for (String fileName : files) {
                    if (fileName.endsWith(".jar")) {
                        File jar = new File(home, fileName);
                        URL url = jar.toURI().toURL();
                        if (!urls.contains(url)) {
                            m.invoke(ucl, url);
                        }
                    }
                }
            }

            Class clazz = Class.forName(cmd.getOptionValue("s"));

            Method method = clazz.getMethod("main", new Class[]{String[].class});
            method.invoke(null, new Object[]{new String[0]});

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            System.exit(0);
        }
    }

    @Command(desc = "Run groovy script",
            options = {
                    "-a groovy",
                    "-e environment",
                    "-f the groovy script file"
            },
            cases = {
                    "java -jar kafkaya.jar -a application -h",
                    "java -jar kafkaya.jar -a application -f stream/app.jar -s com.some.KafkaAppMain -e qa"
            })
    public static void groovy(CommandLine cmd, KafkaAdmin kafkaAdmin) {
        GroovyShell shell = new GroovyShell();
        if (cmd.hasOption("f")) {
            try {
                shell.parse(new File(home, cmd.getOptionValue("f")))
                        .invokeMethod(cmd.getOptionValue("t"), new Object[]{kafkaAdmin.createStreamConfiguration("GroovyApplication")});

            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        String script = "return 'hello world'";

        Object result = shell.evaluate(script);

        System.out.println(result);
    }

    // Utility methods:
    private static void executeCommandLine(CommandLine cmd, KafkaAdmin kafkaAdmin) {
        long timestamp = System.currentTimeMillis();
        String action = "help";
        if (cmd.hasOption("h")) {
            action = "help";

        } else if (cmd.hasOption("a")) {
            action = cmd.getOptionValue("a");

        } else {
            action = getDefaultAction(cmd);
        }

        if (action == null) {
            log("Cannot determine default action, please specify using '-c'");
        }

        log("############### Start executing '" + action + "' on environment '" + env + "' at " + DATE_FORMAT.format(new Date()) +
                " ###############");
        log("");

        try {
            Method method = KafkaAdmin.class.getMethod(action, new Class[]{CommandLine.class, KafkaAdmin.class});
            method.invoke(null, new Object[]{cmd, kafkaAdmin});

            log("");
            log("-------------- Execution succeeded, using " + (System.currentTimeMillis() - timestamp) + "ms ---------------");
            log("");

        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log(e.getMessage());
            System.exit(1);
            log("");
            log("-------------- Execution failed at " + DATE_FORMAT.format(new Date()) + " ---------------");
        }
    }

    private static String getDefaultAction(CommandLine cmd) {
        String action = "help";

        if (cmd.hasOption("b")) {
            Properties properties = getBodConfig(cmd);
            if (properties.containsKey("p") && properties.containsKey("c")
                    || cmd.hasOption("p") && properties.containsKey("c")
                    || properties.containsKey("p") && cmd.hasOption("c")) {
                action = "pubAndSub";

            } else if (properties.containsKey("p")) {
                action = "produce";

            } else if (properties.containsKey("c")) {
                action = "latest";

            }

        } else if (cmd.hasOption("p")) {
            if (cmd.hasOption("c")) {
                action = "pubAndSub";

            } else {
                action = "produce";
            }

        } else if (cmd.hasOption("c")) {
            action = "latest";

        }

        return action;
    }

    private static void printHelp() {
        log("Options Details:");
        options.getOptions().forEach(o -> {
            printOption(o);
        });

        log("");
        log("Command Details:");
        for (Method e : commands) {
            printCommandMethod(e);
        }
    }

    private static void log(String msg) {
        System.out.println(msg);
        if(enableLog && logWriter != null) {
            try {
                logWriter.write(msg);
                logWriter.write("\n");
            } catch (IOException e) {

            }
        }
    }

    private static RecordMetadata send(KafkaAdmin kafkaAdmin, String topicName, File file, String hs, String key) throws Exception {

        long timestamp = System.currentTimeMillis();
        if (!file.exists()) {
            log("File does not exist: " + file.getAbsolutePath());
            System.exit(1);
        }
        byte[] msg = FileUtils.readFileToByteArray(file);

        KafkaProducer kafkaProducer = kafkaAdmin.createKafkaProducer();
        RecordBuilder builder = new RecordBuilder(topicName).key(key).value(msg);
        if (hs != null) {
            Headers headers = headers(hs);
            headers.forEach(e -> {
                builder.header(e.key(), new String(e.value()));
            });
        }
        ProducerRecord<String, byte[]> record = builder.create();

        Future<RecordMetadata> future = kafkaProducer.send(record);
        while (!future.isDone()) {
            if (System.currentTimeMillis() - timestamp > 60000) {
                throw new TimeoutException("Fail to publish message to: " + topicName + " in 60 second.");
            }

            Thread.sleep(100L);
        }

        RecordMetadata metadata = future.get();

        log("Timestamp: " + DATE_FORMAT.format(new Date(metadata.timestamp())));
        log("Topic: " + metadata.topic());
        log("Partition: " + metadata.partition());
        log("Offset: " + metadata.offset());
        log("Key: " + record.key());
        log("Headers: " + toString(record.headers()));
        log("Message: " + new String(record.value()));

        return metadata;

    }

    private static void send(KafkaAdmin kafkaAdmin, String topicName, File file, String hs, String key, Callback callback) throws Exception {

        long timestamp = System.currentTimeMillis();
        if (!file.exists()) {
            log("File does not exist: " + file.getAbsolutePath());
            System.exit(1);
        }
        byte[] msg = FileUtils.readFileToByteArray(file);

        KafkaProducer kafkaProducer = kafkaAdmin.createKafkaProducer();
        RecordBuilder builder = new RecordBuilder(topicName).key(key).value(msg);
        if (hs != null) {
            Headers headers = headers(hs);
            headers.forEach(e -> {
                builder.header(e.key(), new String(e.value()));
            });
        }
        ProducerRecord<String, byte[]> record = builder.create();

        Future<RecordMetadata> future = kafkaProducer.send(record, callback);
        while (!future.isDone()) {
            if (System.currentTimeMillis() - timestamp > 60000) {
                throw new TimeoutException("Fail to publish message to: " + topicName + " in 60 second.");
            }

            Thread.sleep(100L);
        }

        RecordMetadata metadata = future.get();

        log("Timestamp: " + DATE_FORMAT.format(new Date(metadata.timestamp())));
        log("Topic: " + metadata.topic());
        log("Partition: " + metadata.partition());
        log("Offset: " + metadata.offset());
        log("Key: " + record.key());
        log("Headers: " + toString(record.headers()));
        log("Message: " + new String(record.value()));

    }

    private static ConsumerRecord<String, byte[]> latest(KafkaConsumer kafkaConsumer, String topicName, Collection<TopicPartition> partitions) throws Exception {
        List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
        Map<TopicPartition, Long> latestOffsets = kafkaConsumer.endOffsets(partitions);
        for (TopicPartition partition : partitions) {
            List<TopicPartition> assignments = new ArrayList<>();
            assignments.add(partition);

            kafkaConsumer.assign(assignments);
            Long latestOffset = Math.max(0, latestOffsets.get(partition) - 1);
            kafkaConsumer.seek(partition, Math.max(0, latestOffset));
            ConsumerRecords<String, byte[]> polled = kafkaConsumer.poll(Duration.ofMillis(5000));

            polled.forEach(rc -> {
                records.add(rc);
            });
        }

        ConsumerRecord<String, byte[]> rc = null;
        for (ConsumerRecord<String, byte[]> record : records) {
            if (rc == null) {
                rc = record;
            } else if (record.timestamp() > rc.timestamp()) {
                rc = record;
            }
        }

        return rc;
    }

    private static ConsumerRecord<String, byte[]> consume(KafkaConsumer kafkaConsumer, String topicName, int partition, long offset, long timeout) {
        List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
        TopicPartition topicPartition = new TopicPartition(topicName, partition);

        Collection<TopicPartition> partitions = new ArrayList<>();
        partitions.add(topicPartition);
        kafkaConsumer.assign(partitions);
        kafkaConsumer.seekToBeginning(partitions);

        ConsumerRecords<String, byte[]> polled = kafkaConsumer.poll(Duration.ofMillis(timeout));
        polled.forEach(rc -> {
            if (rc.offset() == offset) {
                records.add(rc);
            }
        });

        return records.isEmpty() ? null : records.get(0);
    }

    private static Properties getBodConfig(CommandLine cmd) {
        File dir = new File(workspace, cmd.getOptionValue("b"));
        File bod = new File(dir, "bod.properties");
        if (!bod.exists()) {
            log("File '" + bod + "' does not exit.");
        }

        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(bod));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return properties;
    }

    private static String getBodProperty(String propName, Properties properties) {
        return properties.containsKey(env.toLowerCase() + "." + propName) ?
                properties.getProperty(env.toLowerCase(Locale.ROOT) + "." + propName) : properties.getProperty(propName);
    }

    private static Headers headers(String headers) {
        if (headers == null) {
            return null;
        }

        RecordHeaders recordHeaders = new RecordHeaders();
        if (headers.startsWith("[") && headers.endsWith("]")) {
            String[] arr = headers.substring(1, headers.length() - 1).split(";");
            for (String exp : arr) {
                if (exp.contains("=")) {
                    String[] kv = exp.split("=");
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    if (value.startsWith("'") && value.endsWith("'") || value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    recordHeaders.add(key, value.getBytes());
                }
            }

        } else {
            String token = base64Decode(headers);
            JsonObject jsonObject = JsonParser.parseString(token).getAsJsonObject();

            jsonObject.entrySet().forEach(e -> {
                recordHeaders.add(new RecordHeader(e.getKey(), e.getValue().getAsString().getBytes()));
            });
        }

        return recordHeaders;
    }

    private static String toString(Headers headers) {
        StringBuilder builder = new StringBuilder();
        headers.forEach(e -> {
            builder.append(e.key()).append("=").append(new String(e.value())).append(",");
        });

        String token = builder.toString();
        if (token.endsWith(",")) {
            token = token.substring(0, token.length() - 1);
        }

        return token;
    }

    private static String base64Decode(String msg) {
        return new String(Base64.getDecoder().decode(msg));
    }

    private static String prettyPrint(String msg) {
        String token = msg.trim();
        if (token.startsWith("{") && token.endsWith("}") || token.startsWith("[") && token.endsWith("]")) {
            return prettyPrintJson(msg);
        }

        try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(token)));
            return prettyPrintXml(msg);
        } catch (Exception e) {

        }

        return msg;
    }

    private static String prettyPrintJson(String json) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(JsonParser.parseString(json));
    }

    private static String prettyPrintXml(String xml) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StreamResult result = new StreamResult(new StringWriter());
        Source xmlInsetProperty = new StreamSource(new StringReader(xml));
        transformer.transform(xmlInsetProperty, result);
        return result.getWriter().toString();
    }

    private static String cellFormat(String value, int len) {
        char[] arr = value == null ? "null".toCharArray() : value.toCharArray();
        int size = arr.length;
        int length = Math.max(size, len);

        char[] result = new char[length];

        for (int i = 0; i < length; i++) {
            if (i < size) {
                result[i] = arr[i];
            } else {
                result[i] = ' ';
            }
        }
        return new String(result);
    }

    private static void printOption(Option option) {
        StringBuilder builder = new StringBuilder();
        builder.append("  -").append(option.getOpt()).append("  --").append(option.getLongOpt()).append(" :: ").append(option.getDescription());
        log(builder.toString());
    }

    private static void printCommandMethod(Method method) {
        Command command = method.getAnnotation(Command.class);
        StringBuilder builder = new StringBuilder();
        builder.append("  ").append(method.getName()).append(": ").append(command.desc()).append("\n");
        builder.append("    Options:\n");
        for (String opt : command.options()) {
            builder.append("      ").append(opt).append("\n");
        }
        builder.append("    Examples:\n");
        for (String cs : command.cases()) {
            builder.append("      >").append(cs).append("\n");
        }
        log(builder.toString());
    }

    // RecordBuilder
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Command {
        String desc() default "";

        String[] options() default {};

        String[] cases() default {};
    }

    static class MethodComparator implements Comparator<Method> {

        @Override
        public int compare(Method o1, Method o2) {
            return o1.getName().compareTo(o2.getName());
        }
    }

    private static class PartitionStatus extends PartitionInfo {
        private transient TopicPartition topicPartition;
        private OffsetInfo offset = new OffsetInfo();

        public PartitionStatus(PartitionInfo partitionInfo) {
            super(partitionInfo.topic(), partitionInfo.partition(),
                    partitionInfo.leader(), partitionInfo.replicas(), partitionInfo.inSyncReplicas(), partitionInfo.offlineReplicas());
            this.topicPartition = new TopicPartition(partitionInfo.topic(), partitionInfo.partition());
        }
    }

    private static class OffsetInfo {
        private long begin;
        private long end;
    }

    private static class RecordInfo implements Comparable<RecordInfo> {
        private ConsumerRecord<String, byte[]> record;

        private long ts;

        private int partition;
        private long offset;
        private String timestamp;
        private String key;
        private int size;
        private String headers;

        private RecordInfo(ConsumerRecord<String, byte[]> record) {
            this.ts = record.timestamp();
            this.partition = record.partition();
            this.offset = record.offset();
            this.timestamp = DATE_FORMAT.format(new Date(record.timestamp()));
            this.key = record.key();
            this.size = record.serializedValueSize();
            this.headers = record.headers() == null ? "" : KafkaAdmin.toString(record.headers());
        }

        @Override
        public int compareTo(RecordInfo o) {
            return (int) (ts - o.ts);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RecordInfo)) return false;

            RecordInfo that = (RecordInfo) o;

            if (partition != that.partition) return false;
            return offset == that.offset;
        }

        @Override
        public int hashCode() {
            int result = partition;
            result = 31 * result + (int) (offset ^ (offset >>> 32));
            return result;
        }
    }

    private static class RecordBuilder {
        private String topic;
        private Integer partition = 0;
        private Long timestamp;
        private String key;
        private byte[] value;
        private Map<String, String> headers = new HashMap<>();

        private RecordMetadata metadata;

        public RecordBuilder(String topic) {
            this.topic = topic;
        }

        public RecordBuilder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public RecordBuilder partition(int partition) {
            this.partition = partition;
            return this;
        }

        public RecordBuilder generateKey() {
            this.key = UUID.randomUUID().toString();
            return this;
        }

        public RecordBuilder key(String key) {
            this.key = key;
            return this;
        }

        public RecordBuilder value(byte[] value) {
            this.value = value;
            return this;
        }

        public RecordBuilder message(String msg) {
            this.value = msg.getBytes();
            return this;
        }

        public RecordBuilder header(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public RecordBuilder metadata(RecordMetadata metadata) {
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
