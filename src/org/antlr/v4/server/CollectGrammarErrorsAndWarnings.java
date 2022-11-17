package org.antlr.v4.server;

import org.antlr.v4.Tool;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ANTLRToolListener;
import org.antlr.v4.tool.ErrorManager;
import org.stringtemplate.v4.ST;

import javax.json.Json;
import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

class CollectGrammarErrorsAndWarnings implements ANTLRToolListener {
    ErrorManager errMgr;

    String fileName;

    List<JsonObject> errors = new ArrayList<>();
    List<JsonObject> warnings = new ArrayList<>();

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
        errors.add(Json.createObjectBuilder()
                           .add("type", msg.getErrorType().toString())
                           .add("line", msg.line)
                           .add("pos", msg.charPosition)
                           .add("msg", outputMsg)
                           .build());
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
        errors.add(Json.createObjectBuilder()
                           .add("type", msg.getErrorType().toString())
                           .add("line", msg.line)
                           .add("pos", msg.charPosition)
                           .add("msg", outputMsg)
                           .build());
    }
}
