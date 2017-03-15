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
import io.hawkcd.model.dto.PipelineDto;
import io.hawkcd.model.enums.*;
import io.hawkcd.services.interfaces.IMaterialDefinitionService;
import io.hawkcd.services.interfaces.IPipelineDefinitionService;
import io.hawkcd.services.interfaces.IPipelineService;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * The PipelineService @class gives access to all Pipeline runs in the system
 */
@SuppressWarnings(value = "unchecked")
public class PipelineService extends CrudService<Pipeline> implements IPipelineService {
    private static final Class CLASS_TYPE = Pipeline.class;
    private static final Logger LOGGER = Logger.getLogger(PipelineService.class.getName());

    public static Lock lock = new ReentrantLock();

    private IPipelineDefinitionService pipelineDefinitionService;
    private IMaterialDefinitionService materialDefinitionService;

    public PipelineService() {
        IDbRepository repository = DbRepositoryFactory.create(DATABASE_TYPE, CLASS_TYPE);
        super.setRepository(repository);
        super.setObjectType(CLASS_TYPE.getSimpleName());
        this.pipelineDefinitionService = new PipelineDefinitionService();
        this.materialDefinitionService = new MaterialDefinitionService();
    }

    public PipelineService(IDbRepository repository, IPipelineDefinitionService pipelineDefinitionService, IMaterialDefinitionService materialDefinitionService) {
        super.setRepository(repository);
        super.setObjectType(CLASS_TYPE.getSimpleName());
        this.pipelineDefinitionService = pipelineDefinitionService;
        this.materialDefinitionService = materialDefinitionService;
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.VIEWER)
    public ServiceResult getById(String pipelineId) {
        return super.getById(pipelineId);
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.NONE)
    public ServiceResult getAll() {
        return super.getAll();
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.OPERATOR)
    public ServiceResult add(Pipeline pipeline) {
        PipelineDefinition pipelineDefinition = (PipelineDefinition) this.pipelineDefinitionService.getById(pipeline.getPipelineDefinitionId()).getEntity();
        if (pipelineDefinition == null) {
            return super.createServiceResult(null, NotificationType.ERROR, "Pipeline definition not found.");
        }

        pipeline = PipelineBuilder.buildPipeline(pipelineDefinition);

        pipelineDefinition.setNumberOfExecutions(pipelineDefinition.getNumberOfExecutions() + 1);
        pipelineDefinition.setRevisionCount(pipelineDefinition.getRevisionCount() + 1);
        List<EnvironmentVariable> environmentVariables = pipelineDefinition.getEnvironmentVariables();
        EnvironmentVariable environmentVariable = environmentVariables.stream().filter(e -> e.getKey().equals("COUNT")).findFirst().orElse(null);

        int envAutoIncrement = Integer.parseInt(environmentVariable.getValue()) + 1;

        environmentVariable.setValue(String.valueOf(envAutoIncrement));
        environmentVariables.stream().filter(env -> env.getKey().equals(environmentVariable.getKey())).forEach(env -> env.setValue(environmentVariable.getValue()));
        pipelineDefinition.setEnvironmentVariables(environmentVariables);
        this.pipelineDefinitionService.update(pipelineDefinition);

        this.addMaterialsToPipeline(pipeline);
//        this.addStagesToPipeline(pipeline);

        return super.add(pipeline);
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.ADMIN)
    public ServiceResult update(Pipeline pipeline) {
        ServiceResult result = super.update(pipeline);

        final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
        String methodName = ste[1].getMethodName();
        String className = this.getClass().getSimpleName();

        Message message = AuthorizationFactory.getAuthorizationManager().constructAuthorizedMessage(result, className, methodName);

        MessageDispatcher.dispatchIncomingMessage(message);

        //PublisherFactory.createPublisher().publish("global",message);

        //MessageConverter.
        //Create Message
        //Extracts users from all active sessions
        //Build the user permission Map and fill in the Message class
        //if(isSingleNode){ publish } {else { sendToAllAuthorizedSEssions}}


        //AuthorizationFactory.getAuthorizationManager().

        //EndpointConnector.passResultToEndpoint(this.getClass().getSimpleName(), "update", result);

        return result;
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.ADMIN)
    public ServiceResult delete(Pipeline pipeline) {
        return super.delete(pipeline);
    }
    
    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.VIEWER)
    public ServiceResult getAllByDefinitionId(String pipelineDefinitionId) {
        ServiceResult result = this.getAll();
        List<Pipeline> pipelines = (List<Pipeline>) result.getEntity();

        List<Pipeline> filteredPipelines = pipelines
                .stream()
                .filter(p -> p.getPipelineDefinitionId().equals(pipelineDefinitionId))
                .collect(Collectors.toList());

        result.setEntity(filteredPipelines);

        return result;
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.VIEWER)
    public ServiceResult getAllNonupdatedPipelines() {
        ServiceResult result = this.getAll();
        List<Pipeline> pipelines = (List<Pipeline>) result.getEntity();

        List<Pipeline> updatedPipelines = pipelines
                .stream()
                .filter(p -> !p.areMaterialsUpdated())
                .sorted(Comparator.comparing(Pipeline::getStartTime))
                .collect(Collectors.toList());

        result.setEntity(updatedPipelines);

        return result;
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.VIEWER)
    public ServiceResult getAllUpdatedUnpreparedPipelinesInProgress() {
        ServiceResult result = this.getAll();
        List<Pipeline> pipelines = (List<Pipeline>) result.getEntity();

        List<Pipeline> updatedPipelines = pipelines
                .stream()
                .filter(p -> p.areMaterialsUpdated() && !p.isPrepared() && (p.getStatus() == PipelineStatus.IN_PROGRESS))
                .sorted(Comparator.comparing(Pipeline::getStartTime))
                .collect(Collectors.toList());

        result.setEntity(updatedPipelines);

        return result;
    }

    @Override
    public List<Pipeline> getAllPreparedPipelinesInProgress() {
        List<Pipeline> result = (List<Pipeline>) this.getAll().getEntity();
        result = result
                .stream()
                .filter(p -> p.isPrepared() && (p.getStatus() == PipelineStatus.IN_PROGRESS || p.getRerunStatus() == PipelineStatus.IN_PROGRESS))
                .sorted(Comparator.comparing(Pipeline::getStartTime))
                .collect(Collectors.toList());

        return result;
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.VIEWER)
    public ServiceResult getAllPreparedAwaitingPipelines() {
        ServiceResult result = this.getAll();
        List<Pipeline> pipelines = (List<Pipeline>) result.getEntity();

        List<Pipeline> updatedPipelines = pipelines
                .stream()
                .filter(p -> p.isPrepared() && (p.getStatus() == PipelineStatus.AWAITING || p.getRerunStatus() == PipelineStatus.AWAITING))
                .sorted(Comparator.comparing(Pipeline::getStartTime))
                .collect(Collectors.toList());

        result.setEntity(updatedPipelines);

        return result;
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.VIEWER)
    public ServiceResult getLastRun(String pipelineDefinitionId) {
        ServiceResult result = this.getAllByDefinitionId(pipelineDefinitionId);
        List<Pipeline> pipelines = (List<Pipeline>) result.getEntity();

        Pipeline lastRun = null;
        int lastExecutionId = 0;
        for (Pipeline pipeline : pipelines) {
            if (pipeline.getExecutionId() > lastExecutionId) {
                lastRun = pipeline;
                lastExecutionId = pipeline.getExecutionId();
            }
        }

        result.setEntity(lastRun);

        return result;
    }

