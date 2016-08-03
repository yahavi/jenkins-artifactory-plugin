package org.jfrog.hudson.pipeline.docker;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.hash.Hashing;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.pipeline.PipelineUtils;
import org.jfrog.hudson.pipeline.docker.proxy.ProxyBuildInfoCallback;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by romang on 7/28/16.
 */
public class DockerUtils {
    private static Multimap<String, WeakReference<ProxyBuildInfoCallback>> imageTagsToCapture = ArrayListMultimap.create();

    /**
     * Register to collect buildInfo for specific docker image.
     *
     * @param imageTag
     * @param buildInfoCallback
     * @param log
     */
    public static void registerProxy(String id, ProxyBuildInfoCallback buildInfoCallback) {
        imageTagsToCapture.put(id, new WeakReference<ProxyBuildInfoCallback>(buildInfoCallback));
    }

    /**
     * Get image Id from imageTag using Docker client
     * @param imageTag
     * @return
     */
    public static String getImageTagId(String imageTag) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        return dockerClient.inspectImageCmd(imageTag).exec().getId();
    }

    /**
     * Pass captured content (manifest.json) from the proxy to buildInfo handler.
     * @param content
     */
    public static void captureContent(String content) {
        JsonNode manifest = null;
        try {
            manifest = PipelineUtils.mapper().readTree(content);
            String digest = StringUtils.remove(manifest.get("config").get("digest").toString(), "\"");

            for (WeakReference<ProxyBuildInfoCallback> buildInfo : imageTagsToCapture.get(digest)) {
                if (buildInfo.get() != null) {
                    buildInfo.get().collectBuildInfo(content);
                }
            }
            imageTagsToCapture.removeAll(digest);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get a list of layer digests from docker manifest
     *
     * @param manifestContent
     * @return
     */
    public static List<String> getDockerManifestLayersDigest(String manifestContent) {
        List<String> dockerLayersDependencies = new ArrayList<String>();
        String manifestSha1 = Hashing.sha1().hashString(manifestContent, Charsets.UTF_8).toString();
        dockerLayersDependencies.add("sha1:" + manifestSha1);
        try {
            JsonNode manifest = PipelineUtils.mapper().readTree(manifestContent);
            JsonNode schemaVersion = manifest.get("schemaVersion");
            boolean isSchemeVersion1 = schemaVersion.asInt() == 1;
            JsonNode fsLayers = getFsLayers(manifest, isSchemeVersion1);
            for (JsonNode fsLayer : fsLayers) {
                JsonNode blobSum = getBlobSum(isSchemeVersion1, fsLayer);
                dockerLayersDependencies.add(blobSum.asText());
            }
            dockerLayersDependencies.add(StringUtils.remove(manifest.get("config").get("digest").toString(), "\""));

        } catch (IOException e) {
            e.printStackTrace();
        }
        return dockerLayersDependencies;
    }

    /**
     * return blob sum depend on scheme version
     *
     * @param isSchemeVersion1 - if true scheme version 1
     * @param manifest         - docker manifest
     * @return - layer element
     */
    private static JsonNode getFsLayers(JsonNode manifest, boolean isSchemeVersion1) {
        JsonNode fsLayers;
        if (isSchemeVersion1) {
            fsLayers = manifest.get("fsLayers");
        } else {
            fsLayers = manifest.get("layers");
        }
        return fsLayers;
    }

    /**
     * return blob sum depend on scheme version
     *
     * @param isSchemeVersion1 - if true scheme version 1
     * @param fsLayer          - docker layers
     * @return - manifest element
     */
    private static JsonNode getBlobSum(boolean isSchemeVersion1, JsonNode fsLayer) {
        JsonNode blobSum;
        if (isSchemeVersion1) {
            blobSum = fsLayer.get("blobSum");
        } else {
            blobSum = fsLayer.get("digest");
        }
        return blobSum;
    }

    public static String getShaValue(String digest) {
        return StringUtils.substring(digest, StringUtils.indexOf(digest, ":") + 1);
    }

    public static String getShaVersion(String digest) {
        return StringUtils.substring(digest, 0, StringUtils.indexOf(digest, ":"));
    }
}
