package org.jfrog.hudson.pipeline.types;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by romang on 7/28/16.
 */
public class Docker implements Serializable {
    private CpsScript script;
    private String username;
    private String password;

    public Docker() {
    }

    public Docker(CpsScript script, String username, String password) {
        this.script = script;
        this.username = username;
        this.password = password;
    }

    public void setCpsScript(CpsScript script) {
        this.script = script;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Whitelisted
    public BuildInfo push(String imageTag, String targetRepository) throws Exception {
        return push(imageTag, targetRepository, null);
    }

    @Whitelisted
    public BuildInfo push(String imageTag, String targetRepository, BuildInfo providedBuildInfo) throws Exception {
        Map<String, Object> registerVariables = new LinkedHashMap<String, Object>();
        registerVariables.put("imageTag", imageTag);
        registerVariables.put("targetRepo", targetRepository);
        registerVariables.put("buildInfo", providedBuildInfo);

        BuildInfo buildInfo = (BuildInfo) script.invokeMethod("registerDockerImageStep", registerVariables);
        buildInfo.setCpsScript(script);

        Map<String, Object> pushVariables = new LinkedHashMap<String, Object>();
        pushVariables.put("imageTag", imageTag);
        pushVariables.put("username", username);
        pushVariables.put("password", password);
        script.invokeMethod("dockerPush", pushVariables);

        return buildInfo;
    }

    @Whitelisted
    public BuildInfo push(Map<String, Object> dockerArguments) throws Exception {
        Map<String, Object> registerVariables = new LinkedHashMap<String, Object>();
        registerVariables.put("imageTag", dockerArguments.get("image"));
        registerVariables.put("targetRepo", dockerArguments.get("targetRepo"));
        registerVariables.put("buildInfo", dockerArguments.get("buildInfo"));

        BuildInfo buildInfo = (BuildInfo) script.invokeMethod("registerDockerImageStep", registerVariables);
        buildInfo.setCpsScript(script);

        Map<String, Object> pushVariables = new LinkedHashMap<String, Object>();
        pushVariables.put("imageTag", dockerArguments.get("image"));
        pushVariables.put("username", username);
        pushVariables.put("password", password);
        script.invokeMethod("dockerPush", pushVariables);

        return buildInfo;
    }

    @Whitelisted
    public BuildInfo pull(String imageTag) throws Exception {
        return pull(imageTag, null);
    }

    @Whitelisted
    public BuildInfo pull(String imageTag, BuildInfo providedBuildInfo) throws Exception {
        Map<String, Object> pullVariables = new LinkedHashMap<String, Object>();
        pullVariables.put("imageTag", imageTag);
        pullVariables.put("username", username);
        pullVariables.put("password", password);
        script.invokeMethod("dockerPull", pullVariables);

        return providedBuildInfo;
    }
}
