package org.antlr.v4.server;

import org.antlr.runtime.RecognitionException;
import org.antlr.v4.gui.Interpreter;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ParseInfo;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class GrammarProcessor {
    /** Interpret the input according to the grammar, starting at the start rule, and return a JSON object
     *  with errors, tokens, rule names, and the parse tree.
     */
    public static String interp(String grammar, String lexGrammar, String input, String startRule) {
//        grammar = grammar.replace("\r", "");
//        input = input.replace("\r", "");
        startRule = startRule.strip();
        Grammar g = null;
        LexerGrammar lg = null;
        CollectGrammarErrorsAndWarnings listener = new CollectGrammarErrorsAndWarnings();
        try {
            if ( lexGrammar!=null ) {
                lg = new LexerGrammar(lexGrammar, listener);
                g = new IgnoreTokenVocabGrammar(null, grammar, lg, listener);
            }
            else {
                g = new IgnoreTokenVocabGrammar(null, grammar, null, listener);
            }

            if ( listener.errors.size()>0 ) {
                return String.format("{\"tool_warnings\":[%s],\"tool_errors\":[%s],\"result\":{}}",
                        String.join(",", listener.warnings),
                        String.join(",", listener.errors));
            }
        }
        catch (RecognitionException re) {
            // shouldn't get here.
            System.err.println("Can't parse grammar");
        }

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
//		System.out.println(json);

        json = String.format("{\"tool_warnings\":[%s],\"tool_errors\":[%s],\"result\":{}, \"result\":%s}",
                String.join(",", listener.warnings),
                String.join(",", listener.errors),
                json);

        return json;
    }
}
