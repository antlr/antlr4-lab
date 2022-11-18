package org.antlr.v4.server;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;

import javax.json.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.antlr.v4.gui.Interpreter.profilerColumnNames;
import static org.antlr.v4.server.GrammarProcessor.toSVG;

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
    public static JsonObjectBuilder toJSON(Tree t, Parser recog) throws IOException {
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
    public static JsonObjectBuilder toJSON(Tree t,
                                final List<String> ruleNames,
                                final Vocabulary vocabulary,
                                final TokenStream tokenStream,
                                final CharStream inputStream,
                                JsonArray lexMsgs,
                                JsonArray parseMsgs,
                                String[][] profileData)
        throws IOException
    {
        if ( t==null || ruleNames==null ) {
            return null;
        }

        final JsonObjectBuilder rootObject = Json.createObjectBuilder();

        rootObject.add("rules", Json.createArrayBuilder(ruleNames).build());

        if ( inputStream!=null ) {
            Interval allchar = Interval.of(0, inputStream.size() - 1);
            String input = inputStream.getText(allchar);
            rootObject.add("input", input);
        }

        if ( vocabulary!=null ) {
            List<String> syms = new ArrayList<>(vocabulary.getMaxTokenType());
            for (int i = 0; i < vocabulary.getMaxTokenType(); i++) {
                syms.add('"'+vocabulary.getSymbolicName(i)+'"');
            }
            rootObject.add("symbols", Json.createArrayBuilder(syms).build());
        }

        if ( tokenStream!=null ) {
            final JsonArrayBuilder tokenArray = Json.createArrayBuilder();
            for (int i = 0; i < tokenStream.size(); i++) {
                Token tok = tokenStream.get(i);
                tokenArray.add(Json.createObjectBuilder()
                                   .add("type", tok.getType())
                                   .add("line", tok.getLine())
                                   .add("pos", tok.getCharPositionInLine())
                                   .add("channel", tok.getChannel())
                                   .add("start", tok.getStartIndex())
                                   .add("stop", tok.getStopIndex()).build());
            }
            rootObject.add("tokens", tokenArray.build());
        }

        rootObject.add("svgtree", toSVG(t, ruleNames));
        rootObject.add("tree", toJSONTree(t));

        rootObject.add("lex_errors", lexMsgs);
        rootObject.add("parse_errors", parseMsgs);

        final var dataArray = Json.createArrayBuilder();
        for (String[] row : profileData) {
            dataArray.add(Json.createArrayBuilder(Arrays.asList(row)).build());
        }
        rootObject.add("profile", Json.createObjectBuilder()
                .add("colnames", Json.createArrayBuilder(Arrays.asList(profilerColumnNames)).build())
                .add("data", dataArray.build()).build());

        return rootObject;
    }

    /** Create a JSON representation of a parse tree. The tree is just a series of nested references
     *  to integers, which refer to rules and tokens.
     */
    public static JsonValue toJSONTree(final Tree t) {
        if ( !(t instanceof RuleContext) ) {
            return getJSONNodeText(t);
        }

        final JsonArrayBuilder kidsArray = Json.createArrayBuilder();
        for (int i = 0; i<t.getChildCount(); i++) {
            kidsArray.add(toJSONTree(t.getChild(i)));
        }

        return Json.createObjectBuilder((JsonObject) getJSONNodeText(t))
                .add("kids", kidsArray)
                .build();
    }

    /** Create appropriate JSON text for a tree node */
    public static JsonValue getJSONNodeText(Tree t) {
        if ( t instanceof RuleContext) {
            int ruleIndex = ((RuleContext)t).getRuleContext().getRuleIndex();
            int altNumber = ((RuleContext) t).getAltNumber();
            if ( altNumber!= ATN.INVALID_ALT_NUMBER ) {
                return Json.createObjectBuilder()
                        .add("ruleidx", ruleIndex)
                        .add("alt", altNumber)
                        .build();
            }
            return Json.createObjectBuilder()
                    .add("ruleidx", ruleIndex)
                    .build();
        }
        else if ( t instanceof ErrorNode) {
            Token symbol = ((TerminalNode)t).getSymbol();
            if (symbol != null) {
                return Json.createObjectBuilder()
                        .add("error", symbol.getText())
                        .build();
            }
            return Json.createObjectBuilder()
                    .add("error", t.getPayload().toString())
                    .build();
        }
        else if ( t instanceof TerminalNode) {
            Token symbol = ((TerminalNode)t).getSymbol();
            if (symbol != null) {
                return Json.createValue(symbol.getTokenIndex());
            }
            return Json.createValue("-1");
        }
        return Json.createValue("<unknown node type>");
    }
}
