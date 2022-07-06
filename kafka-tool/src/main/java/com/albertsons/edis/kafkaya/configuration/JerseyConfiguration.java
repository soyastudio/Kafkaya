package com.albertsons.edis.kafkaya.configuration;

import com.albertsons.edis.kafkaya.api.Kafka;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import io.swagger.models.Swagger;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

import javax.ws.rs.ApplicationPath;

@Configuration
@ApplicationPath("/api")
public class JerseyConfiguration extends ResourceConfig {

    public JerseyConfiguration() {
        register(GsonMessageBodyHandler.class);
        register(MultiPartFeature.class);

        register(Kafka.class);

        swaggerConfig();
    }


    private Swagger swaggerConfig() {
        this.register(ApiListingResource.class);
        this.register(SwaggerSerializers.class);

        BeanConfig swaggerConfigBean = new BeanConfig();
        swaggerConfigBean.setConfigId("kafka");
        swaggerConfigBean.setTitle("Kafka Tool");
        swaggerConfigBean.setSchemes(new String[]{"http", "https"});
        swaggerConfigBean.setBasePath("/api");
        swaggerConfigBean.setResourcePackage("com.albertsons.edis.kafkaya.api");
        swaggerConfigBean.setPrettyPrint(true);
        swaggerConfigBean.setScan(true);

        return swaggerConfigBean.getSwagger();
    }

}
