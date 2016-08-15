package org.jfrog.hudson.pipeline.types;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.builder.ArtifactBuilder;
import org.jfrog.build.api.builder.DependencyBuilder;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.builder.ModuleBuilder;
import org.jfrog.build.api.dependency.BuildDependency;
import org.jfrog.build.api.search.AqlSearchResult;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryBuildInfoClient;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryDependenciesClient;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.pipeline.ArtifactoryConfigurator;
import org.jfrog.hudson.pipeline.BuildInfoDeployer;
import org.jfrog.hudson.pipeline.docker.DockerUtils;
import org.jfrog.hudson.pipeline.docker.proxy.ProxyBuildInfoCallback;
import org.jfrog.hudson.util.BuildUniqueIdentifierHelper;
import org.jfrog.hudson.util.CredentialManager;
import org.jfrog.hudson.util.ExtractorUtils;

import java.io.IOException;
import java.io.Serializable;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Created by romang on 4/26/16.
 */
public class BuildInfo implements Serializable, ProxyBuildInfoCallback {

    private String buildName;
    private String buildNumber;
    private Date startDate;
    private BuildRetention retention;
    private List<BuildDependency> buildDependencies = new ArrayList<BuildDependency>();
    private List<Artifact> deployedArtifacts = new ArrayList<Artifact>();
    private List<Dependency> publishedDependencies = new ArrayList<Dependency>();

    private List<Module> modules = new ArrayList<Module>();
    private Env env = new Env();

    private Docker docker = new Docker(this);

    public BuildInfo(Run build) {
        this.buildName = BuildUniqueIdentifierHelper.getBuildName(build);
        this.buildNumber = BuildUniqueIdentifierHelper.getBuildNumber(build);
        this.startDate = new Date(build.getStartTimeInMillis());
        this.retention = new BuildRetention();
    }

    @Whitelisted
    public void setName(String name) {
        this.buildName = name;
    }

    @Whitelisted
    public void setNumber(String number) {
        this.buildNumber = number;
    }

    @Whitelisted
    public String getName() {
        return buildName;
    }

    @Whitelisted
    public String getNumber() {
        return buildNumber;
    }

    @Whitelisted
    public Date getStartDate() {
        return startDate;
    }

    @Whitelisted
    public void setStartDate(Date date) {
        this.startDate = date;
    }

    @Whitelisted
    public void append(BuildInfo other) {
        this.modules.addAll(other.modules);
        this.deployedArtifacts.addAll(other.deployedArtifacts);
        this.publishedDependencies.addAll(other.publishedDependencies);
        this.buildDependencies.addAll(other.buildDependencies);
        this.docker.append(other.docker);
        this.env.append(other.env);
    }

    @Whitelisted
    public Env getEnv() {
        return env;
    }

    @Whitelisted
    public Docker getDocker() {
        return docker;
    }

    @Whitelisted
    public BuildRetention getRetention() {
        return retention;
    }

    @Whitelisted
    public void retention(Map<String, Object> retentionArguments) throws Exception {
        Set<String> retentionArgumentsSet = retentionArguments.keySet();
        List<String> keysAsList = Arrays.asList(new String[]{"maxDays", "maxBuilds", "deleteBuildArtifacts", "doNotDiscardBuilds"});
        if (!keysAsList.containsAll(retentionArgumentsSet)) {
            throw new IllegalArgumentException("Only the following arguments are allowed: " + keysAsList.toString());
        }

        final ObjectMapper mapper = new ObjectMapper();
        this.retention = mapper.convertValue(retentionArguments, BuildRetention.class);
    }

    protected void appendDeployedArtifacts(List<Artifact> artifacts) {
        if (artifacts == null) {
            return;
        }
        deployedArtifacts.addAll(artifacts);
    }

    protected void appendBuildDependencies(List<BuildDependency> dependencies) {
        if (dependencies == null) {
            return;
        }
        buildDependencies.addAll(dependencies);
    }

    protected void appendPublishedDependencies(List<Dependency> dependencies) {
        if (dependencies == null) {
            return;
        }
        publishedDependencies.addAll(dependencies);
    }

    protected List<Artifact> getDeployedArtifacts() {
        return deployedArtifacts;
    }

    protected List<BuildDependency> getBuildDependencies() {
        return buildDependencies;
    }

    protected List<Dependency> getPublishedDependencies() {
        return publishedDependencies;
    }

    protected Map<String, String> getEnvVars() {
        return env.getEnvVars();
    }

    protected Map<String, String> getSysVars() {
        return env.getSysVars();
    }

    protected BuildInfoDeployer createDeployer(Run build, TaskListener listener, ArtifactoryServer server)
            throws InterruptedException, NoSuchAlgorithmException, IOException {

        ArtifactoryConfigurator config = new ArtifactoryConfigurator(server);
        CredentialsConfig preferredDeployer = CredentialManager.getPreferredDeployer(config, server);
        ArtifactoryBuildInfoClient client = server.createArtifactoryClient(preferredDeployer.getUsername(),
                preferredDeployer.getPassword(), server.createProxyConfiguration(Jenkins.getInstance().proxy));

        List<Module> dockerModules = docker.generateBuildInfoModules(listener, config,
                buildName, buildNumber, Long.toString(this.startDate.getTime()));
        addModules(dockerModules);

        return new BuildInfoDeployer(config, client, build, listener, new BuildInfoAccessor(this));
    }

    private void addModules(List<Module> modules) {
        for (Module module : modules) {
            for (Artifact artifact : module.getArtifacts()) {
                deployedArtifacts.put(artifact, artifact);
            }
            for (Dependency dependency : module.getDependencies()) {
                publishedDependencies.put(dependency, dependency);
            }
        }
        addDefaultModuleToModules(ExtractorUtils.sanitizeBuildName(build.getParent().getDisplayName()));
        return new PipelineBuildInfoDeployer(config, client, build, listener, new BuildInfoAccessor(this));
    }

    private void addDefaultModuleToModules(String moduleId) {
        ModuleBuilder moduleBuilder = new ModuleBuilder()
                .id(moduleId)
                .artifacts(deployedArtifacts)
                .dependencies(publishedDependencies);
        modules.add(moduleBuilder.build());
    }

    public void setCpsScript(CpsScript cpsScript) {
        this.env.setCpsScript(cpsScript);
        this.docker.setCpsScript(cpsScript);
    }

    public void collectBuildInfo(String content) {
        docker.addCapturedManifest(content);
    }

    public List<Module> getModules() {
        return modules;
    }
}
