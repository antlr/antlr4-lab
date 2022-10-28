package org.antlr.v4.server;

import org.antlr.v4.gui.Interpreter;
import org.antlr.v4.gui.Trees;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.Triple;
import org.antlr.v4.runtime.misc.Utils;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.tool.ANTLRMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.antlr.v4.gui.Interpreter.profilerColumnNames;
import static org.antlr.v4.server.ANTLRHttpServer.IMAGES_DIR;
import static org.antlr.v4.server.GrammarProcessor.toSVG;
import static us.parr.lib.ParrtSys.execInDir;

// TODO: Ultimately this will go into the ANTLR core and then we can remove this class

/** This "class" wraps support functions that generate JSON for parse trees.
 *  The JSON includes everything needed to reconstruct a parse tree:
 *
 *  	Rule names (field: "rules")
 *  	Input chars (field: "input")
 *  	Tokens (field: "tokens")
 *  	Parse tree (field: "tree"; refs rule indexes and token indexes)
 *
 *  For example, given input "99" and a simple expression grammar giving parse tree
 *  "(s (expr 99) EOF)", the full JSON (formatted by jq) looks like:
 *
 * 	{
 * 	  "rules": [
 * 	    "s",
 * 	    "expr"
 * 	  ],
 * 	  "input": "99",
 * 	  "tokens": [
 * 	    {
 * 	      "type": 3,
 * 	      "line": 1,
 * 	      "pos": 0,
 * 	      "channel": 0,
 * 	      "start": 0,
 * 	      "stop": 1
 * 	    },
 * 	    {
 * 	      "type": -1,
 * 	      "line": 1,
 * 	      "pos": 2,
 * 	      "channel": 0,
 * 	      "start": 2,
 * 	      "stop": 1
 * 	    }
 * 	  ],
 * 	  TODO: this isn't up to date
 * 	  "tree": {
 * 	    "0": [
 * 	      {
 * 	        "1": [
 * 	          0
 * 	        ]
 * 	      },
 * 	      1
 * 	    ]
 * 	  }
 * 	}
 *
 *  Notice that the tree is just a series of nested references to integers, which refer to rules
 *  and tokens.
 *
 *  One potential use case: Create an ANTLR server that accepts a grammar and input as parameters then
 *  returns JSON for the parse tree and the tokens.  This can be deserialized by JavaScript in a web browser
 *  to display the parse result.
 *
 *  To load and dump elements from Python 3:
 *
 *    import json
 *
 *    with open("/tmp/t.json") as f:
 *         data = f.read()
 *
 *    data = json.loads(data)
 *    print(data['rules'])
 *    print(data['input'])
 *    for t in data['tokens']:
 *         print(t)
 *    print(data['tree'])
 *
 *  @since 4.10.2
 */
public class JsonSerializer {
    /** Create a JSON representation of a parse tree and include all other information necessary to reconstruct
     *  a printable parse tree: the rules, input, tokens, and the tree structure that refers to the rule
     *  and token indexes.  Extract all information from the parser, which is assumed to be in a state
     *  post-parse and the object that created tree t.
     *
     * @param t The parse tree to serialize as JSON
     * @param recog The parser that created the parse tree and is in the post-recognition state
     * @return JSON representing the parse tree
     */
    public static String toJSON(Tree t, Parser recog) throws IOException {
        String[] ruleNames = recog != null ? recog.getRuleNames() : null;
        if ( t==null || ruleNames==null ) {
            return null;
        }
        TokenStream tokenStream = recog.getInputStream();
        CharStream inputStream = tokenStream.getTokenSource().getInputStream();
        return toJSON(t, Arrays.asList(ruleNames), recog.getVocabulary(), tokenStream, inputStream,
                null, null, null);
    }

