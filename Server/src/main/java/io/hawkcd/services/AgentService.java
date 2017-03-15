/*
 * Copyright (C) 2016 R&D Solutions Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.hawkcd.services;

import io.hawkcd.core.Message;
import io.hawkcd.core.MessageDispatcher;
import io.hawkcd.core.security.Authorization;
import io.hawkcd.core.security.AuthorizationFactory;
import io.hawkcd.db.DbRepositoryFactory;
import io.hawkcd.db.IDbRepository;
import io.hawkcd.model.*;
import io.hawkcd.model.enums.*;
import io.hawkcd.model.payload.WorkInfo;
import io.hawkcd.services.interfaces.IAgentService;
import io.hawkcd.services.interfaces.IJobService;
import io.hawkcd.services.interfaces.IPipelineService;

import java.util.List;
import java.util.stream.Collectors;

public class AgentService extends CrudService<Agent> implements IAgentService {
    private static final Class CLASS_TYPE = Agent.class;

    private IPipelineService pipelineService;
    private IJobService jobService;

    public AgentService() {
        IDbRepository repository = DbRepositoryFactory.create(DATABASE_TYPE, CLASS_TYPE);
        super.setRepository(repository);
        super.setObjectType(CLASS_TYPE.getSimpleName());
        this.pipelineService = new PipelineService();
        this.jobService = new JobService();
    }

    public AgentService(IDbRepository repository, IPipelineService pipelineService) {
        super.setRepository(repository);
        super.setObjectType(CLASS_TYPE.getSimpleName());
        this.pipelineService = pipelineService;
    }

    @Override
    @Authorization( scope = PermissionScope.SERVER, type = PermissionType.VIEWER )
    public ServiceResult getById(String agentId) {
        return super.getById(agentId);
    }

    @Override
    @Authorization( scope = PermissionScope.SERVER, type = PermissionType.NONE )
    public ServiceResult getAll() {
        return super.getAll();
    }

    @Override
    @Authorization( scope = PermissionScope.SERVER, type = PermissionType.ADMIN )
    public ServiceResult add(Agent agent) {
        ServiceResult result = super.add(agent);
        return result;
    }

    @Override
    @Authorization( scope = PermissionScope.SERVER, type = PermissionType.ADMIN )
    public ServiceResult update(Agent agent) {
        ServiceResult result = super.update(agent);

        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        String methodName = ste[1].getMethodName();
        String className = this.getClass().getSimpleName();

        Message message = AuthorizationFactory.getAuthorizationManager().constructAuthorizedMessage(result,className,methodName);

        MessageDispatcher.dispatchIncomingMessage(message);

        return result;
    }

    @Override
    @Authorization( scope = PermissionScope.SERVER, type = PermissionType.ADMIN )
    public ServiceResult delete(Agent agent) {
        return super.delete(agent);
    }

    @Override
    public ServiceResult getAllAssignableAgents() {
        List<Agent> agents = (List<Agent>) super.getAll().getEntity();
        List<Agent> assignableAgents = agents
                .stream()
                .filter(a -> a.isConnected() && a.isEnabled() && !a.isRunning() && !a.isAssigned())
                .collect(Collectors.toList());

        ServiceResult result =
                super.createServiceResultArray(assignableAgents, NotificationType.SUCCESS, "retrieved successfully");

        return result;
    }

    public ServiceResult getWorkInfo(String agentId) {
        ServiceResult result = null;
        Agent agent = (Agent) this.getById(agentId).getEntity();
        if (agent == null) {
            result = createResult(null, NotificationType.ERROR, "This agent has no job assigned.");
        } else if (agent.isAssigned()) {
            List<Pipeline> pipelines = this.pipelineService.getAllPreparedPipelinesInProgress();
            for (Pipeline pipeline : pipelines) {
                WorkInfo workInfo = new WorkInfo();
                Stage stageInProgress = pipeline.getStagesOfLastStageRun()
                        .stream()
                        .filter(s -> s.getStatus() == StageStatus.IN_PROGRESS)
                        .findFirst()
                        .orElse(null);
                if (stageInProgress == null) {
                    continue;
                }

                Job scheduledJob = stageInProgress
                        .getJobs()
                        .stream()
                        .filter(j -> j.getStatus() == JobStatus.ASSIGNED)
                        .filter(j -> j.getAssignedAgentId().equals(agentId))
                        .findFirst()
                        .orElse(null);
                if (scheduledJob == null) {
                    continue;
                }

                workInfo.setPipelineDefinitionName(pipeline.getPipelineDefinitionName());
                workInfo.setPipelineExecutionID(pipeline.getExecutionId());
                workInfo.setStageDefinitionName(stageInProgress.getStageDefinitionName());
                workInfo.setJobDefinitionName(scheduledJob.getJobDefinitionName());
                scheduledJob.setStatus(JobStatus.RUNNING);
                workInfo.setJob(scheduledJob);
                this.jobService.update(scheduledJob);

                result = createResult(workInfo, NotificationType.SUCCESS, "WorkInfo retrieved successfully");
            }
        }

        if (result == null) {
            agent.setAssigned(false);
            this.update(agent);
            result = createResult(null, NotificationType.ERROR, "This agent has no job assigned.");
        }

        return result;
    }

    private ServiceResult createResult(Object object, NotificationType notificationType, String message) {
        ServiceResult result = new ServiceResult(object, notificationType, message);

        return result;
    }
}
