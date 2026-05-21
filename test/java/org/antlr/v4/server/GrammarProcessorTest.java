package org.antlr.v4.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class GrammarProcessorTest {

    @Test
    void escXmlLeavesPlainTextAlone() {
        assertEquals("hello world", GrammarProcessor.escXml("hello world"));
    }

    @Test
    void escXmlEscapesAmpersand() {
        assertEquals("a&amp;b", GrammarProcessor.escXml("a&b"));
    }

    @Test
    void escXmlEscapesLtGtQuote() {
        assertEquals("&lt;tag attr=&quot;val&quot;&gt;", GrammarProcessor.escXml("<tag attr=\"val\">"));
    }

    @Test
    void makeErrorSVGContainsMessage() {
        String svg = GrammarProcessor.makeErrorSVG("test error msg");
        assertTrue(svg.contains("test error msg"));
        assertTrue(svg.startsWith("<?xml"));
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
    }

    @Test
    void makeErrorSVGMultiLine() {
        String svg = GrammarProcessor.makeErrorSVG("line1\nline2\nline3");
        assertTrue(svg.contains("line1"));
        assertTrue(svg.contains("line2"));
        assertTrue(svg.contains("line3"));
    }

    @Test
    void commandExistsReturnsTrueForJava() {
        assertTrue(GrammarProcessor.commandExists("java"));
    }

    @Test
    void commandExistsReturnsFalseForNonsense() {
        assertFalse(GrammarProcessor.commandExists("xyznonexistentcmd12345"));
    }

    @Test
    void execInDirEchoesOutput() {
        String[] result = GrammarProcessor.execInDir(null, "echo", "hello from execInDir");
        assertEquals(2, result.length);
        assertTrue(result[0].contains("hello from execInDir"),
                "stdout should contain the echoed message, got: " + result[0]);
    }

    @Test
    void execInDirCapturesStderr() {
        String[] result = GrammarProcessor.execInDir(null, "ls", "/nonexistent_path_xyz_123");
        assertTrue(result[1].length() > 0 || result[0].contains("No such file"),
                "stderr or stdout should indicate error");
    }

    @Test
    void interpCombinedGrammarProducesTokensAndTree() throws IOException {
        String grammar = "grammar T; s : ID ; ID : [a-z]+ ; WS : [ ]+ -> skip ;";
        String input = "hello";
        String startRule = "s";

        JsonObject response = GrammarProcessor.interp(grammar, null, input, startRule);

        assertTrue(response.has("result"), "response should have result field");
        JsonObject result = response.getAsJsonObject("result");
        assertTrue(result.has("tokens"), "result should have tokens");
        assertTrue(result.has("tree"), "result should have tree");
        JsonArray tokens = result.getAsJsonArray("tokens");
        assertEquals(2, tokens.size(), "should have 2 tokens (ID + EOF)");

        assertTrue(response.has("warnings"), "response should have warnings");
        assertTrue(response.has("parser_grammar_errors"), "response should have parser_grammar_errors");
        assertTrue(response.has("lexer_grammar_errors"), "response should have lexer_grammar_errors");
    }

    @Test
    void interpReportsUnknownStartRule() throws IOException {
        String grammar = "grammar T; s : ID ; ID : [a-z]+ ; WS : [ ]+ -> skip ;";
        String input = "hello";
        String startRule = "nonexistent";

        JsonObject response = GrammarProcessor.interp(grammar, null, input, startRule);

        JsonArray warnings = response.getAsJsonArray("warnings");
        assertTrue(warnings.size() > 0, "should have warnings about missing start rule");
    }

    @Test
    void interpWithSeparateLexerAndParserGrammars() throws IOException {
        String lexerGrammar = "lexer grammar L; ID : [a-z]+ ; WS : [ ]+ -> skip ;";
        String parserGrammar = "parser grammar P; options { tokenVocab=L; } s : ID ;";
        String input = "hello";
        String startRule = "s";

        JsonObject response = GrammarProcessor.interp(parserGrammar, lexerGrammar, input, startRule);

        assertTrue(response.has("result"), "response should have result field");
        JsonObject result = response.getAsJsonObject("result");
        assertTrue(result.has("tokens"), "result should have tokens");
        assertEquals(2, result.getAsJsonArray("tokens").size(), "should have ID + EOF");
    }

    @Test
    void interpProducesSVGTree() throws IOException {
        String grammar = "grammar T; s : ID ; ID : [a-z]+ ; WS : [ ]+ -> skip ;";
        String input = "hello";
        String startRule = "s";

        JsonObject response = GrammarProcessor.interp(grammar, null, input, startRule);
        JsonObject result = response.getAsJsonObject("result");
        assertTrue(result.has("svgtree"), "result should have svgtree");
        String svg = result.get("svgtree").getAsString();
        assertTrue(svg.contains("<svg"), "svgtree should be valid SVG");
    }

    @Test
    void ensureSVGDepsReturnsNullWhenToolsPresent() {
        assertNull(GrammarProcessor.ensureSVGDeps(), "should return null when ps2pdf and pdf2svg are available");
    }
}
