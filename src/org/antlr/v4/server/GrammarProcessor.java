package org.antlr.v4.server;

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
import static org.antlr.v4.gui.Interpreter.profilerColumnNames;
import static org.antlr.v4.server.ANTLRHttpServer.IMAGES_DIR;
import static org.antlr.v4.server.ANTLRHttpServer.ParseServlet.LOGGER;
import static org.antlr.v4.server.JsonSerializer.escapeJSONString;
import static us.parr.lib.ParrtSys.execInDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrammarProcessor {
    public static final int MAX_PARSE_TIME_MS = 20 * 1000; // 20 seconds

    private static class KillableGrammarParserInterpreter extends GrammarParserInterpreter {
        private final long creationTime = System.currentTimeMillis();

        public KillableGrammarParserInterpreter(Grammar g, ATN deserializedATN, TokenStream tokenStream) {
            super(g, deserializedATN, tokenStream);
        }

        @Override
        protected void visitState(ATNState p) {
            super.visitState(p);
            long now = System.currentTimeMillis();
            if ( now - creationTime > MAX_PARSE_TIME_MS ) {
                throw new ParseCancellationException("Parser timeout ("+MAX_PARSE_TIME_MS+"ms)");
            }
        }
    }

    /** Interpret the input according to the grammar, starting at the start rule, and return a JSON object
     *  with errors, tokens, rule names, and the parse tree.
     */
    public static String interp(String grammar, String lexGrammar, String input, String startRule)
        throws IOException
    {
        startRule = startRule.strip();
        Grammar g = null;
        LexerGrammar lg = null;
        Tool antlrTool = new Tool();
        ErrorManager errMgr = new ErrorManager(antlrTool);
        errMgr.setFormat("antlr");
        CollectGrammarErrorsAndWarnings parselistener = new CollectParserGrammarErrorsAndWarnings(errMgr);
        CollectGrammarErrorsAndWarnings lexlistener = new CollectLexerGrammarErrorsAndWarnings(errMgr);
        List<String> warnings = new ArrayList<>();
        try {
            if ( lexGrammar!=null && lexGrammar.strip().length()>0 ) {
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
            System.err.println("Can't parse grammar");
        }

        String result = "{}";

        Rule r = g.rules.get(startRule);
        if (r == null) {
            String w = "No such start rule: " + startRule;
            warnings.add("{\"msg\":\""+w+"\"}");
            System.err.println(w);
        }
        else {
            if (lexlistener.errors.size() == 0 && parselistener.errors.size() == 0) {
                result = parseAndGetJSON(g, lg, startRule, input);
            }
        }

        result = String.format("{\"warnings\":[%s],"+
                        "\"parser_grammar_errors\":[%s]," +
                        "\"lexer_grammar_errors\":[%s]," +
                        "\"result\":%s}",
                String.join(",", warnings),
                String.join(",", parselistener.errors),
                String.join(",", lexlistener.errors),
                result);

        return result;
    }

    private static String parseAndGetJSON(Grammar g, LexerGrammar lg, String startRule, String input)
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

        KillableGrammarParserInterpreter parser = createGrammarParserInterpreter(g, tokens);

        CollectLexOrParseSyntaxErrors parseListener = new CollectLexOrParseSyntaxErrors();
        parser.removeErrorListeners();
        parser.addErrorListener(parseListener);
        parser.setProfile(true);

        Rule r = g.rules.get(startRule);
        ParseTree t = parser.parse(r.index);
        ParseInfo parseInfo = parser.getParseInfo();

        long now = System.currentTimeMillis();
        System.err.println("PARSE TIME: "+(now - parser.creationTime)+"ms");

//        System.out.println("lex msgs" + lexListener.msgs);
//        System.out.println("parse msgs" + parseListener.msgs);
//
//        System.out.println(t.toStringTree(parser));
        String[][] profileData = getProfilerTable(parser, parseInfo);

        TokenStream tokenStream = parser.getInputStream();
        CharStream inputStream = tokenStream.getTokenSource().getInputStream();
        String json = JsonSerializer.toJSON(
                t,
                Arrays.asList(parser.getRuleNames()),
                parser.getVocabulary(),
                tokenStream,
                inputStream,
                lexListener.msgs,
                parseListener.msgs,
                profileData);
        return json;
    }

    /** Copy this function from {@link Grammar} so we can override {@link ParserInterpreter#visitState(ATNState)} */ 
    public static KillableGrammarParserInterpreter createGrammarParserInterpreter(Grammar g, TokenStream tokenStream) {
        if (g.isLexer()) {
            throw new IllegalStateException("A parser interpreter can only be created for a parser or combined grammar.");
        }
        // must run ATN through serializer to set some state flags
        IntegerList serialized = ATNSerializer.getSerialized(g.getATN());
        ATN deserializedATN = new ATNDeserializer().deserialize(serialized.toArray());

        return new KillableGrammarParserInterpreter(g, deserializedATN, tokenStream);
    }


    private static String[][] getProfilerTable(GrammarParserInterpreter parser, ParseInfo parseInfo) {
        String[] ruleNamesByDecision = new String[parser.getATN().decisionToState.size()];
        for(int i = 0; i < ruleNamesByDecision .length; i++) {
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
        String psFileName = "temp-"+id+".ps";
        String pdfFileName = "temp-"+id+".pdf";
        String svgFileName = "temp-"+id+".svg";
        Trees.writePS(t, ruleNames, Path.of(IMAGES_DIR, psFileName).toAbsolutePath().toString());
        String ps = Files.readString(Path.of(IMAGES_DIR, psFileName));

        final String regex = "%%BoundingBox: [0-9]+ [0-9]+ ([0-9]+) ([0-9]+)";

        final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        final Matcher matcher = pattern.matcher(ps);

        int width;
        int height;
        if ( matcher.find()) {
            width = Integer.valueOf(matcher.group(1));
            height = Integer.valueOf(matcher.group(2));
        }
        else {
            LOGGER.error("Didn't match regex Iin PS: "+regex);
            width = 1000;
            height = 1000;
        }

        String[] results =
            execInDir(IMAGES_DIR, "ps2pdf",
                      "-dDEVICEWIDTHPOINTS=" + width,
                      "-dDEVICEHEIGHTPOINTS=" + height,
                    psFileName, pdfFileName);

        if (results[1].length() > 0) {
            System.err.println("ps2pdf: "+results[1]);
            String msg = results[1].strip();
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" height=\"30\" width=\"800\">\n" +
                    "  <text x=\"0\" y=\"15\" fill=\"red\">Can't create SVG tree; ps2pdf says: "+escapeJSONString(msg)+"</text>\n" +
                    "</svg>";
        }

        results = execInDir(IMAGES_DIR, "pdf2svg", pdfFileName, svgFileName);
        if (results[1].length() > 0) {
            System.err.println("pdf2svg: "+results[1]);
            String msg = results[1].strip();
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" height=\"30\" width=\"800\">\n" +
                    "  <text x=\"0\" y=\"15\" fill=\"red\">Can't create SVG tree; pdf2svg says: "+escapeJSONString(msg)+"</text>\n" +
                    "</svg>";
        }

        String svgfilename = Path.of(IMAGES_DIR, svgFileName).toAbsolutePath().toString();
        String svg = new String(Files.readAllBytes(Paths.get(svgfilename)));
        return svg;
    }
}
