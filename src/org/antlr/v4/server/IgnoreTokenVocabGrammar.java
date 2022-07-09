package org.antlr.v4.server;

import org.antlr.runtime.RecognitionException;
import org.antlr.v4.tool.ANTLRToolListener;
import org.antlr.v4.tool.Grammar;

class IgnoreTokenVocabGrammar extends Grammar {
    public IgnoreTokenVocabGrammar(String fileName,
                                   String grammarText,
                                   Grammar tokenVocabSource,
                                   ANTLRToolListener listener)
            throws RecognitionException {
        super(fileName, grammarText, tokenVocabSource, listener);
    }

    @Override
    public void importTokensFromTokensFile() {
        // don't try to import tokens files; must give me both grammars if split
    }
}
