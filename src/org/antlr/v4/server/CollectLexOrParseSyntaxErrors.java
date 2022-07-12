package org.antlr.v4.server;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Utils;

import java.util.ArrayList;
import java.util.List;

class CollectLexOrParseSyntaxErrors extends BaseErrorListener {
    List<String> msgs = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg,
                            org.antlr.v4.runtime.RecognitionException e) {
        msg = Utils.escapeJSONString(msg);
        String err;
        if ( recognizer instanceof Lexer ) {
            int erridx = ((Lexer) recognizer)._input.index(); // where we detected error
            int startidx = erridx;
            if ( e instanceof LexerNoViableAltException ) {
                startidx = ((LexerNoViableAltException)e).getStartIndex();
            }
            err = String.format("{\"startidx\":%d,\"erridx\":%d,\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
                    startidx, erridx, line, charPositionInLine, msg);
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
            err = String.format("{\"startidx\":%d,\"stopidx\":%d,\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
                    startToken.getTokenIndex(), stopToken.getTokenIndex(), line, charPositionInLine, msg);
        }
        msgs.add(err);
    }
}
