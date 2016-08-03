package org.jfrog.hudson.pipeline.docker.proxy;

import net.lightbody.bmp.mitm.PemFileCertificateSource;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import java.io.File;

/**
 * Created by romang on 7/10/16.
 */
public class DeProxy {

    static private HttpProxyServer server = null;

    public static void init() {
        if (server != null) {
            return;
        }

        // TODO: allow enabling and disabling proxy in Jenkins configuration, with certificate and port configuration.
        PemFileCertificateSource fileCertificateSource = new PemFileCertificateSource(
                new File("/home/romang/.ssl/docker.jfrogdev.com.crt"),    // the PEM-encoded certificate file
                new File("/home/romang/.ssl/docker.jfrogdev.com.key"),    // the PEM-encoded private key file
                "");

        ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
                .rootCertificateSource(fileCertificateSource)
                .trustAllServers(true) // do not validate servers' certificates
                .build();

        try {
            server = DefaultHttpProxyServer.bootstrap()
                    .withPort(8082)
                    .withFiltersSource(new BuildInfoHttpFiltersSource())
                    .withManInTheMiddle(mitmManager)
                    .start();
        } catch (RuntimeException e) {
            // Do nothing.
        }
    }

    public static boolean isUp() {
        if (server == null) {
            return false;
        }
        return true;
    }
}
