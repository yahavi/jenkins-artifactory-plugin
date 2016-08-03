package org.jfrog.hudson.pipeline.types;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.hudson.pipeline.docker.DockerUtils;

import java.io.Serializable;
import java.util.*;

/**
 * Created by romang on 7/28/16.
 */
public class Docker implements Serializable {

    private BuildInfo buildInfo;
    private CpsScript cpsScript;
    private Set<String> capturedImages = new HashSet<String>();
    private Set<String> dockerLayersDependencies = new HashSet<String>();

    public Docker(BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    @Whitelisted
    public void capture(String imageTag) {
        Map<String, Object> stepVariables = new LinkedHashMap<String, Object>();
        stepVariables.put("imageTag", imageTag);
        stepVariables.put("imageId", DockerUtils.getImageTagId(imageTag));
        stepVariables.put("buildInfo", buildInfo);

        capturedImages.add(imageTag);
        cpsScript.invokeMethod("registerDockerImageStep", stepVariables);
    }

    @Whitelisted
    public Set<String> getCaptured() {
        return capturedImages;
    }

    public void setCpsScript(CpsScript cpsScript) {
        this.cpsScript = cpsScript;
    }

    public Set<String> getDockerLayersDependencies() {
        return dockerLayersDependencies;
    }
}
