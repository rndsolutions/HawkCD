package net.hawkengine.services;

import net.hawkengine.model.Pipeline;
import net.hawkengine.model.ServiceResult;
import net.hawkengine.model.Stage;
import net.hawkengine.services.interfaces.IPipelineService;
import net.hawkengine.services.interfaces.IStageService;

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
    public ServiceResult getById(String stageId) {
        List<Pipeline> allPipelines = (List<Pipeline>) this.pipelineService.getAll().getObject();
        Stage result = null;
        for (Pipeline pipeline : allPipelines) {
            List<Stage> stages = pipeline.getStages();
            for (Stage stage : stages) {
                if (stage.getId().equals(stageId)) {
                    result = stage;

                    return super.createServiceResult(result, false, this.successMessage);
                }
            }
        }
        return super.createServiceResult(result, true, this.failureMessage);
    }

    @Override
    public ServiceResult getAll() {
        List<Pipeline> allPipelines = (List<Pipeline>) this.pipelineService.getAll().getObject();
        List<Stage> allStages = new ArrayList<>();

        for (Pipeline pipeline : allPipelines) {
            List<Stage> stages = pipeline.getStages();
            allStages.addAll(stages);
        }
        return super.createServiceResultArray(allStages, false, this.successMessage);
    }

    @Override
    public ServiceResult add(Stage stage) {
        Pipeline pipeline = (Pipeline) this.pipelineService.getById(stage.getPipelineId()).getObject();
        List<Stage> stages = pipeline.getStages();

        for (Stage stageFromDb : stages) {
            if (stageFromDb.getId().equals(stage.getId())) {
                return super.createServiceResult(stage, true, "already exist");
            }
        }

        stages.add(stage);
        pipeline.setStages(stages);
        ServiceResult serviceResult = this.pipelineService.update(pipeline);

        if (serviceResult.hasError()) {
            return super.createServiceResult(stage, true, "not created");
        }
        Stage result = this.extractStageFromPipeline(pipeline, stage.getId());

        if (result == null) {
            return super.createServiceResult(result, true, "not created");
        }

        return super.createServiceResult(result, false, "created successfully");
    }

    @Override
    public ServiceResult update(Stage stage) {
        ServiceResult serviceResult = new ServiceResult();
        Pipeline pipeline = (Pipeline) this.pipelineService.getById(stage.getPipelineId()).getObject();
        List<Stage> stages = pipeline.getStages();
        int stageCollectionSize = stages.size();
        boolean isPresent = false;
        for (int i = 0; i < stageCollectionSize; i++) {
            if (stages.get(i).getId().equals(stage.getId())) {
                isPresent = true;
                stages.set(i, stage);
            }
        }

        if (!isPresent) {
            return super.createServiceResult((Stage) serviceResult.getObject(), true, "not found");
        }

        pipeline.setStages(stages);
        serviceResult = this.pipelineService.update(pipeline);

        if (serviceResult.hasError()) {
            serviceResult = super.createServiceResult((Stage) serviceResult.getObject(), true, "not updated");
        } else {
            serviceResult = super.createServiceResult(stage, false, "updated successfully");
        }
        return serviceResult;
    }

    @Override
    public ServiceResult delete(String stageId) {
        Pipeline pipelineToUpdate = new Pipeline();
        List<Pipeline> pipelines = (List<Pipeline>) this.pipelineService.getAll().getObject();

        for (Pipeline pipeline : pipelines) {
            List<Stage> stages = pipeline.getStages();

            for (Stage stage : stages) {
                if (stage.getId().equals(stageId)) {
                    pipelineToUpdate = pipeline;
                }
            }
        }

        boolean isRemoved = false;
        ServiceResult serviceResult = new ServiceResult();
        List<Stage> stages = pipelineToUpdate.getStages();
        Stage stage = stages
                .stream()
                .filter(st -> st.getId().equals(stageId))
                .findFirst()
                .orElse(null);

        if (stage == null) {
            serviceResult = super.createServiceResult(stage, true, "not found");
        }

        if (stages.size() > 1) {
            isRemoved = stages.remove(stage);
        } else {
            return super.createServiceResult(stage, true, "is the last Stage and cannot be deleted");
        }

        if (isRemoved) {
            pipelineToUpdate.setStages(stages);
            serviceResult = this.pipelineService.update(pipelineToUpdate);
            if (!serviceResult.hasError()) {

                serviceResult = super.createServiceResult(stage, false, "deleted successfully");
            }
        }
        return serviceResult;
    }

    private Stage extractStageFromPipeline(Pipeline pipline, String stageId) {
        Stage result = pipline.getStages().stream()
                .filter(stage -> stage.getId().equals(stageId))
                .findFirst()
                .orElse(null);

        return result;

    }
}