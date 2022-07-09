package org.antlr.v4.server;

import org.antlr.v4.runtime.misc.Utils;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ANTLRToolListener;

import java.util.ArrayList;
import java.util.List;

class CollectGrammarErrorsAndWarnings implements ANTLRToolListener {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    @Override
    public void info(String msg) {
    }

    @Override
    public void error(ANTLRMessage msg) {
        String errText = (String) msg.getArgs()[0];
        errText = Utils.escapeJSONString(errText);
        String s = String.format("{\"type\":\"%s\",\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
                msg.getErrorType().toString(),
                msg.line,
                msg.charPosition,
                errText);
        errors.add(s);
    }

    @Override
    public void warning(ANTLRMessage msg) {
        String errText = (String) msg.getArgs()[0];
        errText = Utils.escapeJSONString(errText);
        String s = String.format("{\"type\":\"%s\",\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
                msg.getErrorType().toString(),
                msg.line,
                msg.charPosition,
                errText);
        warnings.add(s);
    }
}
