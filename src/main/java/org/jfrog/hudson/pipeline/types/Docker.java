package org.jfrog.hudson.pipeline.types;

import hudson.Launcher;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.build.api.Module;
import org.jfrog.hudson.pipeline.ArtifactoryConfigurator;
import org.jfrog.hudson.pipeline.docker.DockerAgentUtils;
import org.jfrog.hudson.pipeline.docker.DockerImage;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by romang on 7/28/16.
 */
public class Docker implements Serializable {

    private BuildInfo buildInfo;
    private CpsScript cpsScript;
    private List<Integer> buildInfoIds = new ArrayList<Integer>();

    public Docker(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    @Whitelisted
    public void capture(String imageTag) {
        Map<String, Object> stepVariables = new LinkedHashMap<String, Object>();
        stepVariables.put("imageTag", imageTag);
        stepVariables.put("buildInfo", buildInfo);

        cpsScript.invokeMethod("registerDockerImageStep", stepVariables);
    }

    public List<Module> generateBuildInfoModules(TaskListener listener, ArtifactoryConfigurator config,
                                                 Launcher launcher) throws IOException, InterruptedException {
        buildInfoIds.add(buildInfo.hashCode());
        List<DockerImage> dockerImages = new ArrayList<DockerImage>();
        for (Integer buildInfoId : buildInfoIds) {
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

    public void append(Docker other) {
        buildInfoIds.add(other.buildInfo.hashCode());
    }
}
