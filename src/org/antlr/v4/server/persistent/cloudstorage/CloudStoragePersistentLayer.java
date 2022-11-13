package org.antlr.v4.server.persistent.cloudstorage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.antlr.v4.server.ANTLRHttpServer;
import org.antlr.v4.server.persistent.PersistentLayer;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;

public class CloudStoragePersistentLayer implements PersistentLayer<String> {
    static final ch.qos.logback.classic.Logger LOGGER =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ANTLRHttpServer.class);

    public void persist(byte[] byteBuffer, String identifier) throws IOException {
        for (String key: System.getenv().keySet()) {
            LOGGER.info(key + " " + System.getenv(key));
        }
        // The ID of your GCP project
        String projectId = "antlr4lab";

        // The ID of your GCS bucket
        String bucketName = "antlr4-lab-us";

        // The ID of your GCS object
        String objectName = identifier + ".json";

        // The path to your file to upload
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        storage.create(blobInfo, byteBuffer);
        LOGGER.info("Successfully stored " + objectName);
    }

    @Override
    public byte[] retrieve(String identifier) throws IOException, InvalidKeyException {
        return new byte[0];
    }
}
