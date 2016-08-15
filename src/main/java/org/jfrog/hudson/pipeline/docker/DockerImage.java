package org.jfrog.hudson.pipeline.docker;

import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.jfrog.build.api.Artifact;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.builder.ArtifactBuilder;
import org.jfrog.build.api.builder.DependencyBuilder;
import org.jfrog.build.api.search.AqlSearchResult;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryBuildInfoClient;
import org.jfrog.build.extractor.clientConfiguration.client.ArtifactoryDependenciesClient;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.pipeline.ArtifactoryConfigurator;
import org.jfrog.hudson.util.CredentialManager;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Created by romang on 8/9/16.
 */
public class DockerImage implements Serializable {
    private final String imageId;
    private final String imageTag;
    private final String manifest;
    private Properties properties = new Properties();

    public DockerImage(String imageId, String imageTag, String manifest) {
        this.imageId = imageId;
        this.imageTag = imageTag;
        this.manifest = manifest;
    }

    public void addProperties(Properties properties) {
        this.properties.putAll(properties);
    }

    public Module generateBuildInfoModule(TaskListener listener, ArtifactoryConfigurator config, String buildName, String buildNumber, String timestamp) throws IOException {
        final String buildProperties = String.format("build.name=%s|build.number=%s|build.timestamp=%s", buildName, buildNumber, timestamp);
        Properties artifactProperties = new Properties();
        artifactProperties.setProperty("build.name", buildName);
        artifactProperties.setProperty("build.number", buildNumber);
        artifactProperties.setProperty("build.timestamp", timestamp);

        ArtifactoryServer server = config.getArtifactoryServer();
        CredentialsConfig preferredResolver = server.getDeployerCredentialsConfig();
        ArtifactoryDependenciesClient dependenciesClient = server.createArtifactoryDependenciesClient(
                preferredResolver.getUsername(), preferredResolver.getPassword(),
                server.createProxyConfiguration(Jenkins.getInstance().proxy), listener);

        CredentialsConfig preferredDeployer = CredentialManager.getPreferredDeployer(config, server);
        ArtifactoryBuildInfoClient propertyChangeClient = server.createArtifactoryClient(
                preferredDeployer.getUsername(), preferredDeployer.getPassword(),
                server.createProxyConfiguration(Jenkins.getInstance().proxy));

        Module buildInfoModule = new Module();
        buildInfoModule.setId(imageTag.substring(imageTag.indexOf("/") + 1));
        DockerLayers layers = createLayers(dependenciesClient);
        setDependenciesAndArtifacts(buildInfoModule, layers, buildProperties, artifactProperties,
                dependenciesClient, propertyChangeClient, server);
        setProperties(buildInfoModule);
        return buildInfoModule;
    }

    private void setProperties(Module buildInfoModule) {
        properties.setProperty("docker.image.id", DockerUtils.getShaValue(imageId));
        properties.setProperty("docker.captured.image", imageTag);
        buildInfoModule.setProperties(properties);
    }

    private DockerLayers createLayers(ArtifactoryDependenciesClient dependenciesClient) throws IOException {
        String queryStr = getAqlSearchQuery();
        AqlSearchResult result = dependenciesClient.searchArtifactsByAql(queryStr);

        DockerLayers layers = new DockerLayers();
        for (AqlSearchResult.SearchEntry entry : result.getResults()) {
            DockerLayer layer = new DockerLayer(entry);
            layers.addLayer(layer);
        }
        return layers;
    }

    private void setDependenciesAndArtifacts(Module buildInfoModule, DockerLayers layers, String buildProperties, Properties artifactProperties, ArtifactoryDependenciesClient dependenciesClient, ArtifactoryBuildInfoClient propertyChangeClient, ArtifactoryServer server) throws IOException {
        DockerLayer historyLayer = layers.getByDigest(imageId);
        HttpResponse res = dependenciesClient.downloadArtifact(server.getUrl() + "/" + historyLayer.getFullPath());
        int dependencyLayerNum = DockerUtils.getNumberOfDependentLayers(IOUtils.toString(res.getEntity().getContent()));

        List<Dependency> dependencies = new ArrayList<Dependency>();
        Iterator<String> it = DockerUtils.getLayersDigests(manifest).iterator();
        for (int i = 0; i < dependencyLayerNum; i++) {
            String digest = it.next();
            DockerLayer layer = layers.getByDigest(digest);
            propertyChangeClient.executeUpdateFileProperty(layer.getFullPath(), buildProperties);
            Dependency dependency = new DependencyBuilder().id(layer.getFilename()).sha1(layer.getSha1()).properties(artifactProperties).build();
            dependencies.add(dependency);
        }
        buildInfoModule.setDependencies(dependencies);

        List<Artifact> artifacts = new ArrayList<Artifact>();
        while (it.hasNext()) {
            String digest = it.next();
            DockerLayer layer = layers.getByDigest(digest);
            propertyChangeClient.executeUpdateFileProperty(layer.getFullPath(), buildProperties);
            Artifact artifact = new ArtifactBuilder(layer.getFilename()).sha1(layer.getSha1()).properties(artifactProperties).build();
            artifacts.add(artifact);
        }
        buildInfoModule.setArtifacts(artifacts);
    }

    private String getAqlSearchQuery() {
        final String imagePath = DockerUtils.getImagePath(imageTag);
        List<String> layersDigest = DockerUtils.getLayersDigests(manifest);

        StringBuilder aqlRequestForDockerSha = new StringBuilder("items.find({\"$or\":[ ");
        List<String> layersQuery = new ArrayList<String>();
        for (String digest : layersDigest) {
            String shaVersion = DockerUtils.getShaVersion(digest);
            String shaValue = DockerUtils.getShaValue(digest);

            String singleFileQuery = String.format("{\"$and\": [{\"name\": {\"$eq\" : \"%s\"}}, {\"path\": {\"$eq\": \"%s\"}}]}",
                    DockerUtils.digestToFilename(digest), imagePath);

            //String singleFileQuery = "{\"$and\": [{\"name\": {\"$eq\" : \"" + shaVersion + "__" + shaValue + "\"}}, {\"path\": {\"$eq\": \"" + imagePath + "\"}}]}";
            if (StringUtils.equalsIgnoreCase(shaVersion, "sha1")) {

                singleFileQuery = String.format("{\"$and\": [{\"actual_sha1\": {\"$eq\" : \"%s\"}}, {\"path\": {\"$eq\": \"%s\"}}]}",
                        shaValue, imagePath);
                //singleFileQuery = "{\"$and\": [{\"actual_sha1\": {\"$eq\" : \"" + shaValue + "\"}}, {\"path\": {\"$eq\": \"" + imagePath + "\"}}]}";
            }
            layersQuery.add(singleFileQuery);
        }

        aqlRequestForDockerSha.append(StringUtils.join(layersQuery, ","));
        aqlRequestForDockerSha.append("]}).include(\"name\",\"repo\",\"path\",\"actual_sha1\")");
        return aqlRequestForDockerSha.toString();
    }
}
