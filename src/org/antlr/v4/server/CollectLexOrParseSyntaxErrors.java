package org.antlr.v4.server;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
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
        if (offendingSymbol != null) {
            err = String.format("{\"token\":%d,\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
                    ((Token) (offendingSymbol)).getTokenIndex(), line, charPositionInLine, msg);
        } else {
            err = String.format("{\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
                    line, charPositionInLine, msg);
        }
        msgs.add(err);
    }
}
