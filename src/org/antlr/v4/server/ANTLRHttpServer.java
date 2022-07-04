package org.antlr.v4.server;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.antlr.runtime.RecognitionException;
import org.antlr.v4.Tool;
import org.antlr.v4.gui.Interpreter;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.DecisionInfo;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.tool.*;
import org.eclipse.jetty.http.HttpParser;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ANTLRHttpServer {
	protected static class IgnoreTokenVocabGrammar extends Grammar {
		public IgnoreTokenVocabGrammar(String fileName,
									   String grammarText,
									   Grammar tokenVocabSource,
									   ANTLRToolListener listener)
				throws RecognitionException {
			super(fileName, grammarText, tokenVocabSource, listener);
		}

		@Override
		public void importTokensFromTokensFile() {
			// don't try to import tokens files; must give me both grammars if split
		}
	}

	public static class HelloWorldServlet extends HttpServlet {
		@Override
		public void doGet(HttpServletRequest request, HttpServletResponse response)
				throws IOException {
			response.setContentType("text/plain;charset=utf-8");
			response.setContentType("text/html;");

//			response.getWriter().println("<h1>Hello world!</h1>");

//			System.out.println(request.getParameterMap());
//			JsonReader jsonReader = Json.createReader(request.getReader());
			String grammar = request.getParameter("grammar");
			String input = request.getParameter("input");
			String startRule = request.getParameter("start");
			System.out.println(grammar);
			System.out.println(input);
			System.out.println(startRule);
//			JsonReader jsonReader = Json.createReader(new StringReader(data));
//			JsonObject jobj = jsonReader.readObject();
//			System.out.println(jobj);

			interp(grammar, input, startRule);

			response.setStatus(HttpServletResponse.SC_OK);
		}
	}

	public static ParseInfo interp(String grammar, String input, String startRule) {
		grammar = grammar.replace("\r", "");
		input = input.replace("\r", "");
		startRule = startRule.strip();
		Grammar g = null;
		LexerGrammar lg = null;
		DefaultToolListener listener = new DefaultToolListener(new Tool());
		try {
			g = new IgnoreTokenVocabGrammar(null, grammar, null, listener);
		}
		catch (RecognitionException re) {
			System.err.println("Can't parse grammar");
		}

		CharStream charStream = CharStreams.fromString(input);

		LexerInterpreter lexEngine = (lg != null) ?
				lg.createLexerInterpreter(charStream) :
				g.createLexerInterpreter(charStream);

		CommonTokenStream tokens = new CommonTokenStream(lexEngine);

		tokens.fill();

		GrammarParserInterpreter parser = g.createGrammarParserInterpreter(tokens);

		Rule r = g.rules.get(startRule);
		if (r == null) {
			System.err.println("No such start rule: " + startRule);
			return null;
		}
		ParseTree t = parser.parse(r.index);
		ParseInfo parseInfo = parser.getParseInfo();

		return parseInfo;
	}

	public static void main(String[] args) throws Exception {
//		QueuedThreadPool threadPool = new QueuedThreadPool();
//		threadPool.setName("server");
//		Server server = new Server(threadPool);

		Server server = new Server(8080);

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/hello");
		context.addServlet(new ServletHolder(new HelloWorldServlet()), "/*");
		server.setHandler(context);

		server.start();
	}
}
