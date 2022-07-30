package org.antlr.v4.server;

import org.antlr.v4.Tool;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ANTLRToolListener;
import org.antlr.v4.tool.ErrorManager;
import org.stringtemplate.v4.ST;

import java.util.ArrayList;
import java.util.List;

class CollectGrammarErrorsAndWarnings implements ANTLRToolListener {
    ErrorManager errMgr;

    String fileName;

    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    public CollectGrammarErrorsAndWarnings(ErrorManager errMgr) {
        this.errMgr = errMgr;
    }

    @Override
    public void info(String msg) {
    }

    @Override
    public void error(ANTLRMessage msg) {
        msg.fileName = fileName;
        ST msgST = errMgr.getMessageTemplate(msg);
        String outputMsg = msgST.render();
        if ( errMgr.formatWantsSingleLineMessage() ) {
            outputMsg = outputMsg.replace('\n', ' ');
        }

        // Strip "(126)" from "error(126): "
        outputMsg = outputMsg.replaceAll("error\\(.*?\\)", "error");
        outputMsg = JsonSerializer.escapeJSONString(outputMsg);
        String s = String.format("{\"type\":\"%s\",\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
                msg.getErrorType().toString(),
                msg.line,
                msg.charPosition,
                outputMsg);
        errors.add(s);
    }

    @Override
    public void warning(ANTLRMessage msg) {
        msg.fileName = fileName;
        ST msgST = errMgr.getMessageTemplate(msg);
        String outputMsg = msgST.render();
        if ( errMgr.formatWantsSingleLineMessage() ) {
            outputMsg = outputMsg.replace('\n', ' ');
        }

        outputMsg = outputMsg.replaceAll("warning\\(.*?\\)", "warning");
        outputMsg = JsonSerializer.escapeJSONString(outputMsg);
        String s = String.format("{\"type\":\"%s\",\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
                msg.getErrorType().toString(),
                msg.line,
                msg.charPosition,
                outputMsg);
        warnings.add(s);
    }
}
