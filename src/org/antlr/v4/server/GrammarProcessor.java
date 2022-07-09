package org.antlr.v4.server;

import org.antlr.runtime.RecognitionException;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.tool.*;

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

//            if ( lexlistener.errors.size()>0 || parselistener.errors.size()>0 ) {
//                return String.format("{\"tool_warnings\":[%s],\"tool_parser_grammar_errors\":[%s],"+
//                                "\"tool_lexer_grammar_errors\":[%s],"+
//                                "\"result\":{}, \"result\":{}}",
//                        String.join(",", warnings),
//                        String.join(",", parselistener.errors),
//                        String.join(",", lexlistener.errors));
//            }
        }
        catch (RecognitionException re) {
            // shouldn't get here.
            System.err.println("Can't parse grammar");
        }

        String result = "{}";
        if ( lexlistener.errors.size()==0 && parselistener.errors.size()==0 ) {
            result = getParseResultJSON(g, lg, startRule, input);
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
//        if ( lexlistener.errors.size()>0 ) {
//            json = String.format("{\"tool_warnings\":[%s],\"tool_parser_grammar_errors\":[%s],\"tool_lexer_grammar_errors\":[%s],\"result\":{}, \"result\":%s}",
//                    String.join(",", warnings),
//                    String.join(",", parselistener.errors),
//                    String.join(",", lexlistener.errors),
//                    json);
//        }
//        else {
//            json = String.format("{\"tool_warnings\":[%s],\"tool_parser_grammar_errors\":[%s],\"result\":{}, \"result\":%s}",
//                    String.join(",", warnings),
//                    String.join(",", parselistener.errors),
//                    json);
//        }

        return result;
    }

    private static String getParseResultJSON(Grammar g, LexerGrammar lg, String startRule, String input) {
        CharStream charStream = CharStreams.fromString(input);

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
        return json;
    }
}
