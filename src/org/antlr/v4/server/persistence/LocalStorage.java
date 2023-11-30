package org.antlr.v4.server.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;

public class LocalStorage implements PersistenceLayer<String> {
    @Override
    public void persist(byte[] byteBuffer, String identifier) throws IOException {
        String objectName = identifier + ".json";
        Files.write(Paths.get("/tmp/" + objectName), byteBuffer);
    }

    @Override
    public byte[] retrieve(String identifier) throws IOException, InvalidKeyException {
        String objectName = identifier + ".json";
        return Files.readAllBytes(Paths.get("/tmp/" + objectName));
    }
}