//    @Override
//    public ServiceResult getAllPipelineHistoryDTOs(String pipelineDefinitionId) {
//        ServiceResult result = this.getAllByDefinitionId(pipelineDefinitionId);
//        List<Pipeline> pipelines = (List<Pipeline>) result.getObject();
//
//        List<PipelineDto> pipelineDtos = new ArrayList<>();
//        for (Pipeline pipeline : pipelines) {
//            PipelineDto pipelineDto = new PipelineDto();
//            pipelineDto.constructHistoryPipelineDto(pipeline);
//            pipelineDtos.add(pipelineDto);
//        }
//
//        result.setObject(pipelineDtos);
//
//        return result;
//    }

    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.NONE)
    public ServiceResult getAllPipelineHistoryDTOs(String pipelineDefinitionId, Integer numberOfPipelines) {
        return this.getAllPipelineHistoryDTOs(pipelineDefinitionId, numberOfPipelines, null);
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.NONE)
    public ServiceResult getAllPipelineHistoryDTOs(String pipelineDefinitionId, Integer numberOfPipelines, String pipelineId) {
        ServiceResult result = this.getAllByDefinitionId(pipelineDefinitionId);
        List<Pipeline> pipelines = (List<Pipeline>) result.getEntity();
        List<Pipeline> filteredPipelines = pipelines
                .stream()
                .sorted((p1, p2) -> p2.getStartTime().compareTo(p1.getStartTime()))
                .collect(Collectors.toList());

        int indexOfPipeline = this.getIndexOfPipeline(filteredPipelines, pipelineId);
        if (indexOfPipeline == -1) {
            filteredPipelines = filteredPipelines
                    .stream()
                    .limit(numberOfPipelines)
                    .collect(Collectors.toList());
        } else {
            filteredPipelines = filteredPipelines
                    .stream()
                    .skip(indexOfPipeline + 1)
                    .limit(numberOfPipelines)
                    .collect(Collectors.toList());
        }

        List<PipelineDto> pipelineDtos = new ArrayList<>();
        for (Pipeline pipeline : filteredPipelines) {
            PipelineDto pipelineDto = new PipelineDto();
            pipelineDto.constructHistoryPipelineDto(pipeline);
            pipelineDtos.add(pipelineDto);
        }

        result.setEntity(pipelineDtos);

        return result;
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.NONE)
    public ServiceResult getAllPipelineArtifactDTOs(String searchCriteria, Integer numberOfPipelines) {
        return this.getAllPipelineArtifactDTOs(searchCriteria, numberOfPipelines, "");
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.NONE)
    public ServiceResult getAllPipelineArtifactDTOs(String searchCriteria, Integer numberOfPipelines, String pipelineId) {
        ServiceResult result = this.getAll();
        List<Pipeline> pipelines = (List<Pipeline>) result.getEntity();
        List<Pipeline> filteredPipelines = pipelines
                .stream()
                .filter(p -> p.getPipelineDefinitionName().toLowerCase().contains(searchCriteria.toLowerCase()))
                .sorted((p1, p2) -> p2.getStartTime().compareTo(p1.getStartTime()))
                .collect(Collectors.toList());

        int indexOfPipeline = this.getIndexOfPipeline(filteredPipelines, pipelineId);
        if (indexOfPipeline == -1) {
            filteredPipelines = filteredPipelines
                    .stream()
                    .limit(numberOfPipelines)
                    .collect(Collectors.toList());
        } else {
            filteredPipelines = filteredPipelines
                    .stream()
                    .skip(indexOfPipeline + 1)
                    .limit(numberOfPipelines)
                    .collect(Collectors.toList());
        }

        List<PipelineDto> pipelineDtos = new ArrayList<>();
        for (Pipeline pipeline : filteredPipelines) {
            PipelineDto pipelineDto = new PipelineDto();
            boolean isScrollCall = false;
            if (pipelineId.length() > 0) {
                isScrollCall = true;
            }
            pipelineDto.constructArtifactPipelineDto(pipeline, isScrollCall);
            pipelineDtos.add(pipelineDto);
        }

        result.setEntity(pipelineDtos);

        return result;
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.ADMIN)
    public ServiceResult rerunStageWithSpecificJobs(Stage stageToRerun, ArrayList<String> jobDefinitionIds) {
        PipelineDefinition pipelineDefinition = (PipelineDefinition)
                this.pipelineDefinitionService.getById(stageToRerun.getPipelineDefinitionId()).getEntity();
        if (pipelineDefinition == null) {
            return super.createServiceResult(null, NotificationType.ERROR, "Pipeline Definition does not exist.");
        }

        Pipeline pipeline = (Pipeline) this.getById(stageToRerun.getPipelineId()).getEntity();
        if (pipeline == null) {
            return super.createServiceResult(null, NotificationType.ERROR, "Pipeline does not exist.");
        }

        StageRun stageRun = new StageRun();
        stageRun.setExecutionId(pipeline.getStageRuns().size() + 1);

        boolean stageToRerunIsSet = false;
        for (StageDefinition stageDefinition : pipelineDefinition.getStageDefinitions()) {
            if (!stageDefinition.getId().equals(stageToRerun.getStageDefinitionId()) && !stageToRerunIsSet) {
                Stage stage = PipelineBuilder.buildStage(stageDefinition, pipeline.getId());
                stage.setStatus(StageStatus.SKIPPED);
                stageRun.addStage(stage);
            } else if (!stageDefinition.getId().equals(stageToRerun.getStageDefinitionId()) && stageToRerunIsSet) {
                Stage stage = PipelineBuilder.buildStage(stageDefinition, pipeline.getId());
                stageRun.addStage(stage);
            } else if (stageDefinition.getId().equals(stageToRerun.getStageDefinitionId())) {
                Stage stage = PipelineBuilder.buildStage(stageDefinition, pipeline.getId(), jobDefinitionIds);
                stageRun.addStage(stage);
                stageToRerunIsSet = true;
            }
        }

        pipeline.getStageRuns().add(stageRun);
        pipeline.setRerunStatus(PipelineStatus.IN_PROGRESS);

        return this.update(pipeline);
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.OPERATOR)
    public ServiceResult cancelPipeline(Pipeline pipeline) {
        ServiceResult result = this.getById(pipeline.getId());
        if (result.getNotificationType() == NotificationType.ERROR) {
            return result;
        }

        Pipeline pipelineToBeCanceled = (Pipeline) result.getEntity();
        pipelineToBeCanceled.setShouldBeCanceled(true);
        pipelineToBeCanceled.setStatus(PipelineStatus.IN_PROGRESS);
        return this.update(pipelineToBeCanceled);
    }

    @Override
    @Authorization(scope = PermissionScope.PIPELINE, type = PermissionType.OPERATOR)
    public ServiceResult pausePipeline(Pipeline pipeline) {
        ServiceResult result = this.getById(pipeline.getId());
        if (result.getNotificationType() == NotificationType.ERROR) {
            return result;
        }

        Pipeline pipelineToBePaused = (Pipeline) result.getEntity();
        if (pipelineToBePaused.getStatus() == PipelineStatus.IN_PROGRESS || pipelineToBePaused.getRerunStatus() == PipelineStatus.IN_PROGRESS) {
            pipelineToBePaused.setStatus(PipelineStatus.PAUSED);
            result.setNotificationType(NotificationType.WARNING);
            String message = String.format("Pipeline %s set to PAUSED.", pipelineToBePaused.getPipelineDefinitionName());
            result.setMessage(message);
            LOGGER.info(message);
            for (Stage stage : pipelineToBePaused.getStagesOfLastStageRun()) {
                if (stage.getStatus() == StageStatus.IN_PROGRESS) {
                    stage.setStatus(StageStatus.PAUSED);
                }
            }
        } else {
            pipelineToBePaused.setStatus(PipelineStatus.IN_PROGRESS);
            String message = String.format("Pipeline %s set to IN_PROGRESS.", pipelineToBePaused.getPipelineDefinitionName());
            LOGGER.info(message);
            for (Stage stage : pipelineToBePaused.getStagesOfLastStageRun()) {
                if (stage.getStatus() == StageStatus.PAUSED) {
                    stage.setStatus(StageStatus.IN_PROGRESS);
                    stage.setTriggeredManually(false);
                }
            }
        }

        return this.update(pipelineToBePaused);
    }

    private void addMaterialsToPipeline(Pipeline pipeline) {
        PipelineDefinition pipelineDefinition = (PipelineDefinition) this.pipelineDefinitionService.getById(pipeline.getPipelineDefinitionId()).getEntity();
        List<MaterialDefinition> materialDefinitions =
                (List<MaterialDefinition>) this.materialDefinitionService.getAllFromPipelineDefinition(pipelineDefinition.getId()).getEntity();

        List<Material> materials = new ArrayList<>();
        for (MaterialDefinition materialDefinition : materialDefinitions) {
            Material material = new Material();
            material.setPipelineDefinitionId(pipeline.getPipelineDefinitionId());
            material.setMaterialDefinition(materialDefinition);
            materials.add(material);
        }

        pipeline.setMaterials(materials);
    }

