package org.antlr.v4.server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.antlr.v4.server.persistent.PersistenceLayer;
import org.antlr.v4.server.persistent.cloudstorage.CloudStoragePersistenceLayer;
import org.antlr.v4.server.unique.DummyUniqueKeyGenerator;
import org.antlr.v4.server.unique.UniqueKeyGenerator;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.LoggerFactory;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Optional;

import static org.antlr.v4.server.GrammarProcessor.interp;

public class ANTLRHttpServer {
    public static final String IMAGES_DIR = "/tmp/antlr-images";

    public static class ParseServlet extends DefaultServlet {
        static final ch.qos.logback.classic.Logger LOGGER = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ANTLRHttpServer.class);

        @Override
        public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            LOGGER.info("INITIATE REQUEST IP: "+request.getRemoteAddr()+
                    ", Content-Length: "+request.getContentLength());
            logMemoryInfo("BEFORE PROCESSING FROM IP: "+request.getRemoteAddr());
            JsonObject jsonResponse = new JsonObject();
            try {
                response.setContentType("text/plain;charset=utf-8");
                response.setContentType("text/html;");
                response.addHeader("Access-Control-Allow-Origin", "*");

                JsonObject jsonObj = JsonParser.parseReader(request.getReader()).getAsJsonObject();

                String grammar = jsonObj.get("grammar").getAsString();
                String lexGrammar = jsonObj.get("lexgrammar").getAsString(); // can be null
                String input = jsonObj.get("input").getAsString();
                String startRule = jsonObj.get("start").getAsString();

                StringBuilder logMsg = new StringBuilder();
                logMsg.append("GRAMMAR:\n");
                logMsg.append(grammar);
                logMsg.append("\nLEX GRAMMAR:\n");
                logMsg.append(lexGrammar);
                logMsg.append("\nINPUT ("+input.length()+" char):\n\"\"\"");
                logMsg.append(input);
                logMsg.append("\"\"\"\n");
                logMsg.append("STARTRULE: ");
                logMsg.append(startRule);
                logMsg.append('\n');
                LOGGER.info(logMsg.toString());

                if (grammar.isBlank() && lexGrammar.isBlank()) {
                    jsonResponse.addProperty("arg_error", "missing either combined grammar or lexer and " + "parser both");
                }
                else if (grammar.isBlank()) {
                    jsonResponse.addProperty("arg_error", "missing parser grammar");
                }
                else if (startRule.isBlank()) {
                    jsonResponse.addProperty("arg_error", "missing start rule");
                }
                else if (input.isEmpty()) {
                    jsonResponse.addProperty("arg_error", "missing input");
                }
                else {
                    try {
                        jsonResponse = interp(grammar, lexGrammar, input, startRule);
                    }
                    catch (ParseCancellationException pce) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        pce.printStackTrace(pw);
                        jsonResponse.addProperty("exception", pce.getMessage());
                        jsonResponse.addProperty("exception_trace", sw.toString());
                        LOGGER.warn(pce.toString());
                    }
                }
            }
            catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                jsonResponse.addProperty("exception", e.getMessage());
                jsonResponse.addProperty("exception_trace", sw.toString());
                LOGGER.error("PARSER FAILED", e);
            }

            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter w = response.getWriter();
            w.write(new Gson().toJson(jsonResponse));
            w.flush();

            // Don't save SVG tree in log; usually too big
            JsonElement result = jsonResponse.get("result");
            if ( result!=null && ((JsonObject) result).has("svgtree") ) {
                ((JsonObject) result).remove("svgtree");
            }
            logMemoryInfo("AFTER PARSE FROM IP: "+request.getRemoteAddr());
            LOGGER.info("RESULT:\n" + jsonResponse);
        }
    }

    private static void logMemoryInfo(String prefix) {
        Runtime.getRuntime().gc();
        var fm = Runtime.getRuntime().freeMemory();
        var tm = Runtime.getRuntime().totalMemory();
        NumberFormat.getInstance().format(fm);
        ParseServlet.LOGGER.info(prefix + " memory: free=" + NumberFormat.getInstance().format(fm) + " bytes" +
                ", total=" + NumberFormat.getInstance().format(tm) + " bytes");
    }

    public static class ShareServlet extends DefaultServlet {
        static final ch.qos.logback.classic.Logger LOGGER = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ANTLRHttpServer.class);

        @Override
        public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            final JsonObject jsonResponse = new JsonObject();
            try {
                response.setContentType("text/plain;charset=utf-8");
                response.setContentType("text/html;");
                response.addHeader("Access-Control-Allow-Origin", "*");

                JsonObject jsonObj = JsonParser.parseReader(request.getReader()).getAsJsonObject();
                PersistenceLayer<String> persistenceLayer = new CloudStoragePersistenceLayer();
                UniqueKeyGenerator keyGen = new DummyUniqueKeyGenerator();
                Optional<String> uniqueKey = keyGen.generateKey();
                persistenceLayer.persist(new Gson().toJson(jsonResponse).getBytes(StandardCharsets.UTF_8), uniqueKey.orElseThrow());

                jsonResponse.addProperty("resource_id", uniqueKey.orElseThrow());
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

    public static void main(String[] args) throws Exception {
        new File(IMAGES_DIR).mkdirs();

        Files.createDirectories(Path.of("/var/log/antlrlab"));
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(5);
        threadPool.setName("server");

        Server server = new Server(threadPool);

        ServerConnector http = new ServerConnector(server);
        http.setPort(80);

        server.addConnector(http);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(new ParseServlet()), "/parse/*");
        context.addServlet(new ServletHolder(new ShareServlet()), "/share/*");

        ServletHolder holderHome = new ServletHolder("static-home", DefaultServlet.class);
        holderHome.setInitParameter("resourceBase", "static");
        holderHome.setInitParameter("dirAllowed", "true");
        holderHome.setInitParameter("pathInfoOnly", "true");
        context.addServlet(holderHome, "/*");

        server.setHandler(context);

        server.start();
    }
}
