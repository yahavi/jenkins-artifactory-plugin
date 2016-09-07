package org.jfrog.hudson.pipeline.docker.proxy;

import hudson.FilePath;
import hudson.model.Node;
import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import net.lightbody.bmp.mitm.PemFileCertificateSource;
import net.lightbody.bmp.mitm.TrustSource;
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

    public static void init(int proxyPort, String proxyPublicKey, String proxyPrivateKey) {
        stop();
        PemFileCertificateSource fileCertificateSource = CertManager.getCertificateSource(proxyPublicKey, proxyPrivateKey);
        ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
                .rootCertificateSource(fileCertificateSource)
                .trustSource(TrustSource.defaultTrustSource())
                .build();

        try {
            server = DefaultHttpProxyServer.bootstrap()
                    .withPort(proxyPort)
                    .withFiltersSource(new BuildInfoHttpFiltersSource())
                    .withManInTheMiddle(mitmManager)
                    .withConnectTimeout(0)
                    .start();
            System.out.println("Certificate public key location: " + proxyPublicKey);
            System.out.println("Certificate private key location: " + proxyPrivateKey);
        } catch (RuntimeException e) {
            System.out.println(e.getStackTrace());
        }
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
        }
    }


    public static void stopAll() throws IOException, InterruptedException {
        stop();
        List<Node> nodes = Jenkins.getInstance().getNodes();
        for (Node node : nodes) {
            if (node == null || node.getChannel() == null) {
                continue;
            }
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
            if (node == null || node.getChannel() == null) {
                continue;
            }

            FilePath remoteCertPath = new FilePath(node.getChannel(), certPublic);
            FilePath localCertPath = new FilePath(new File(certPublic));
            localCertPath.copyTo(remoteCertPath);

            FilePath remoteKeyPath = new FilePath(node.getChannel(), certPrivate);
            FilePath localKeyPath = new FilePath(new File(certPrivate));
            localKeyPath.copyTo(remoteKeyPath);

            node.getChannel().call(new Callable<Boolean, IOException>() {
                public Boolean call() throws IOException {
                    DeProxy.init(port, certPublic, certPrivate);
                    return true;
                }
            });
        }
    }
}