//    private void addStagesToPipeline(Pipeline pipeline) {
//        PipelineDefinition pipelineDefinition = (PipelineDefinition) this.pipelineDefinitionService.getById(pipeline.getPipelineDefinitionId()).getEntity();
//        List<StageDefinition> stageDefinitions = pipelineDefinition.getStageDefinitions();
//
//        List<Stage> stages = new ArrayList<>();
//        for (StageDefinition stageDefinition : stageDefinitions) {
//            Stage stage = new Stage();
//            stage.setPipelineId(pipeline.getId());
//            stage.setStageDefinitionId(stageDefinition.getId());
//            stage.setStageDefinitionName(stageDefinition.getName());
//            stages.add(stage);
//            this.addJobsToStage(stageDefinition, stage);
//        }
//
//        pipeline.setStages(stages);
//    }

//    private void addJobsToStage(StageDefinition stageDefinition, Stage stage) {
//        List<JobDefinition> jobDefinitions = stageDefinition.getJobDefinitions();
//
//        List<Job> jobs = new ArrayList<>();
//        for (JobDefinition jobDefinition : jobDefinitions) {
//            Job job = new Job();
//            job.setPipelineId(stage.getPipelineId());
//            job.setJobDefinitionId(jobDefinition.getId());
//            job.setJobDefinitionName(jobDefinition.getName());
//            job.setStageId(stage.getId());
//            job.setJobDefinitionName(jobDefinition.getName());
//            jobs.add(job);
//            this.addTasksToJob(jobDefinition, job);
//        }
//
//        stage.setJobs(jobs);
//    }

