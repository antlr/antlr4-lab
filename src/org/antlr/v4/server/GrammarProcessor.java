package org.antlr.v4.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.antlr.runtime.RecognitionException;
import org.antlr.v4.Tool;
import org.antlr.v4.gui.Interpreter;
import org.antlr.v4.gui.Trees;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.misc.IntegerList;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.tool.*;
import us.parr.lib.ParrtSys;

import static org.antlr.v4.gui.Interpreter.profilerColumnNames;
import static org.antlr.v4.server.ANTLRHttpServer.IMAGES_DIR;
import static org.antlr.v4.server.ANTLRHttpServer.ParseServlet.LOGGER;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrammarProcessor {
    public static final int MAX_PARSE_TIME_MS = 10 * 1000; // 10 seconds
    public static final int MAX_TREE_SIZE_IN_NODES = 50_000;

    private static class KillableGrammarParserInterpreter extends GrammarParserInterpreter {
        private final long creationTime = System.currentTimeMillis();
        protected String startRule;

        public KillableGrammarParserInterpreter(Grammar g,
                                                ATN deserializedATN,
                                                String startRule,
                                                TokenStream tokenStream) {
            super(g, deserializedATN, tokenStream);
            this.startRule = startRule;
        }

        @Override
        protected void visitState(ATNState p) {
            super.visitState(p);
            long now = System.currentTimeMillis();
            long runTimeMs = now - creationTime;
            if (runTimeMs > MAX_PARSE_TIME_MS) {
                String msg = "Parser timeout (" + MAX_PARSE_TIME_MS + "ms) in rule " + startRule;
                throw new ParseCancellationException(msg);
            }
        }
    }

    /**
     * Interpret the input according to the grammar, starting at the start rule, and return a JSON object
     * with errors, tokens, rule names, and the parse tree.
     */
    public static JsonObject interp(String grammar, String lexGrammar, String input, String startRule)
            throws IOException {
        startRule = startRule.strip();
        Grammar g = null;
        LexerGrammar lg = null;
        Tool antlrTool = new Tool();
        ErrorManager errMgr = new ErrorManager(antlrTool);
        errMgr.setFormat("antlr");
        CollectGrammarErrorsAndWarnings parselistener = new CollectParserGrammarErrorsAndWarnings(errMgr);
        CollectGrammarErrorsAndWarnings lexlistener = new CollectLexerGrammarErrorsAndWarnings(errMgr);
        final JsonArray warnings = new JsonArray();
        try {
            if (lexGrammar != null && lexGrammar.strip().length() > 0) {
                lg = new LexerGrammar(lexGrammar, lexlistener);
                g = new IgnoreTokenVocabGrammar(null, grammar, lg, parselistener);
            }
            else {
                g = new IgnoreTokenVocabGrammar(null, grammar, null, parselistener);
            }

            warnings.addAll(lexlistener.warnings);
            warnings.addAll(parselistener.warnings);
        }
        catch (RecognitionException re) {
            // shouldn't get here.
            LOGGER.info("Can't parse grammar");
        }

        JsonObject result = new JsonObject();

        Rule r = g.rules.get(startRule);
        if (r == null) {
            String w = "No such start rule: " + startRule;
            LOGGER.error(w);
            final JsonObject jsonError = new JsonObject();
            jsonError.addProperty("msg", w);
            warnings.add(jsonError);
        }
        else {
            if (lexlistener.errors.size() == 0 && parselistener.errors.size() == 0) {
                result = parseAndGetJSON(g, lg, startRule, input);
            }
        }

        final JsonObject jsonResponse = new JsonObject();
        jsonResponse.add("warnings", warnings);
        jsonResponse.add("parser_grammar_errors", parselistener.errors);
        jsonResponse.add("lexer_grammar_errors", lexlistener.errors);
        jsonResponse.add("result", result);
        return jsonResponse;
    }

    private static JsonObject parseAndGetJSON(Grammar g, LexerGrammar lg, String startRule, String input)
            throws IOException
    {
        CharStream charStream = CharStreams.fromStream(new StringBufferInputStream(input));

        LexerInterpreter lexEngine = (lg != null) ?
                lg.createLexerInterpreter(charStream) :
                g.createLexerInterpreter(charStream);

        CollectLexOrParseSyntaxErrors lexListener = new CollectLexOrParseSyntaxErrors();
        lexEngine.removeErrorListeners();
        lexEngine.addErrorListener(lexListener);

        CommonTokenStream tokens = new CommonTokenStream(lexEngine);

        tokens.fill();

        KillableGrammarParserInterpreter parser = createGrammarParserInterpreter(g, startRule, tokens);

        CollectLexOrParseSyntaxErrors parseListener = new CollectLexOrParseSyntaxErrors();
        parser.removeErrorListeners();
        parser.addErrorListener(parseListener);
        parser.setProfile(true);

        Rule r = g.rules.get(startRule);
        ParseTree t = parser.parse(r.index);
        ParseInfo parseInfo = parser.getParseInfo();

        int n = nodeCount(t);
        if ( n > MAX_TREE_SIZE_IN_NODES ) {
            var msg = "Tree size "+n+" nodes > max of "+MAX_TREE_SIZE_IN_NODES;
            throw new ParseCancellationException(msg);
        }

        long now = System.currentTimeMillis();
//        LOGGER.info("PARSE TIME: "+(now - parser.creationTime)+"ms");

//        System.out.println("lex msgs" + lexListener.msgs);
//        System.out.println("parse msgs" + parseListener.msgs);
//
//        System.out.println(t.toStringTree(parser));
        String[][] profileData = getProfilerTable(parser, parseInfo);

        TokenStream tokenStream = parser.getInputStream();
//        CharStream inputStream = tokenStream.getTokenSource().getInputStream();
        CharStream inputStream = null; // don't send input back to client (they have it and it can be big)
        return JsonSerializer.toJSON(
                t,
                Arrays.asList(parser.getRuleNames()),
                parser.getVocabulary(),
                tokenStream,
                inputStream,
                lexListener.msgs,
                parseListener.msgs,
                profileData);
    }

    /**
     * Copy this function from {@link Grammar} so we can override {@link ParserInterpreter#visitState(ATNState)}
     */
    public static KillableGrammarParserInterpreter createGrammarParserInterpreter(Grammar g,
                                                                                  String startRule,
                                                                                  TokenStream tokenStream) {
        if (g.isLexer()) {
            throw new IllegalStateException("A parser interpreter can only be created for a parser or combined grammar.");
        }
        // must run ATN through serializer to set some state flags
        IntegerList serialized = ATNSerializer.getSerialized(g.getATN());
        ATN deserializedATN = new ATNDeserializer().deserialize(serialized.toArray());

        return new KillableGrammarParserInterpreter(g, deserializedATN, startRule, tokenStream);
    }


    private static String[][] getProfilerTable(GrammarParserInterpreter parser, ParseInfo parseInfo) {
        String[] ruleNamesByDecision = new String[parser.getATN().decisionToState.size()];
        for (int i = 0; i < ruleNamesByDecision.length; i++) {
            ruleNamesByDecision[i] = parser.getRuleNames()[parser.getATN().getDecisionState(i).ruleIndex];
        }

        DecisionInfo[] decisionInfo = parseInfo.getDecisionInfo();
        String[][] table = new String[decisionInfo.length][profilerColumnNames.length];

        for (int decision = 0; decision < decisionInfo.length; decision++) {
            for (int col = 0; col < profilerColumnNames.length; col++) {
                Object colVal = Interpreter.getValue(decisionInfo[decision], ruleNamesByDecision, decision, col);
                table[decision][col] = colVal.toString();
            }
        }

        return table;
    }

    public static String toSVG(Tree t, List<String> ruleNames) throws IOException {
        long id = Thread.currentThread().getId();
        String psFileName = "temp-" + id + ".ps";
        String pdfFileName = "temp-" + id + ".pdf";
        String svgFileName = "temp-" + id + ".svg";
        Trees.writePS(t, ruleNames, Path.of(IMAGES_DIR, psFileName).toAbsolutePath().toString());
        String ps = Files.readString(Path.of(IMAGES_DIR, psFileName));

        final String regex = "%%BoundingBox: [0-9]+ [0-9]+ ([0-9]+) ([0-9]+)";

        final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        final Matcher matcher = pattern.matcher(ps);

        int width;
        int height;
        if (matcher.find()) {
            width = Integer.valueOf(matcher.group(1));
            height = Integer.valueOf(matcher.group(2));
        }
        else {
            LOGGER.error("Didn't match regex in PS: " + regex);
            width = 1000;
            height = 1000;
        }

        String[] results =
                execInDir(IMAGES_DIR, "ps2pdf",
                        "-dDEVICEWIDTHPOINTS=" + width,
                        "-dDEVICEHEIGHTPOINTS=" + height,
                        psFileName, pdfFileName);

        if (results[1].length() > 0) {
            LOGGER.info("ps2pdf: " + results[1]);
            String msg = results[1].strip();
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" height=\"30\" width=\"800\">\n" +
                    "  <text x=\"0\" y=\"15\" fill=\"red\">Can't create SVG tree; ps2pdf says: " + msg + "</text>\n" +
                    "</svg>";
        }

        results = execInDir(IMAGES_DIR, "pdf2svg", pdfFileName, svgFileName);
        if (results[1].length() > 0) {
            LOGGER.info("pdf2svg: " + results[1]);
            String msg = results[1].strip();
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" height=\"30\" width=\"800\">\n" +
                    "  <text x=\"0\" y=\"15\" fill=\"red\">Can't create SVG tree; pdf2svg says: " + msg + "</text>\n" +
                    "</svg>";
        }

        String svgfilename = Path.of(IMAGES_DIR, svgFileName).toAbsolutePath().toString();
        String svg = new String(Files.readAllBytes(Paths.get(svgfilename)));
        return svg;
    }

    private static String[] execInDir(String imagesDir, String program, String... args) {
        return ParrtSys.exec("env --chdir=" + imagesDir + " " + program + " " + String.join(" ", args));
    }

    public static final int nodeCount(Tree t) {
        if (t == null) {
            return 0;
        }
        int n = 1;
        for (int i = 0; i < t.getChildCount(); i++) {
            n += nodeCount(t.getChild(i));
        }
        return n;
    }

    /** A test main program for the "big" dir grammar */
    public static void main(String[] args) throws IOException {
        new File(IMAGES_DIR).mkdirs();
        var base = "/Users/parrt/antlr/code/antlr4-lab/big/";
        String parserContent = Files.readString(Path.of(base + "TPSParser.g4"));
        String lexerContent = Files.readString(Path.of(base + "TPSLexer.g4"));
        String input = Files.readString(Path.of(base + "fonline.clc"));
        var json = interp(parserContent, lexerContent, input, "program");
//        System.out.println(json);
    }
}
