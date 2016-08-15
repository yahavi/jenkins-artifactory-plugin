package org.jfrog.hudson.pipeline.docker.proxy;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.slaves.ComputerListener;
import org.jfrog.hudson.util.plugins.PluginsUtils;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by romang on 8/14/16.
 */
@Extension
public class AgentProxy extends ComputerListener implements Serializable {

    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        final int port = PluginsUtils.getProxyPort();
        final String publicKey = PluginsUtils.getProxyPublicKey();
        final String privateKey = PluginsUtils.getProxyPrivateKey();
        c.getChannel().call(new Callable<Boolean, IOException>() {
            public Boolean call() throws IOException {
                DeProxy.init(port, publicKey, privateKey);
                return true;
            }
        });
        super.onOnline(c, listener);
    }
}
