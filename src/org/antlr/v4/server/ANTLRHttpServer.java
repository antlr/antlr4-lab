package org.antlr.v4.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
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
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.LoggerFactory;



import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.antlr.v4.server.GrammarProcessor.interp;

public class ANTLRHttpServer {
	public static final String IMAGES_DIR = "/tmp/antlr-images";

	public static class ParseServlet extends DefaultServlet {
		static final ch.qos.logback.classic.Logger LOGGER =
				(ch.qos.logback.classic.Logger)LoggerFactory.getLogger(ANTLRHttpServer.class);

		@Override
		public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws IOException
		{

			JsonObject jsonResponse = new JsonObject();
			try {
				response.setContentType("text/plain;charset=utf-8");
				response.setContentType("text/html;");

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
				}
				else if (grammar.strip().length() == 0 && lexGrammar.strip().length() > 0) {
					jsonResponse.addProperty("arg_error", "missing parser grammar");
				}
				else if (startRule.strip().length() == 0) {
					jsonResponse.addProperty("arg_error", "missing start rule");
				}
				else if (input.length() == 0) {
					jsonResponse.addProperty("arg_error", "missing input");
				}
				else {
					try {
						jsonResponse = interp(grammar, lexGrammar, input, startRule);
					}
					catch (ParseCancellationException pce) {
						jsonResponse.addProperty("exception_trace", "parser timeout ("+GrammarProcessor.MAX_PARSE_TIME_MS+"ms)");
					}
					catch (Throwable e) {
						e.printStackTrace(System.err);
					}
				}
			}
			catch (Exception e) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				jsonResponse.addProperty("exception_trace", sw.toString());
				jsonResponse.addProperty("exception", e.getMessage());
			}
			LOGGER.info("RESULT:\n"+jsonResponse);
			response.setStatus(HttpServletResponse.SC_OK);
			PrintWriter w = response.getWriter();
			w.write(new Gson().toJson(jsonResponse));
			w.flush();
		}
	}

	public static class ShareServlet extends DefaultServlet {
		static final ch.qos.logback.classic.Logger LOGGER =
				(ch.qos.logback.classic.Logger)LoggerFactory.getLogger(ANTLRHttpServer.class);

		@Override
		public void doPost(HttpServletRequest request, HttpServletResponse response)
				throws IOException
		{
			final JsonObject jsonResponse = new JsonObject();
			try {
				response.setContentType("text/plain;charset=utf-8");
				response.setContentType("text/html;");

				JsonObject jsonObj = JsonParser.parseReader(request.getReader()).getAsJsonObject();
				PersistenceLayer<String> persistenceLayer = new CloudStoragePersistenceLayer();
				UniqueKeyGenerator keyGen = new DummyUniqueKeyGenerator();
				Optional<String> uniqueKey = keyGen.generateKey();
				persistenceLayer.persist(new Gson().toJson(jsonResponse).getBytes(StandardCharsets.UTF_8),
						uniqueKey.orElseThrow());

				jsonResponse.addProperty("resource_id", uniqueKey.orElseThrow());
			}
			catch (Exception e) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				jsonResponse.addProperty("exception_trace", sw.toString());
				jsonResponse.addProperty("exception", e.getMessage());

			}
			LOGGER.info("RESULT:\n"+jsonResponse);
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
		threadPool.setMaxThreads(10);
		threadPool.setName("server");

		Server server = new Server(threadPool);

		ServerConnector http = new ServerConnector(server);
		http.setPort(80);

		server.addConnector(http);

//		Server server = new Server(8080);

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		context.addServlet(new ServletHolder(new ParseServlet()), "/parse/*");
		context.addServlet(new ServletHolder(new ShareServlet()), "/share/*");

		ServletHolder holderHome = new ServletHolder("static-home", DefaultServlet.class);
		holderHome.setInitParameter("resourceBase", "web");
		holderHome.setInitParameter("dirAllowed","true");
		holderHome.setInitParameter("pathInfoOnly","true");
		context.addServlet(holderHome,"/*");

		FilterHolder filter = new FilterHolder(CrossOriginFilter.class);
		filter.setInitParameter("allowedMethods", "POST, OPTIONS");
		filter.setInitParameter("allowedOrigins", "*");
		context.addFilter(filter, "/*", null);

		server.setHandler(context);

		server.start();
	}
}
