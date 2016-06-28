package net.hawkengine.http;

import com.google.gson.Gson;

import net.hawkengine.core.utilities.SchemaValidator;
import net.hawkengine.model.Agent;
import net.hawkengine.model.ExecTask;
import net.hawkengine.model.Job;
import net.hawkengine.model.ServiceResult;
import net.hawkengine.model.Task;
import net.hawkengine.model.payload.WorkInfo;
import net.hawkengine.services.AgentService;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/agents")
@Consumes("application/json")
@Produces("application/json")
public class AgentController {
    private AgentService agentService;
    private ServiceResult serviceResult;
    private SchemaValidator schemaValidator;
    private Gson jsonBuilder;

    public AgentController() {
        agentService = new AgentService();
        serviceResult = new ServiceResult();
        schemaValidator = new SchemaValidator();
        jsonBuilder = new Gson();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllAgents() {
        this.serviceResult = this.agentService.getAll();
        List<String> agentList = (List<String>) this.serviceResult.getObject();
        Object result = this.jsonBuilder.toJson(agentList);
        return Response.ok().entity(result).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{agentId}")
    public Response getById(@PathParam("agentId") String id) {
        this.serviceResult = this.agentService.getById(id);
        boolean hasError = this.serviceResult.hasError();
        if (hasError) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(this.serviceResult.getMessage())
                    .type(MediaType.TEXT_HTML)
                    .build();
        }
        return Response.ok().entity(this.serviceResult.getObject()).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{agentId}/work")
    public Response getWork(@PathParam("agentId") String agentId) {
        boolean isSent = false;
        if (!isSent) {
            isSent = true;

            WorkInfo work = new WorkInfo();
            work.setPipelineDefinitionName("Vlad");
            ExecTask execTask = new ExecTask();
            execTask.setCommand("/bin/bash");
            List<String> args = new ArrayList<>();
            args.add("echo \"Vlado\" >> vlad.txt");
            execTask.setArguments(args);
            Task task = new Task();
            task.setTaskDefinition(execTask);
            Job job = new Job();
            List<Task> tasks = new ArrayList<>();
            tasks.add(task);
            job.setTasks(tasks);
            work.setJob(job);
            return Response.ok().entity(work).build();
        }

        return Response.noContent().build();

//        this.serviceResult = this.agentService.getWorkInfo(agentId);
//        if (this.serviceResult.getObject().equals(false)){
//            return Response.ok()
//                    .entity(this.serviceResult.getMessage())
//                    .type(MediaType.TEXT_HTML)
//                    .build();
//        } else {
//            return Response.ok().entity(this.serviceResult.getObject()).build();
//        }
    }


    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response addAgent(Agent agent) {
        String result = schemaValidator.validate(agent);
        if (result.equals("OK")) {
            this.serviceResult = this.agentService.add(agent);
            return Response.ok().entity(this.serviceResult.getObject()).build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(result)
                    .type(MediaType.TEXT_HTML)
                    .build();
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/work")
    public Response addWork(Job job) {

        int five = 5;
        //TODO: Service operation to be implemented.

        return Response.ok().build();
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAgent(Agent agent) {
        String result = schemaValidator.validate(agent);
        if (result.equals("OK")) {
            this.serviceResult = this.agentService.update(agent);
            return Response.ok().entity(this.serviceResult.getObject()).build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(result)
                    .type(MediaType.TEXT_HTML)
                    .build();
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{agentId}")
    public Response deleteAgent(@PathParam("agentId") String agentId) {
        this.serviceResult = agentService.delete(agentId);
        boolean hasError = this.serviceResult.hasError();
        if (hasError) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(this.serviceResult.getMessage())
                    .build();
        } else {
            return Response.ok().entity(this.serviceResult.getMessage()).build();
        }
    }
}