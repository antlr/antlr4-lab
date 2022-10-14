package org.antlr.v4.server;

import org.antlr.runtime.RecognitionException;
import org.antlr.v4.Tool;
import org.antlr.v4.gui.Interpreter;
import org.antlr.v4.gui.Trees;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.DecisionInfo;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.tool.*;
import static org.antlr.v4.gui.Interpreter.profilerColumnNames;
import static org.antlr.v4.server.ANTLRHttpServer.IMAGES_DIR;
import static us.parr.lib.ParrtSys.execInDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class GrammarProcessor {
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

        GrammarParserInterpreter parser = g.createGrammarParserInterpreter(tokens);

        CollectLexOrParseSyntaxErrors parseListener = new CollectLexOrParseSyntaxErrors();
        parser.removeErrorListeners();
        parser.addErrorListener(parseListener);
        parser.setProfile(true);

        Rule r = g.rules.get(startRule);
        ParseTree t = parser.parse(r.index);
        ParseInfo parseInfo = parser.getParseInfo();

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
	String ps_fn = Path.of(IMAGES_DIR, "temp.ps").toAbsolutePath().toString();
        Trees.writePS(t, ruleNames, ps_fn);

	// Open PS file and pick off line with '%%BoundingBox: 0 0 3857
	// 414'.
	File file = new File(ps_fn);
	BufferedReader br = new BufferedReader(new FileReader(file));
	br.readLine();
	String string = br.readLine();
	//file.close();
	int i2 = string.lastIndexOf(' ');
	int i1 = string.lastIndexOf(' ', i2 - 1);
	String s1 = string.substring(i1+1, i2);
	String s2 = string.substring(i2+1);
	int width = 1000;
	try{
	    width = Integer.parseInt(s1);
	}
	catch (NumberFormatException ex){
	    ex.printStackTrace();
	}
	int height = 1000;
	try{
	    height = Integer.parseInt(s2);
	}
	catch (NumberFormatException ex){
	    ex.printStackTrace();
	}

	String[] results = execInDir(IMAGES_DIR, "ps2pdf",
				     "-dDEVICEWIDTHPOINTS=" + width,
				     "-dDEVICEHEIGHTPOINTS=" + height,
				     "temp.ps", "temp.pdf");
        if (results[1].length() > 0) {
            System.err.println(results[1]);
	}

//        results = execInDir(IMAGES_DIR, "pdfcrop", "--hires", "temp.pdf"); // creates temp-crop.pdf
//        if (results[1].length() > 0) {
//            System.err.println(results[1]);
//        }

	results = execInDir(IMAGES_DIR, "pdf2svg", "temp.pdf", "temp.svg");
        if (results[1].length() > 0) {
            System.err.println(results[1]);
        }

        String svgfilename = Path.of(IMAGES_DIR, "temp.svg").toAbsolutePath().toString();
        String svg = new String(Files.readAllBytes(Paths.get(svgfilename)));
        return svg;
    }
}
