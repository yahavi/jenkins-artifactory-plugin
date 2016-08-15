package org.jfrog.hudson.pipeline.docker.proxy;

import hudson.model.Node;
import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import net.lightbody.bmp.mitm.PemFileCertificateSource;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by romang on 7/10/16.
 */
public class DeProxy {

    static private HttpProxyServer server = null;
    static private int port;
    static private String publicKey;
    static private String privateKey;

    public static void init(int proxyPort, String proxyPublicKey, String proxyPrivateKey) {

        if (server != null && port == proxyPort &&
                publicKey.equals(proxyPublicKey) && privateKey.equals(proxyPrivateKey)) {
            return;
        }

        stop();
        PemFileCertificateSource fileCertificateSource = new PemFileCertificateSource(
                new File(proxyPublicKey),    // the PEM-encoded certificate file
                new File(proxyPrivateKey),    // the PEM-encoded private key file
                "");

        ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
                .rootCertificateSource(fileCertificateSource)
                .trustAllServers(true) // do not validate servers' certificates
                .build();

        try {
            server = DefaultHttpProxyServer.bootstrap()
                    .withPort(proxyPort)
                    .withFiltersSource(new BuildInfoHttpFiltersSource())
                    .withManInTheMiddle(mitmManager)
                    .start();
        } catch (RuntimeException e) {
            System.out.println(e.getStackTrace());
        }
        port = proxyPort;
        publicKey = proxyPublicKey;
        privateKey = proxyPrivateKey;
    }

    public static boolean isUp() {
        if (server == null) {
            return false;
        }
        return true;
    }

    public static void stop() {
        if (server != null) {
            server.stop();
            server = null;
            port = 0;
            publicKey = null;
            privateKey = null;
        }
    }


    public static void stopAll() throws IOException, InterruptedException {
        stop();
        List<Node> nodes = Jenkins.getInstance().getNodes();
        for (Node node : nodes) {
            node.getChannel().call(new Callable<Boolean, IOException>() {
                public Boolean call() throws IOException {
                    DeProxy.stop();
                    return true;
                }
            });
        }
    }

    public static void initAll(final int port, final String certPublic, final String certPrivate) throws IOException, InterruptedException {
        init(port, certPublic, certPrivate);
        List<Node> nodes = Jenkins.getInstance().getNodes();
        for (Node node : nodes) {
            node.getChannel().call(new Callable<Boolean, IOException>() {
                public Boolean call() throws IOException {
                    DeProxy.init(port, certPublic, certPrivate);
                    return true;
                }
            });
        }
    }
}
