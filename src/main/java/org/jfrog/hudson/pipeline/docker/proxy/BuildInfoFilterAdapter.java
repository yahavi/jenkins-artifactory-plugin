package org.jfrog.hudson.pipeline.docker.proxy;

import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.jfrog.hudson.pipeline.docker.DockerUtils;
import org.littleshoot.proxy.HttpFiltersAdapter;

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
        System.out.println("####################" + originalRequest.getUri());
        if (httpObject instanceof ByteBufHolder && originalRequest.getMethod() == HttpMethod.PUT) {
            String contentStr = ((ByteBufHolder) httpObject).content().toString(CharsetUtil.UTF_8);
            DockerUtils.captureContent(contentStr);
        }

        return null;
    }
}
