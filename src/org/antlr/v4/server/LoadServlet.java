package org.antlr.v4.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class LoadServlet extends DefaultServlet {
    static final ch.qos.logback.classic.Logger LOGGER =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ANTLRHttpServer.class);

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String uuid = request.getParameter("uuid");
        final JsonObject jsonResponse = new JsonObject();
        try {
            response.setContentType("text/plain;charset=utf-8");
            response.setContentType("text/html;");
            response.addHeader("Access-Control-Allow-Origin", "*");
            System.err.println("uuid: " + uuid);
            byte[] jsonBytes = ANTLRHttpServer.persistenceLayer.retrieve(uuid);
            LOGGER.info("LOAD:\n" + jsonResponse);
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter w = response.getWriter();
            w.write(new String(jsonBytes));
            w.flush();
        }
        catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            jsonResponse.addProperty("exception_trace", sw.toString());
            jsonResponse.addProperty("exception", e.getMessage());
        }
    }
}
