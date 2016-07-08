package net.hawkengine.core.materialupdater;

import net.hawkengine.model.MaterialDefinition;

public class GitMaterialUpdater extends MaterialUpdater{
    private IGitService gitService;

    public GitMaterialUpdater() {
        this.gitService = new GitService();
    }

    public GitMaterialUpdater(IGitService gitService) {
        this.gitService = gitService;
    }

    @Override
    public MaterialDefinition getLatestMaterialVersion(MaterialDefinition materialDefinition) {
        return null;
    }

    @Override
    public boolean areMaterialsSameVersion(MaterialDefinition latestMaterial, MaterialDefinition dbMaterial) {
        return false;
    }
}
