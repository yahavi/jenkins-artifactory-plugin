package org.jfrog.hudson.pipeline.docker.proxy;

/**
 * Created by romang on 7/28/16.
 */
public interface ProxyBuildInfoCallback {

    void collectBuildInfo(String content);
}
