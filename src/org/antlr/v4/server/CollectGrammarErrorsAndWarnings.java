package org.antlr.v4.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ANTLRToolListener;
import org.antlr.v4.tool.ErrorManager;
import org.stringtemplate.v4.ST;

class CollectGrammarErrorsAndWarnings implements ANTLRToolListener {
    ErrorManager errMgr;

    String fileName;

    final JsonArray errors = new JsonArray();
    final JsonArray warnings = new JsonArray();

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
        // hack to remove "lexer:" fileName etc...
        outputMsg = outputMsg.replace(": "+fileName+":", ": ");
        if ( errMgr.formatWantsSingleLineMessage() ) {
            outputMsg = outputMsg.replace('\n', ' ');
        }

        // Strip "(126)" from "error(126): "
        outputMsg = outputMsg.replaceAll("error\\(.*?\\)", "error");
        final JsonObject jsonOutput = new JsonObject();
        jsonOutput.addProperty("type", msg.getErrorType().toString());
        jsonOutput.addProperty("line", msg.line);
        jsonOutput.addProperty("pos", msg.charPosition);
        jsonOutput.addProperty("msg", outputMsg);
        errors.add(jsonOutput);
    }

    @Override
    public void warning(ANTLRMessage msg) {
        msg.fileName = fileName;
        ST msgST = errMgr.getMessageTemplate(msg);
        String outputMsg = msgST.render();
        // hack to remove "lexer:" fileName etc...
        outputMsg = outputMsg.replace(": "+fileName+":", ": ");
        if ( errMgr.formatWantsSingleLineMessage() ) {
            outputMsg = outputMsg.replace('\n', ' ');
        }

        outputMsg = outputMsg.replaceAll("warning\\(.*?\\)", "warning");
        final JsonObject jsonOutput = new JsonObject();
        jsonOutput.addProperty("type", msg.getErrorType().toString());
        jsonOutput.addProperty("line", msg.line);
        jsonOutput.addProperty("pos", msg.charPosition);
        jsonOutput.addProperty("msg", outputMsg);
        errors.add(jsonOutput);
    }
}
