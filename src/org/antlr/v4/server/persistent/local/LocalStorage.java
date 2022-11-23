package org.antlr.v4.server.persistent.local;

import org.antlr.v4.server.persistent.PersistenceLayer;

import java.io.IOException;
import java.security.InvalidKeyException;

public class LocalStorage implements PersistenceLayer<String> {
    @Override
    public void persist(byte[] byteBuffer, String identifier) throws IOException {

    }

    @Override
    public byte[] retrieve(String identifier) throws IOException, InvalidKeyException {
        return new byte[0];
    }
}
