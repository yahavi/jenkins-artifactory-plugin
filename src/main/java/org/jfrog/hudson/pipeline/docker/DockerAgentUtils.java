package org.jfrog.hudson.pipeline.docker;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import hudson.Launcher;
import hudson.remoting.Callable;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.pipeline.docker.proxy.DeProxy;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Created by romang on 8/15/16.
 */
public class DockerAgentUtils implements Serializable {
    private static Multimap<String, Integer> imageIdToBuildInfoId = ArrayListMultimap.create();
    private static Multimap<Integer, DockerImage> buildInfoIdToDockerImage = ArrayListMultimap.create();
    private static Map<String, String> imageIdToImageTag = new HashMap<String, String>();

    public static boolean isProxyUp(Launcher launcher) throws IOException, InterruptedException {
        return launcher.getChannel().call(new Callable<Boolean, IOException>() {
            public Boolean call() throws IOException {
                return DeProxy.isUp();
            }
        });
    }

    public static String registerProxy(Launcher launcher, final String imageTag, final int buildInfoId) throws IOException, InterruptedException {
        return launcher.getChannel().call(new Callable<String, IOException>() {
            public String call() throws IOException {
                String imageId = DockerUtils.getImageDigest(imageTag);
                imageIdToBuildInfoId.put(imageId, buildInfoId);
                imageIdToImageTag.put(imageId, imageTag);
                return imageId;
            }
        });
    }

    public static void captureContent(String content, Properties properties) {
        try {
            String digest = DockerUtils.getConfigDigest(content);
            for (Integer buildInfoId : imageIdToBuildInfoId.get(digest)) {
                DockerImage dockerImage = new DockerImage(digest, imageIdToImageTag.get(digest), content);
                String parentId = DockerUtils.getParentDigest(digest);
                if (StringUtils.isNotEmpty(parentId)) {
                    properties.setProperty("docker.image.parent", DockerUtils.getShaValue(parentId));
                }
                dockerImage.addProperties(properties);
                buildInfoIdToDockerImage.put(buildInfoId, dockerImage);
            }
            imageIdToBuildInfoId.removeAll(digest);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<DockerImage> getDockerImagesFromAgent(Launcher launcher, final int buildInfoId) throws IOException, InterruptedException {
        return launcher.getChannel().call(new Callable<List<DockerImage>, IOException>() {
            public List<DockerImage> call() throws IOException {
                List<DockerImage> dockerImages = new ArrayList<DockerImage>();
                dockerImages.addAll(buildInfoIdToDockerImage.get(buildInfoId));
                buildInfoIdToDockerImage.removeAll(buildInfoId);
                return dockerImages;
            }
        });
    }
}
