package org.antlr.v4.server.persistence;

import java.io.IOException;
import java.security.InvalidKeyException;

public interface PersistenceLayer<K> {
    static final LocalStorage local = new LocalStorage();
    static final CloudStoragePersistenceLayer cloud = new CloudStoragePersistenceLayer();

    public static PersistenceLayer newInstance(String where) {
        switch (where) {
            case "cloud" : return cloud;
            default : return local;
        }
    }

    /**
     * Persists a byte buffer in the underlying storage system.
     * @param buffer byte buffer
     * @param identifier identifier for the persisted content
     * @throws IOException captures IO errors
     */
    void persist(byte[] buffer, K identifier) throws IOException;

    /**
     * Retrieves the content from
     * @param identifier identifier for the persisted content
     * @return
     * @throws IOException captures IO errors
     * @throws InvalidKeyException when key cannot be found
     */
    byte[] retrieve(K identifier) throws IOException, InvalidKeyException;
}
