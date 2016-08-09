package org.jfrog.hudson.pipeline.docker;

import org.jfrog.build.api.search.AqlSearchResult;

import java.io.Serializable;

/**
 * Created by romang on 8/9/16.
 */
public class DockerLayer implements Serializable {
    private String repo;
    private String path;
    private String filename;
    private String sha1;
    private String digest;

    public DockerLayer(AqlSearchResult.SearchEntry entry) {
        this.repo = entry.getRepo();
        this.path = entry.getPath();
        this.filename = entry.getName();
        this.sha1 = entry.getActual_sha1();
        if (!filename.equals("manifest.json")) {
            this.digest = DockerUtils.filenameToDigest(filename);
        } else {
            this.digest = "sha1:" + sha1;
        }
    }

    public String getFullPath() {
        return repo + "/" + path + "/" + filename;
    }

    public String getFilename() {
        return filename;
    }

    public String getSha1() {
        return sha1;
    }

    public String getDigest() {
        return digest;
    }
}
