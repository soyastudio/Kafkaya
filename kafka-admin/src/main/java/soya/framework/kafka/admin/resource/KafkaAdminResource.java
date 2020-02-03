package soya.framework.kafka.admin.resource;

import soya.framework.kafka.admin.model.NewTopicModel;
import soya.framework.kafka.admin.model.RecordModel;
import soya.framework.kafka.admin.service.KafkaAdminService;
import com.google.gson.Gson;
import io.swagger.annotations.Api;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Path("/kafka")
@Api(value = "Kafka Admin Service")
public class KafkaAdminResource {
    @Autowired
    private KafkaAdminService kafkaAdminService;

    @GET
    @Path("/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response metrics() {
        Gson gson = new Gson();
        return Response.status(200).entity(gson.toJson(kafkaAdminService.metrics())).build();
    }

    @GET
    @Path("/topics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response topics() {
        try {
            List<String> list = new ArrayList<>(kafkaAdminService.topicNames());
            Collections.sort(list);
            return Response.status(200).entity(list).build();

        } catch (Exception e) {
            return Response.status(500).build();
        }
    }

    @POST
    @Path("/topic")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces(MediaType.APPLICATION_JSON)
    public Response createTopic(String json) {
        kafkaAdminService.createTopic(NewTopicModel.fromJson(json));
        return Response.status(200).build();
    }

    @GET
    @Path("/topic/{topic}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response topic(@PathParam("topic") String topic) {
        List<PartitionInfo> partitions = kafkaAdminService.topic(topic);
        return Response.status(200).entity(partitions).build();
    }

    @DELETE
    @Path("/topic")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteTopic(String json) {
        kafkaAdminService.deleteTopic(json);
        return Response.status(200).build();
    }

    @POST
    @Path("/message")
    @Produces(MediaType.APPLICATION_JSON)
    public Response publish(@HeaderParam("topic") String topic, String message) {
        RecordModel recordModel = kafkaAdminService.publish(topic, message);
        return Response.status(200).entity(recordModel).build();
    }

    @GET
    @Path("/message")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Response latestRecord(@HeaderParam("topic") String topic) {
        List<ConsumerRecord<String, byte[]>> records = kafkaAdminService.getLatestRecords(topic, 2);
        if (records.isEmpty()) {
            return Response.status(200).build();

        } else {
            String message = new String(records.get(records.size() - 1).value());
            return Response.status(200).entity(message).build();
        }
    }

    @GET
    @Path("/records")
    @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces(MediaType.APPLICATION_JSON)
    public Response latestRecords(@HeaderParam("topic") String topic, @HeaderParam("count") int count) {
        List<ConsumerRecord<String, byte[]>> records = kafkaAdminService.getLatestRecords(topic, count);
        List<RecordModel> models = new ArrayList<>();
        records.forEach(e -> {
            models.add(RecordModel.fromConsumerRecord(e));
        });
        return Response.status(200).entity(models).build();
    }
}
