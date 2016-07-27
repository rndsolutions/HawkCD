package net.hawkengine.services;

import net.hawkengine.db.DbRepositoryFactory;
import net.hawkengine.db.IDbRepository;
import net.hawkengine.model.Material;
import net.hawkengine.model.ServiceResult;
import net.hawkengine.services.interfaces.IMaterialService;

import java.util.List;

public class MaterialService extends CrudService<Material> implements IMaterialService {
    private static final Class CLASS_TYPE = Material.class;

    public MaterialService() {
        IDbRepository repository = DbRepositoryFactory.create(DATABASE_TYPE, CLASS_TYPE);
        super.setRepository(repository);
        super.setObjectType(CLASS_TYPE.getSimpleName());
    }

    public MaterialService(IDbRepository repository) {
        super.setRepository(repository);
        super.setObjectType(CLASS_TYPE.getSimpleName());
    }

    @Override
    public ServiceResult getById(String materialId) {
        return super.getById(materialId);
    }

    @Override
    public ServiceResult getAll() {
        return super.getAll();
    }

    @Override
    public ServiceResult add(Material material) {
        return super.add(material);
    }

    @Override
    public ServiceResult update(Material material) {
        return super.update(material);
    }

    @Override
    public ServiceResult delete(String materialId) {
        return super.delete(materialId);
    }

    @Override
    public ServiceResult getLatestMaterial(String materialDefinitionId) {
        List<Material> materials = (List<Material>) this.getAll().getObject();
        Material latestMaterial = materials
                .stream()
                .filter(m -> m.getMaterialDefinition().getId().equals(materialDefinitionId))
                .sorted((m1, m2) -> m2.getChangeDate().compareTo(m1.getChangeDate()))
                .findFirst()
                .orElse(null);

        if (latestMaterial != null) {
            return super.createServiceResult(latestMaterial, false, "retrieved successfully");
        } else {
            return super.createServiceResult(latestMaterial, true, "not found");
        }
    }
}
