package org.antlr.v4.server;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.antlr.v4.server.persistent.PersistenceLayer;
import org.antlr.v4.server.persistent.cloudstorage.CloudStoragePersistenceLayer;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;

public class LoadServlet extends DefaultServlet {
    static final ch.qos.logback.classic.Logger LOGGER =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ANTLRHttpServer.class);

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            String path = request.getPathInfo();
            String hash = path.substring(path.indexOf('/') + 1);
            PersistenceLayer<String> persistenceLayer = new CloudStoragePersistenceLayer();
            if ( !persistenceLayer.exists(hash) ) {
                LOGGER.info("Load: Unknown " + hash);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            LOGGER.info("Load: " + hash);
            Cookie hashCookie = new Cookie("resource_id", hash);
            hashCookie.setPath("/");
            hashCookie.setComment("__SAME_SITE_LAX__"); // Chrome apparently needs this or cookies disappear
            response.addCookie(hashCookie);
            response.sendRedirect("/index.html");
        }
        catch (Exception e) {
            PrintWriter pw = response.getWriter();
            e.printStackTrace(pw);
            pw.flush();
            response.setContentType("text/plain;charset=utf-8");
            response.setContentType("text/html;");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
