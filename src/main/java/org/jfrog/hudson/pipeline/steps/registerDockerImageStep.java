package org.jfrog.hudson.pipeline.steps;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.cli.MissingArgumentException;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jfrog.hudson.pipeline.Utils;
import org.jfrog.hudson.pipeline.docker.DockerAgentUtils;
import org.jfrog.hudson.pipeline.types.buildInfo.BuildInfo;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by romang on 5/2/16.
 */
public class registerDockerImageStep extends AbstractStepImpl {

    private final String image;
    private final BuildInfo buildInfo;
    private final String targetRepo;

    @DataBoundConstructor
    public registerDockerImageStep(String image, String targetRepo, BuildInfo buildInfo) {
        this.image = image;
        this.buildInfo = buildInfo;
        this.targetRepo = targetRepo;
    }

    public String getImage() {
        return image;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public String getTargetRepo() {
        return targetRepo;
    }

    public static class Execution extends AbstractSynchronousStepExecution<BuildInfo> {
        private static final long serialVersionUID = 1L;

        @Inject(optional = true)
        private transient registerDockerImageStep step;

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient Run build;

        @StepContextParameter
        private transient Launcher launcher;

        @StepContextParameter
        private transient FilePath ws;

        @Override
        protected BuildInfo run() throws Exception {
            BuildInfo buildInfo = Utils.prepareBuildinfo(build, step.getBuildInfo());
            JenkinsBuildInfoLog log = new JenkinsBuildInfoLog(listener);
            if (!DockerAgentUtils.isProxyUp(launcher)) {
                getContext().onFailure(new RuntimeException("Build info capturing for Docker images is not available while Artifactory proxy is not running, enable the proxy in Jenkins configuration."));
                return null;
            }

            if (step.getImage() == null) {
                getContext().onFailure(new MissingArgumentException("Missing 'image' parameter"));
                return null;
            }

            if (step.getTargetRepo() == null) {
                getContext().onFailure(new MissingArgumentException("Missing 'targetRepo' parameter"));
                return null;
            }

            DockerAgentUtils.registerProxy(launcher, step.getImage(), step.getTargetRepo(), buildInfo.hashCode());
            return buildInfo;
        }
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(registerDockerImageStep.Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "registerDockerImageStep";
        }

        @Override
        public String getDisplayName() {
            return "Register DockerBuildInfo image for capturing to build info";
        }
    }

}

