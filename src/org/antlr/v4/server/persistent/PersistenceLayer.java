package org.antlr.v4.server.persistent;

import java.io.IOException;
import java.security.InvalidKeyException;

public interface PersistenceLayer<K> {
    /**
     * Persists a byte buffer in the underlying storage system.
     * @param buffer byte buffer
     * @param identifier identifier for the persisted content
     * @throws IOException captures IO errors
     */
    void persist(byte[] buffer, K identifier) throws IOException;

    /** Return true if the identifier exists in the underlying storage system. */
    boolean exists(String identifier);

    /**
     * Retrieves the content from
     * @param identifier identifier for the persisted content
     * @return
     * @throws IOException captures IO errors
     * @throws InvalidKeyException when key cannot be found
     */
    byte[] retrieve(K identifier) throws IOException, InvalidKeyException;
}
