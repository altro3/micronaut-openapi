package io.micronaut.openapi.quarkus.api;

import io.micronaut.openapi.quarkus.api.dto.User;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseHeader;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/user")
class TestController {

    /**
     * {@summary Create post op summary.} Operation post description.
     *
     * @param user User request body
     *
     * @return created post user
     */
    @POST
    @Path("/create")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Consumes(MediaType.APPLICATION_JSON)
    public User createPost(User user) {
        user.setId(9876L);
        return user;
    }

    /**
     * {@summary Create patch op summary.} Operation patch description.
     *
     * @param user User request body
     */
    @PATCH
    @Path("/create")
    @ResponseStatus(204)
    @ResponseHeader(name = "X-Cheese", value = "Camembert")
    public void createPatch(User user) {
    }

    @GET
    @Path("/{userId}")
    @Produces(MediaType.TEXT_HTML)
    public String get(
        @PathParam("userId") String userId,
        @QueryParam("age") @DefaultValue("123") Integer age
    ) {
        return "Pong userId " + userId;
    }

    @PATCH
    @Path("/patch")
    public User patch(
        User user
    ) {
        user.setId(9876L);
        return user;
    }
}
