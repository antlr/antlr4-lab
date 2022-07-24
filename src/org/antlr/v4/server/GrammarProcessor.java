package org.antlr.v4.server;

import org.antlr.runtime.RecognitionException;
import org.antlr.v4.gui.Interpreter;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.DecisionInfo;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.tool.*;
import static org.antlr.v4.gui.Interpreter.profilerColumnNames;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GrammarProcessor {
    /** Interpret the input according to the grammar, starting at the start rule, and return a JSON object
     *  with errors, tokens, rule names, and the parse tree.
     */
    public static String interp(String grammar, String lexGrammar, String input, String startRule) {
        startRule = startRule.strip();
        Grammar g = null;
        LexerGrammar lg = null;
        CollectGrammarErrorsAndWarnings lexlistener = new CollectGrammarErrorsAndWarnings();
        CollectGrammarErrorsAndWarnings parselistener = new CollectGrammarErrorsAndWarnings();
        List<String> warnings = new ArrayList<>();
        try {
            if ( lexGrammar!=null ) {
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
        if ( lexlistener.errors.size()==0 && parselistener.errors.size()==0 ) {
            result = parseAndGetJSON(g, lg, startRule, input);
//		System.out.println(result);
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

    private static String parseAndGetJSON(Grammar g, LexerGrammar lg, String startRule, String input) {
//        CharStream charStream = CharStreams.fromString(input);
        CharStream charStream = null;
        try {
            charStream = CharStreams.fromStream(new StringBufferInputStream(input));
        }
        catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }

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
        if (r == null) {
            System.err.println("No such start rule: " + startRule);
            return null;
        }
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
}
