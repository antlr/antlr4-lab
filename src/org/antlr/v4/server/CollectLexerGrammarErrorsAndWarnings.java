package org.antlr.v4.server;

import org.antlr.v4.tool.ErrorManager;

public class CollectLexerGrammarErrorsAndWarnings extends CollectGrammarErrorsAndWarnings {
    public CollectLexerGrammarErrorsAndWarnings(ErrorManager errMgr) {
        super(errMgr);
        fileName = "lexer";
    }
}
