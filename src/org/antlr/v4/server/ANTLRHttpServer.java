package org.antlr.v4.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;

import org.slf4j.LoggerFactory;



import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.antlr.v4.server.GrammarProcessor.interp;

public class ANTLRHttpServer {
	public static final String IMAGES_DIR = "/tmp/antlr-images";
	public static final int UUID_LEN = 36;

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
				response.addHeader("Access-Control-Allow-Origin", "*");

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

//	public class RouterServlet extends DefaultServlet {
//		private final Map<String, HttpServlet> routes = new HashMap<>();
//		private final HttpServlet defaultServlet;
//
//		public RouterServlet(HttpServlet defaultServlet) {
//			this.defaultServlet = defaultServlet;
//		}
//
//		public void addRoute(String path, HttpServlet servlet) {
//			routes.put(path, servlet);
//		}
//
//		@Override
//		protected void service(HttpServletRequest req, HttpServletResponse resp)
//				throws ServletException, IOException
//		{
//			String path = req.getPathInfo();
//			if (path == null) {
//				path = "/";
//			}
//			HttpServlet servlet = routes.get(path);
//			if (servlet == null) {
//				servlet = defaultServlet;
//			}
//			servlet.service(req, resp);
//		}
//	}

	public static class RouterServlet extends DefaultServlet {

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			String path = request.getPathInfo();
			if ( path.length()== UUID_LEN+1 ) { // +1 for '/' on front
				System.out.printf("UUID "+path);
				// Forward to the usual "/" ULR (index.html); doesn't change the URL in browser window
				ServletContext context = request.getServletContext();
				RequestDispatcher dispatcher = context.getRequestDispatcher(URIUtil.encodePath("/"));
				dispatcher.forward(request, response);
			}
			else {
				super.doGet(request, response);
			}
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
		context.setBaseResource(new PathResource(Paths.get("static")));
		context.addServlet(RouterServlet.class, "/*");
		context.addServlet(ParseServlet.class, "/parse/*");
		context.addServlet(ShareServlet.class, "/share/*");
		server.setHandler(context);

		server.start();
	}
}
