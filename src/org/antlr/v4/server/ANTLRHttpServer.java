package org.antlr.v4.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.antlr.runtime.RecognitionException;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.misc.Utils;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.tool.*;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ANTLRHttpServer {
	static class GrammarErrorListener implements ANTLRToolListener {
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();

		@Override
		public void info(String msg) { }

		@Override
		public void error(ANTLRMessage msg) {
			String errText = (String)msg.getArgs()[0];
			errText = Utils.escapeJSONString(errText);
			String s = String.format("{\"type\":\"%s\",\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
					msg.getErrorType().toString(),
					msg.line,
					msg.charPosition,
					errText);
			errors.add(s);
		}
		@Override
		public void warning(ANTLRMessage msg) {
			String errText = (String)msg.getArgs()[0];
			errText = Utils.escapeJSONString(errText);
			String s = String.format("{\"type\":\"%s\",\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
					msg.getErrorType().toString(),
					msg.line,
					msg.charPosition,
					errText);
			warnings.add(s);
		}
	}

	static class ParseErrorListener extends BaseErrorListener {
		List<String> msgs = new ArrayList<>();
		@Override
		public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
								int line, int charPositionInLine,
								String msg,
								org.antlr.v4.runtime.RecognitionException e)
		{
			msg = Utils.escapeJSONString(msg);
			String err;
			if ( offendingSymbol != null) {
				err = String.format("{\"token\":%d,\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
						((Token) (offendingSymbol)).getTokenIndex(), line, charPositionInLine, msg);
			}
			else {
				err = String.format("{\"line\":%d,\"pos\":%d,\"msg\":\"%s\"}",
						line, charPositionInLine, msg);
			}
			msgs.add(err);
		}
	}

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
//			System.out.println(grammar);
//			System.out.println(input);
//			System.out.println(startRule);
//			JsonReader jsonReader = Json.createReader(new StringReader(data));
//			JsonObject jobj = jsonReader.readObject();
//			System.out.println(jobj);

			String json = interp(grammar, input, startRule);

			response.setStatus(HttpServletResponse.SC_OK);
			PrintWriter w = response.getWriter();
			w.write(json);
			w.flush();
		}
	}

	public static String interp(String grammar, String input, String startRule) {
		grammar = grammar.replace("\r", "");
		input = input.replace("\r", "");
		startRule = startRule.strip();
		Grammar g = null;
		LexerGrammar lg = null;
//		DefaultToolListener listener = new DefaultToolListener(new Tool());
		GrammarErrorListener listener = new GrammarErrorListener();
		try {
			g = new IgnoreTokenVocabGrammar(null, grammar, null, listener);
			System.err.println("grammar warns" + listener.warnings);
			System.err.println("grammar errors" + listener.errors);

			return String.format("{\"tool_warnings\":[%s],\"tool_errors\":[%s],\"result\":{}}",
					String.join(",", listener.warnings),
					String.join(",", listener.errors));
		}
		catch (RecognitionException re) {
			// shouldn't get here.
			System.err.println("Can't parse grammar");
		}

		CharStream charStream = CharStreams.fromString(input);

		LexerInterpreter lexEngine = (lg != null) ?
				lg.createLexerInterpreter(charStream) :
				g.createLexerInterpreter(charStream);

		ParseErrorListener lexListener = new ParseErrorListener();
		lexEngine.removeErrorListeners();
		lexEngine.addErrorListener(lexListener);

		CommonTokenStream tokens = new CommonTokenStream(lexEngine);

		tokens.fill();

		GrammarParserInterpreter parser = g.createGrammarParserInterpreter(tokens);

		ParseErrorListener parseListener = new ParseErrorListener();
		parser.removeErrorListeners();
		parser.addErrorListener(parseListener);

		Rule r = g.rules.get(startRule);
		if (r == null) {
			System.err.println("No such start rule: " + startRule);
			return null;
		}
		ParseTree t = parser.parse(r.index);
		ParseInfo parseInfo = parser.getParseInfo();

		System.out.println("lex msgs" + lexListener.msgs);
		System.out.println("parse msgs" + parseListener.msgs);

		System.out.println(t.toStringTree(parser));

		TokenStream tokenStream = parser.getInputStream();
		CharStream inputStream = tokenStream.getTokenSource().getInputStream();
		String json = JsonSerializer.toJSON(
				t,
				Arrays.asList(parser.getRuleNames()),
				tokenStream,
				inputStream,
				lexListener.msgs,
				parseListener.msgs);
//		System.out.println(json);

		return json;
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
