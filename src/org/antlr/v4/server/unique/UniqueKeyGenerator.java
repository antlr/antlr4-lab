package org.antlr.v4.server.unique;

import java.util.Optional;

public interface UniqueKeyGenerator {
    Optional<String> generateKey();
}
