package org.antlr.v4.server;

import org.antlr.v4.tool.ErrorManager;

public class CollectParserGrammarErrorsAndWarnings extends CollectGrammarErrorsAndWarnings {
    public CollectParserGrammarErrorsAndWarnings(ErrorManager errMgr) {
        super(errMgr);
        fileName = "parser";
    }
}
