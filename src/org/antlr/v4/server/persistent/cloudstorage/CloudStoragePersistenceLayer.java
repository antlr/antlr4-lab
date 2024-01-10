package org.antlr.v4.server.persistent.cloudstorage;

import com.google.cloud.storage.*;
import org.antlr.v4.server.ANTLRHttpServer;
import org.antlr.v4.server.persistent.PersistenceLayer;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;

public class CloudStoragePersistenceLayer implements PersistenceLayer<String> {
    static final ch.qos.logback.classic.Logger LOGGER =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ANTLRHttpServer.class);

    // The ID of your GCP project
    public static final String projectId = "antlr4lab";

    // The ID of your GCS bucket
    public static final String bucketName = "antlr4-lab-us";

    public void persist(byte[] byteBuffer, String identifier) throws IOException {
        for (String key: System.getenv().keySet()) {
            LOGGER.info(key + " " + System.getenv(key));
        }

        String objectName = identifier + ".json"; // The ID of your GCS object

        // The path to your file to upload
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

        storage.create(blobInfo, byteBuffer);
        LOGGER.info("Successfully stored " + objectName);
    }

    @Override
    public boolean exists(String identifier) {
        String objectName = identifier + ".json";
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
        BlobId blobId = BlobId.of(bucketName, objectName);
        return storage.get(blobId) != null;
    }

    @Override
    public byte[] retrieve(String identifier) throws IOException, InvalidKeyException {
        if ( identifier.equals("HASH") ) {
            return "{\"fake\":999}".getBytes();
        }
        String objectName = identifier + ".json";
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
        BlobId blobId = BlobId.of(bucketName, objectName);
        Blob blob = storage.get(blobId);
        if ( blob == null ) {
            throw new InvalidKeyException("Invalid key");
        }
        return blob.getContent();
    }
}
