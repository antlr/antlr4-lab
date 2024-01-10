package org.antlr.v4.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.antlr.v4.server.GrammarProcessor.interp;

public class ParseServlet extends DefaultServlet {
    static final ch.qos.logback.classic.Logger LOGGER =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ANTLRHttpServer.class);

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        JsonObject jsonResponse = new JsonObject();
        try {
            response.setContentType("text/plain;charset=utf-8");
            response.setContentType("text/html;");
            response.addHeader("Access-Control-Allow-Origin", "*");

            JsonObject jsonObj = JsonParser.parseReader(request.getReader()).getAsJsonObject();
//				System.out.println(jsonObj);

            String grammar = jsonObj.get("grammar").getAsString();
            String lexGrammar = jsonObj.get("lexgrammar").getAsString(); // can be null
            String input = jsonObj.get("input").getAsString();
            String startRule = jsonObj.get("start").getAsString();

            StringBuilder logMsg = new StringBuilder();
            logMsg.append("GRAMMAR:\n");
            logMsg.append(grammar);
            logMsg.append("\nLEX GRAMMAR:\n");
            logMsg.append(lexGrammar);
            logMsg.append("\nINPUT:\n\"\"\"");
            logMsg.append(input);
            logMsg.append("\"\"\"\n");
            logMsg.append("STARTRULE: ");
            logMsg.append(startRule);
            logMsg.append('\n');
            LOGGER.info(logMsg.toString());

            if (grammar.strip().length() == 0 && lexGrammar.strip().length() == 0) {
                jsonResponse.addProperty("arg_error", "missing either combined grammar or lexer and " +
                        "parser both");
            } else if (grammar.strip().length() == 0 && lexGrammar.strip().length() > 0) {
                jsonResponse.addProperty("arg_error", "missing parser grammar");
            } else if (startRule.strip().length() == 0) {
                jsonResponse.addProperty("arg_error", "missing start rule");
            } else if (input.length() == 0) {
                jsonResponse.addProperty("arg_error", "missing input");
            } else {
                try {
                    jsonResponse = interp(grammar, lexGrammar, input, startRule);
                } catch (ParseCancellationException pce) {
                    jsonResponse.addProperty("exception_trace", "parser timeout (" + GrammarProcessor.MAX_PARSE_TIME_MS + "ms)");
                } catch (Throwable e) {
                    e.printStackTrace(System.err);
                }
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            jsonResponse.addProperty("exception_trace", sw.toString());
            jsonResponse.addProperty("exception", e.getMessage());
        }
        LOGGER.info("RESULT:\n" + jsonResponse);
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter w = response.getWriter();
        w.write(new Gson().toJson(jsonResponse));
        w.flush();
    }
}
