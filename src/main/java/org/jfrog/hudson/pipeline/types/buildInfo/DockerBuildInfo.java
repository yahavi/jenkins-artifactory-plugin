package org.jfrog.hudson.pipeline.types.buildInfo;

import hudson.Launcher;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.build.api.Module;
import org.jfrog.hudson.pipeline.ArtifactoryConfigurator;
import org.jfrog.hudson.pipeline.docker.DockerAgentUtils;
import org.jfrog.hudson.pipeline.docker.DockerImage;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by romang on 7/28/16.
 */
public class DockerBuildInfo implements Serializable {

    private BuildInfo buildInfo;
    private CpsScript cpsScript;
    private List<Integer> aggregatedBuildInfoIds = new ArrayList<Integer>();

    public DockerBuildInfo(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public List<Module> generateBuildInfoModules(TaskListener listener, ArtifactoryConfigurator config,
                                                 Launcher launcher) throws IOException, InterruptedException {
        aggregatedBuildInfoIds.add(buildInfo.hashCode());
        List<DockerImage> dockerImages = new ArrayList<DockerImage>();
        for (Integer buildInfoId : aggregatedBuildInfoIds) {
            dockerImages.addAll(DockerAgentUtils.getDockerImagesFromAgent(launcher, buildInfoId));
        }

        String timestamp = Long.toString(buildInfo.getStartDate().getTime());
        ArrayList<Module> modules = new ArrayList<Module>();
        for (DockerImage dockerImage : dockerImages) {
            modules.add(dockerImage.generateBuildInfoModule(listener, config, buildInfo.getName(), buildInfo.getNumber(), timestamp));
        }
        return modules;
    }

    public void setCpsScript(CpsScript cpsScript) {
        this.cpsScript = cpsScript;
    }

    public void append(DockerBuildInfo other) {
        aggregatedBuildInfoIds.add(other.buildInfo.hashCode());
    }
}
