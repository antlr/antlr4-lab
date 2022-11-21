package org.antlr.v4.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.antlr.v4.server.persistent.PersistenceLayer;
import org.antlr.v4.server.persistent.cloudstorage.CloudStoragePersistenceLayer;
import org.antlr.v4.server.unique.DummyUniqueKeyGenerator;
import org.antlr.v4.server.unique.UniqueKeyGenerator;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Optional;

public class LoadServlet extends DefaultServlet {
    static final ch.qos.logback.classic.Logger LOGGER =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ANTLRHttpServer.class);

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            response.setContentType("text/plain;charset=utf-8");
            response.setContentType("text/html;");
            response.addHeader("Access-Control-Allow-Origin", "*");

            String path = request.getPathInfo();
            String hash = path.substring(path.indexOf('/') + 1);
            PersistenceLayer<String> persistenceLayer = new CloudStoragePersistenceLayer();
            byte[] jsonBytes = persistenceLayer.retrieve(hash);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            System.out.println(json);

            Cookie hashCookie = new Cookie("resource_id", hash);
            hashCookie.setPath("/");
            hashCookie.setComment("__SAME_SITE_LAX__"); // Chrome apparently needs this or cookies disappear
            response.addCookie(hashCookie);
            response.sendRedirect("/index.html");
        }
        catch (InvalidKeyException ike) {
            System.err.println("Invalid key");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        catch (Exception e) {
            PrintWriter pw = response.getWriter();
            e.printStackTrace(pw);
            pw.flush();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
