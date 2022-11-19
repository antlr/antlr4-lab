package org.antlr.v4.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.antlr.v4.runtime.*;

class CollectLexOrParseSyntaxErrors extends BaseErrorListener {
    final JsonArray msgs = new JsonArray();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg,
                            org.antlr.v4.runtime.RecognitionException e) {
        final JsonObject err = new JsonObject();
        if ( recognizer instanceof Lexer ) {
            int erridx = ((Lexer) recognizer)._input.index(); // where we detected error
            int startidx = erridx;
            if ( e instanceof LexerNoViableAltException ) {
                startidx = ((LexerNoViableAltException)e).getStartIndex();
            }
            err.addProperty("startidx", startidx);
            err.addProperty("erridx", erridx);
            err.addProperty("line", line);
            err.addProperty("pos", charPositionInLine);
            err.addProperty("msg", msg);
        }
        else {
            Token startToken;
            Token stopToken;
            if ( e instanceof NoViableAltException ) {
                startToken = ((NoViableAltException) e).getStartToken();
                stopToken = e.getOffendingToken();
            }
            else if ( e==null ) {
                startToken = stopToken = (Token)offendingSymbol;
            }
            else {
                startToken = stopToken = e.getOffendingToken();
            }
            err.addProperty("startidx", startToken.getTokenIndex());
            err.addProperty("stopidx", stopToken.getTokenIndex());
            err.addProperty("line", line);
            err.addProperty("pos", charPositionInLine);
            err.addProperty("msg", msg);
        }
        msgs.add(err);
    }
}