//    private void addTasksToJob(JobDefinition jobDefinition, Job job) {
//        List<TaskDefinition> taskDefinitions = jobDefinition.getTaskDefinitions();
//
//        List<Task> tasks = new ArrayList<>();
//        for (TaskDefinition taskDefinition : taskDefinitions) {
//
//            Task task = new Task();
//            if (taskDefinition.getType() == TaskType.FETCH_MATERIAL) {
//                FetchMaterialTask fetchMaterialTask = (FetchMaterialTask) taskDefinition;
//                task.setTaskDefinition(fetchMaterialTask);
//            }
//
//            task.setJobId(job.getId());
//            task.setStageId(job.getStageId());
//            task.setPipelineId(job.getPipelineId());
//            task.setTaskDefinition(taskDefinition);
//            task.setRunIfCondition(taskDefinition.getRunIfCondition());
//            tasks.add(task);
//        }
//
//        job.setTasks(tasks);
//    }

    private int getIndexOfPipeline(List<Pipeline> pipelines, String pipelineId) {
        int indexOfPipeline = -1;

        if (pipelineId != null) {
            int collectionSize = pipelines.size();
            for (int i = 0; i < collectionSize; i++) {
                if (pipelines.get(i).getId().equals(pipelineId)) {
                    indexOfPipeline = i;
                    break;
                }
            }
        }

        return indexOfPipeline;
    }
}