    /** Create a JSON representation of a parse tree and include all other information necessary to reconstruct
     *  a printable parse tree: the rules, input, tokens, and the tree structure that refers to the rule
     *  and token indexes.  The tree and rule names are required but the token stream and input stream are optional.
     */
    public static String toJSON(Tree t,
                                final List<String> ruleNames,
                                final Vocabulary vocabulary,
                                final TokenStream tokenStream,
                                final CharStream inputStream,
                                List<String> lexMsgs,
                                List<String> parseMsgs,
                                String[][] profileData)
        throws IOException
    {
        if ( t==null || ruleNames==null ) {
            return null;
        }

        StringBuilder buf = new StringBuilder();
        buf.append("{");
        buf.append("\"rules\":[\"");
        buf.append(String.join("\",\"", ruleNames));
        buf.append("\"],");

        if ( inputStream!=null ) {
            Interval allchar = Interval.of(0, inputStream.size() - 1);
            String input = inputStream.getText(allchar);
            input = JsonSerializer.escapeJSONString(input);
            buf.append("\"input\":\"");
            buf.append(input);
            buf.append("\",");
        }

        if ( vocabulary!=null ) {
            List<String> syms = new ArrayList<>();
            buf.append("\"symbols\":[");
            for (int i = 0; i < vocabulary.getMaxTokenType(); i++) {
                syms.add('"'+vocabulary.getSymbolicName(i)+'"');
            }
            buf.append(String.join(",", syms));
            buf.append("],");
        }

        if ( tokenStream!=null ) {
            List<String> tokenStrings = new ArrayList<>();
            for (int i = 0; i < tokenStream.size(); i++) {
                Token tok = tokenStream.get(i);
                String s = String.format("{\"type\":%d,\"line\":%d,\"pos\":%d,\"channel\":%d,\"start\":%d,\"stop\":%d}",
                        tok.getType(), tok.getLine(), tok.getCharPositionInLine(), tok.getChannel(),
                        tok.getStartIndex(), tok.getStopIndex());
                tokenStrings.add(s);
            }
            buf.append("\"tokens\":[");
            buf.append(String.join(",", tokenStrings));
            buf.append("],");
        }

        String svg = toSVG(t, ruleNames);
        buf.append("\"svgtree\":\"");
        buf.append(escapeJSONString(svg));
        buf.append("\",");

        String tree = toJSONTree(t);
        buf.append("\"tree\":");
        buf.append(tree);
        buf.append(',');

        String errs =
                String.format("\"lex_errors\":[%s],\"parse_errors\":[%s]",
                String.join(",", lexMsgs),
                String.join(",", parseMsgs));
        buf.append(errs);

        buf.append(",\"profile\":{\"colnames\":[");
        for (int i = 0; i < profilerColumnNames.length; i++) {
            if ( i>0 ) buf.append(',');
            buf.append(String.format("\"%s\"",profilerColumnNames[i]));
        }
        buf.append("],\"data\":[");
        for (int i = 0; i < profileData.length; i++) {
            String[] row = profileData[i];
            if ( i>0 ) buf.append(',');
            buf.append("[\"");
            buf.append(String.join("\",\"",row));
            buf.append("\"]");
        }

        buf.append("]}");

        buf.append("}");

        return buf.toString();
    }

    /** Create a JSON representation of a parse tree. The tree is just a series of nested references
     *  to integers, which refer to rules and tokens.
     */
    public static String toJSONTree(final Tree t) {
        if ( !(t instanceof RuleContext) ) {
            return getJSONNodeText(t);
        }
        StringBuilder buf = new StringBuilder();
        buf.append("{");
        buf.append(getJSONNodeText(t));
        buf.append(",\"kids\":[");
        for (int i = 0; i<t.getChildCount(); i++) {
            if ( i>0 ) buf.append(',');
            buf.append(toJSONTree(t.getChild(i)));
        }
        buf.append("]");
        buf.append("}");
        return buf.toString();
    }

    /** Create appropriate JSON text for a tree node */
    public static String getJSONNodeText(Tree t) {
        if ( t instanceof RuleContext) {
            int ruleIndex = ((RuleContext)t).getRuleContext().getRuleIndex();
            int altNumber = ((RuleContext) t).getAltNumber();
            if ( altNumber!= ATN.INVALID_ALT_NUMBER ) {
                return String.format("\"ruleidx\":%d,\"alt\":%d",ruleIndex,altNumber);
            }
            return String.format("\"ruleidx\":%d",ruleIndex,altNumber);
        }
        else if ( t instanceof ErrorNode) {
            Token symbol = ((TerminalNode)t).getSymbol();
            if (symbol != null) {
                return "{\"error\":\"" + symbol.getText() + "\"}";
            }
            return "{\"error\":\""+t.getPayload().toString()+"\"}";
        }
        else if ( t instanceof TerminalNode) {
            Token symbol = ((TerminalNode)t).getSymbol();
            if (symbol != null) {
                return String.valueOf(symbol.getTokenIndex());
            }
            return "-1";
        }
        return "<unknown node type>";
    }

    public static String escapeJSONString(String s) {
        s = s.replace("\\", "\\\\");
        s = s.replace("\"", "\\\"");
        s = s.replace("\n", "\\n");
        s = s.replace("\r", "\\r");
        s = s.replace("\t", "\\t");
        return s;
    }
}
