package com.albertsons.edis.kafkaya.api;

import io.swagger.annotations.Api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/kafka")
@Api(value = "kafka")
public class Kafka {

    @GET
    @Path("/help")
    @Produces({MediaType.TEXT_PLAIN})
    public Response help() throws Exception {
        return Response
                .ok("xxx")
                .build();
    }



}
