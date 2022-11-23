package org.antlr.v4.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.antlr.v4.server.persistence.PersistenceLayer;
import org.antlr.v4.server.persistence.CloudStoragePersistenceLayer;
import org.antlr.v4.server.unique.DummyUniqueKeyGenerator;
import org.antlr.v4.server.unique.UniqueKeyGenerator;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.antlr.v4.server.ANTLRHttpServer.persistenceLayer;

public class ShareServlet extends DefaultServlet {
    static final ch.qos.logback.classic.Logger LOGGER =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ANTLRHttpServer.class);

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        final JsonObject jsonResponse = new JsonObject();
        try {
            response.setContentType("text/plain;charset=utf-8");
            response.setContentType("text/html;");
            response.addHeader("Access-Control-Allow-Origin", "*");

            JsonObject jsonObj = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            UniqueKeyGenerator keyGen = new DummyUniqueKeyGenerator();
            Optional<String> uniqueKey = keyGen.generateKey();
            persistenceLayer.persist(new Gson().toJson(jsonObj).getBytes(StandardCharsets.UTF_8),
                    uniqueKey.orElseThrow());

            jsonResponse.addProperty("uuid", uniqueKey.orElseThrow());
        }
        catch (Exception e) {
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
