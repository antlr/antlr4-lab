package org.antlr.v4.server;

import org.antlr.v4.runtime.*;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

class CollectLexOrParseSyntaxErrors extends BaseErrorListener {
    JsonArrayBuilder msgs = Json.createArrayBuilder();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg,
                            org.antlr.v4.runtime.RecognitionException e) {
        JsonObject err;
        if ( recognizer instanceof Lexer ) {
            int erridx = ((Lexer) recognizer)._input.index(); // where we detected error
            int startidx = erridx;
            if ( e instanceof LexerNoViableAltException ) {
                startidx = ((LexerNoViableAltException)e).getStartIndex();
            }
            err = Json.createObjectBuilder()
                    .add("startidx", startidx)
                    .add("erridx", erridx)
                    .add("line", line)
                    .add("pos", charPositionInLine)
                    .add("msg", msg)
                    .build();
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
            err = Json.createObjectBuilder()
                    .add("startidx", startToken.getTokenIndex())
                    .add("stopidx", stopToken.getTokenIndex())
                    .add("line", line)
                    .add("pos", charPositionInLine)
                    .add("msg", msg)
                    .build();
        }
        msgs.add(err);
    }
}
