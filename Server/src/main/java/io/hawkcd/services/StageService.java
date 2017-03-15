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

import io.hawkcd.core.security.Authorization;
import io.hawkcd.model.Pipeline;
import io.hawkcd.model.ServiceResult;
import io.hawkcd.model.Stage;
import io.hawkcd.model.enums.NotificationType;
import io.hawkcd.model.enums.PermissionScope;
import io.hawkcd.model.enums.PermissionType;
import io.hawkcd.services.interfaces.IPipelineService;
import io.hawkcd.services.interfaces.IStageService;

import java.util.ArrayList;
import java.util.List;

public class StageService extends CrudService<Stage> implements IStageService {
    private static final Class CLASS_TYPE = Stage.class;

    private IPipelineService pipelineService;
    private String failureMessage = "not found";
    private String successMessage = "retrieved successfully";

    public StageService() {
        this.pipelineService = new PipelineService();
        super.setObjectType(CLASS_TYPE.getSimpleName());
    }

    public StageService(IPipelineService pipelineService) {
        this.pipelineService = pipelineService;
        super.setObjectType(CLASS_TYPE.getSimpleName());
    }

    @Override
    @Authorization( scope = PermissionScope.PIPELINE, type = PermissionType.VIEWER )
    public ServiceResult getById(String stageId) {
        List<Pipeline> allPipelines = (List<Pipeline>) this.pipelineService.getAll().getEntity();
        Stage result = null;
        for (Pipeline pipeline : allPipelines) {
            List<Stage> stages = pipeline.getStagesOfLastStageRun();
            for (Stage stage : stages) {
                if (stage.getId().equals(stageId)) {
                    result = stage;

                    return super.createServiceResult(result, NotificationType.SUCCESS, this.successMessage);
                }
            }
        }
        return super.createServiceResult(result, NotificationType.ERROR, this.failureMessage);
    }

    @Override
    @Authorization( scope = PermissionScope.PIPELINE, type = PermissionType.NONE )
    public ServiceResult getAll() {
        List<Pipeline> allPipelines = (List<Pipeline>) this.pipelineService.getAll().getEntity();
        List<Stage> allStages = new ArrayList<>();

        for (Pipeline pipeline : allPipelines) {
            List<Stage> stages = pipeline.getStagesOfLastStageRun();
            allStages.addAll(stages);
        }
        return super.createServiceResultArray(allStages, NotificationType.SUCCESS, this.successMessage);
    }

//    @Override
//    @Authorization( scope = PermissionScope.PIPELINE, type = PermissionType.ADMIN )
//    public ServiceResult add(Stage stage) {
//        Pipeline pipeline = (Pipeline) this.pipelineService.getById(stage.getPipelineId()).getEntity();
//        List<Stage> stages = pipeline.getStagesOfLastStageRun();
//
//        for (Stage stageFromDb : stages) {
//            if (stageFromDb.getId().equals(stage.getId())) {
//                return super.createServiceResult(stage, NotificationType.ERROR, "already exist");
//            }
//        }
//
//        stages.add(stage);
//        pipeline.setStages(stages);
//        ServiceResult serviceResult = this.pipelineService.update(pipeline);
//
//        if (serviceResult.getNotificationType() == NotificationType.ERROR) {
//            return super.createServiceResult(stage, NotificationType.ERROR, "not created");
//        }
//        Stage result = this.extractStageFromPipeline(pipeline, stage.getId());
//
//        if (result == null) {
//            return super.createServiceResult(result, NotificationType.ERROR, "not created");
//        }
//
//        return super.createServiceResult(result, NotificationType.SUCCESS, "created successfully");
//    }

    @Override
    @Authorization( scope = PermissionScope.PIPELINE, type = PermissionType.ADMIN )
    public ServiceResult update(Stage stage) {
        Pipeline pipeline = (Pipeline) this.pipelineService.getById(stage.getPipelineId()).getEntity();
        List<Stage> stages = pipeline.getStagesOfLastStageRun();
        int stageCollectionSize = stages.size();
        boolean isPresent = false;
        for (int i = 0; i < stageCollectionSize; i++) {
            if (stages.get(i).getId().equals(stage.getId())) {
                isPresent = true;
                stages.set(i, stage);
            }
        }

        if (!isPresent) {
            return super.createServiceResult(null, NotificationType.ERROR, "not found");
        }

//        pipeline.setStages(stages);
        ServiceResult serviceResult = this.pipelineService.update(pipeline);

        if (serviceResult.getNotificationType() == NotificationType.ERROR) {
            serviceResult = super.createServiceResult((Stage) serviceResult.getEntity(), NotificationType.ERROR, "not updated");
        } else {
            serviceResult = super.createServiceResult(stage, NotificationType.SUCCESS, "updated successfully");
        }
        return serviceResult;
    }

//    @Override
//    @Authorization( scope = PermissionScope.PIPELINE, type = PermissionType.ADMIN )
//    public ServiceResult delete(Stage stage) {
//        Pipeline pipelineToUpdate = new Pipeline();
//        List<Pipeline> pipelines = (List<Pipeline>) this.pipelineService.getAll().getEntity();
//        for (Pipeline pipeline : pipelines) {
//            List<Stage> stages = pipeline.getStages();
//
//            for (Stage s : stages) {
//                if (s.getId().equals(stage.getId())) {
//                    pipelineToUpdate = pipeline;
//                }
//            }
//        }
//
//        boolean isRemoved = false;
//        ServiceResult serviceResult = null;
//        List<Stage> stages = pipelineToUpdate.getStages();
//
//        Stage s = stages
//                .stream()
//                .filter(st -> st.getId().equals(stage.getId()))
//                .findFirst()
//                .orElse(null);
//
//        if (s == null) {
//            serviceResult = super.createServiceResult(s, NotificationType.ERROR, "not found");
//        }
//
//        if (stages.size() > 1) {
//            isRemoved = stages.remove(s);
//        } else {
//            return super.createServiceResult(s, NotificationType.ERROR, "is the last Stage and cannot be deleted");
//        }
//
//        if (isRemoved) {
//            pipelineToUpdate.setStages(stages);
//            serviceResult = this.pipelineService.update(pipelineToUpdate);
//            if (serviceResult.getNotificationType() == NotificationType.SUCCESS) {
//
//                serviceResult = super.createServiceResult(s, NotificationType.SUCCESS, "deleted successfully");
//            }
//        }
//        return serviceResult;
//    }
//
//    private Stage extractStageFromPipeline(Pipeline pipeline, String stageId) {
//        Stage result = pipeline.getStages().stream()
//                .filter(stage -> stage.getId().equals(stageId))
//                .findFirst()
//                .orElse(null);
//
//        return result;
//    }
}