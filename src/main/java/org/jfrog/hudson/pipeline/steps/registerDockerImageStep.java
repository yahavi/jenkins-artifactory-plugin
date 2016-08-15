package org.jfrog.hudson.pipeline.steps;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jfrog.hudson.pipeline.docker.DockerAgentUtils;
import org.jfrog.hudson.pipeline.types.BuildInfo;
import org.jfrog.hudson.util.JenkinsBuildInfoLog;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by romang on 5/2/16.
 */
public class registerDockerImageStep extends AbstractStepImpl {

    private final String imageTag;
    private final BuildInfo buildInfo;

    @DataBoundConstructor
    public registerDockerImageStep(String imageTag, BuildInfo buildInfo) {
        this.imageTag = imageTag;
        this.buildInfo = buildInfo;
    }

    public String getImageTag() {
        return imageTag;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public static class Execution extends AbstractSynchronousStepExecution<Boolean> {
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
        protected Boolean run() throws Exception {
            JenkinsBuildInfoLog log = new JenkinsBuildInfoLog(listener);
            if (!DockerAgentUtils.isProxyUp(launcher)) {
                log.error("Artifactory proxy is not running, build info will not be collected for image:");
                log.error(step.getImageTag());
                throw new RuntimeException("Docker proxy is not running, enable the proxy in Jenkins configuration");
            }

            log.info("Build info will be captured for docker image: " + step.getImageTag());
            DockerAgentUtils.registerProxy(launcher, step.getImageTag(), step.getBuildInfo().hashCode());
            return true;
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
            return "Register Docker image for capturing to build info";
        }
    }

}

