package org.jfrog.hudson.pipeline.docker.proxy;

import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.CharsetUtil;
import org.jfrog.hudson.pipeline.docker.DockerAgentUtils;
import org.littleshoot.proxy.HttpFiltersAdapter;

import java.util.Properties;

/**
 * Created by romang on 7/10/16.
 */
public class BuildInfoFilterAdapter extends HttpFiltersAdapter {

    public BuildInfoFilterAdapter(HttpRequest originalRequest, ChannelHandlerContext ctx) {
        super(originalRequest, ctx);
    }

    public BuildInfoFilterAdapter(HttpRequest originalRequest) {
        super(originalRequest);
    }

    @Override
    public HttpResponse proxyToServerRequest(HttpObject httpObject) {
        if (httpObject instanceof ByteBufHolder && originalRequest.getMethod() == HttpMethod.PUT
                && originalRequest.getUri().contains("manifest")) {
            String contentStr = ((ByteBufHolder) httpObject).content().toString(CharsetUtil.UTF_8);
            Properties properties = new Properties();
            properties.put("User-Agent", originalRequest.headers().get("User-Agent"));
            DockerAgentUtils.captureContent(contentStr, properties);
        }

        return null;
    }
}
