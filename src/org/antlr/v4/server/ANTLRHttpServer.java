package org.antlr.v4.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

import static org.antlr.v4.server.GrammarProcessor.interp;

public class ANTLRHttpServer {
	public static class HelloWorldServlet extends DefaultServlet {
		@Override
		public void doPost(HttpServletRequest request, HttpServletResponse response)
				throws IOException {
			response.setContentType("text/plain;charset=utf-8");
			response.setContentType("text/html;");
			response.addHeader("Access-Control-Allow-Origin", "*");

			JsonReader jsonReader = Json.createReader(request.getReader());
			JsonObject jsonObj = jsonReader.readObject();
			System.out.println(jsonObj);

			String grammar = jsonObj.getString("grammar", "");
			String lexGrammar = jsonObj.getString("lexgrammar", ""); // can be null
			String input = jsonObj.getString("input", "");
			String startRule = jsonObj.getString("start", "");

			String json;
			if ( grammar.strip().length()==0 && lexGrammar.strip().length()==0 ) {
				json = "{\"arg_error\":\"missing either combined grammar or lexer and parser both\"}";
			}
			else if ( grammar.strip().length()==0 && lexGrammar.strip().length()>0 ) {
				json = "{\"arg_error\":\"missing parser grammar\"}";
			}
			else if ( startRule.strip().length()==0 ) {
				json = "{\"arg_error\":\"missing start rule\"}";
			}
			else if ( input.length()==0 ) {
				json = "{\"arg_error\":\"missing input\"}";
			}
			else {
				try {
					json = interp(grammar, lexGrammar, input, startRule);
				}
				catch (Throwable e) {
					System.err.println("whoa");
					e.printStackTrace(System.err);
					json = "{}";
				}
			}

			response.setStatus(HttpServletResponse.SC_OK);
			PrintWriter w = response.getWriter();
			w.write(json);
			w.flush();
		}
	}

	public static void main(String[] args) throws Exception {
//		QueuedThreadPool threadPool = new QueuedThreadPool();
//		threadPool.setName("server");
//		Server server = new Server(threadPool);

		Server server = new Server(8080);

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		context.addServlet(new ServletHolder(new HelloWorldServlet()), "/antlr/*");

		ServletHolder holderHome = new ServletHolder("static-home", DefaultServlet.class);
		holderHome.setInitParameter("resourceBase", "static");
		holderHome.setInitParameter("dirAllowed","true");
		holderHome.setInitParameter("pathInfoOnly","true");
		context.addServlet(holderHome,"/static/*");

		ServletHolder testHome = new ServletHolder("static-test", DefaultServlet.class);
		testHome.setInitParameter("resourceBase", "test");
		testHome.setInitParameter("dirAllowed","true");
		testHome.setInitParameter("pathInfoOnly","true");
		context.addServlet(testHome,"/test/*");

//		ServletHolder holderPwd = new ServletHolder("default", DefaultServlet.class);
//		holderPwd.setInitParameter("dirAllowed","true");
//		context.addServlet(holderPwd,"/");

		server.setHandler(context);

		server.start();
	}
}
