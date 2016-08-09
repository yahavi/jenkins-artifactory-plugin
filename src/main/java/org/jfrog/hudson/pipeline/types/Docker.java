package org.jfrog.hudson.pipeline.types;

import hudson.model.TaskListener;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.build.api.Module;
import org.jfrog.hudson.pipeline.ArtifactoryConfigurator;
import org.jfrog.hudson.pipeline.docker.DockerImage;
import org.jfrog.hudson.pipeline.docker.DockerUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Created by romang on 7/28/16.
 */
public class Docker implements Serializable {

    private BuildInfo buildInfo;
    private Map<String, String> imageIdTags = new HashMap<String, String>();
    private List<DockerImage> dockerImages = new ArrayList<DockerImage>();
    private CpsScript cpsScript;

    public Docker(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    @Whitelisted
    public void capture(String imageTag) {
        Map<String, Object> stepVariables = new LinkedHashMap<String, Object>();
        String imageId = DockerUtils.getImageTagId(imageTag);
        stepVariables.put("imageTag", imageTag);
        stepVariables.put("imageId", DockerUtils.getImageTagId(imageTag));
        stepVariables.put("buildInfo", buildInfo);

        imageIdTags.put(imageId, imageTag);
        cpsScript.invokeMethod("registerDockerImageStep", stepVariables);
    }

    public void addCapturedManifest(String manifest) {
        try {
            String imageId = DockerUtils.getImageIdFromManifest(manifest);
            dockerImages.add(new DockerImage(imageId, imageIdTags.get(imageId), manifest));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<Module> generateBuildInfoModules(TaskListener listener, ArtifactoryConfigurator config,
                                                 String buildName, String buildNumber, String timestamp) throws IOException {
        ArrayList<Module> modules = new ArrayList<Module>();
        for (DockerImage dockerImage : dockerImages) {
            modules.add(dockerImage.generateBuildInfoModule(listener, config, buildName, buildNumber, timestamp));
        }
        return modules;
    }

    public void setCpsScript(CpsScript cpsScript) {
        this.cpsScript = cpsScript;
    }

    public void append(Docker other) {
        this.imageIdTags.putAll(other.imageIdTags);
        this.dockerImages.addAll(other.dockerImages);
    }
}
