package soya.framework.kafka.admin.configuration;

import soya.framework.kafka.admin.resource.KafkaAdminResource;
import soya.framework.kafka.admin.service.KafkaAdminService;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.ws.rs.ApplicationPath;

@Configuration
@ApplicationPath("/api")
public class KafkaAdminConfiguration extends ResourceConfig {


    @Autowired
    private Environment environment;

    @Autowired
    private KafkaProperties kafkaProperties;

    public KafkaAdminConfiguration() {
        this.register(GsonProvider.class);

        this.register(KafkaAdminResource.class);
        this.register(ApiListingResource.class);
        this.register(SwaggerSerializers.class);

        BeanConfig swaggerConfigBean = new BeanConfig();
        swaggerConfigBean.setConfigId("kafka-admin-service");
        swaggerConfigBean.setTitle("Kafka Admin Service");
        swaggerConfigBean.setSchemes(new String[]{"http"});
        swaggerConfigBean.setBasePath("/api");
        swaggerConfigBean.setResourcePackage("com.albertsons.esed.kafka.admin.resource");
        swaggerConfigBean.setPrettyPrint(true);
        swaggerConfigBean.setScan(true);
    }

    @Bean
    KafkaAdminService kafkaAdminService() {
        //KafkaAdmin kafkaAdmin = new KafkaAdmin(kafkaProperties.buildAdminProperties());

        AdminClient adminClient = AdminClient.create(kafkaProperties.buildAdminProperties());
        KafkaProducer kafkaProducer = new KafkaProducer(kafkaProperties.buildProducerProperties());
        KafkaConsumer kafkaConsumer = new KafkaConsumer(kafkaProperties.buildConsumerProperties());

        return new KafkaAdminService(adminClient, kafkaProducer, kafkaConsumer);
    }

}
