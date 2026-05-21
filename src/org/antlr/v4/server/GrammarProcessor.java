package org.antlr.v4.server;

/*
 * Local modifications (Michele Fadda, 2026):
 * - Inlined execInDir() and StreamVacuum from the removed parrtlib dependency.
 * - execInDir uses ProcessBuilder instead of Runtime.exec for better
 *   PATH/environment control (adds /opt/homebrew/bin for Apple Silicon).
 * - Added ensureSVGDeps() that auto-installs ps2pdf/pdf2svg via brew (macOS)
 *   or apt-get (Linux) if missing, or returns a helpful SVG error with
 *   installation instructions (Windows/unsupported OS).
 * - toSVG() returns an error SVG with guidance instead of crashing on failure.
 * - commandExists() helper checks system PATH via `which`.
 * - Replaced deprecated StringBufferInputStream with CharStreams.fromString().
 * - Added escXml() and makeErrorSVG() helpers for safe SVG error rendering.
 * - Several private methods changed to package-private for testability.
 */

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

import static org.antlr.v4.gui.Interpreter.profilerColumnNames;
import static org.antlr.v4.server.ANTLRHttpServer.IMAGES_DIR;
import static org.antlr.v4.server.ANTLRHttpServer.ParseServlet.LOGGER;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
        CharStream charStream = CharStreams.fromString(input);

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
        String depError = ensureSVGDeps();
        if (depError != null) {
            return depError;
        }

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
            return makeErrorSVG("ps2pdf failed: " + escXml(results[1].strip()));
        }

        results = execInDir(IMAGES_DIR, "pdf2svg", pdfFileName, svgFileName);
        if (results[1].length() > 0) {
            LOGGER.info("pdf2svg: " + results[1]);
            return makeErrorSVG("pdf2svg failed: " + escXml(results[1].strip()));
        }

        String svgfilename = Path.of(IMAGES_DIR, svgFileName).toAbsolutePath().toString();
        String svg = new String(Files.readAllBytes(Paths.get(svgfilename)));
        return svg;
    }

    static String escXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String makeErrorSVG(String msg) {
        String[] lines = msg.split("\n");
        int lineH = 18;
        int h = Math.max(30, (lines.length + 1) * lineH);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" height=\"").append(h).append("\" width=\"800\">\n");
        for (int i = 0; i < lines.length; i++) {
            sb.append("  <text x=\"5\" y=\"").append((i + 1) * lineH).append("\" fill=\"red\" font-size=\"12\">")
              .append(lines[i]).append("</text>\n");
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    static String ensureSVGDeps() {
        boolean ps2pdfOk = commandExists("ps2pdf");
        boolean pdf2svgOk = commandExists("pdf2svg");
        if (ps2pdfOk && pdf2svgOk) return null;

        String os = System.getProperty("os.name").toLowerCase();
        LOGGER.info("Ensuring SVG deps (ps2pdf={}, pdf2svg={}) on {}", ps2pdfOk, pdf2svgOk, os);

        List<String> missing = new ArrayList<>();
        if (!ps2pdfOk) missing.add("ghostscript");
        if (!pdf2svgOk) missing.add("pdf2svg");

        if (os.contains("mac")) {
            if (commandExists("brew")) {
                List<String> cmd = new ArrayList<>(Arrays.asList("brew", "install"));
                cmd.addAll(missing);
                try {
                    execInDir(null, cmd.toArray(new String[0]));
                    return null;
                }
                catch (RuntimeException e) {
                    LOGGER.warn("brew install failed: " + e.getMessage());
                }
            }
            return makeErrorSVG(
                "To view parse trees, install these missing tools:\n" +
                "  brew install " + String.join(" ", missing) + "\n\n" +
                "If Homebrew is not installed, get it from: https://brew.sh");
        }
        else if (os.contains("linux")) {
            if (commandExists("apt-get")) {
                List<String> cmd = new ArrayList<>(Arrays.asList("sudo", "apt-get", "install", "-y"));
                cmd.addAll(missing);
                try {
                    execInDir(null, cmd.toArray(new String[0]));
                    return null;
                }
                catch (RuntimeException e) {
                    LOGGER.warn("apt-get install failed: " + e.getMessage());
                }
            }
            return makeErrorSVG(
                "To view parse trees, install these missing tools:\n" +
                "  sudo apt-get install -y " + String.join(" ", missing) + "\n\n" +
                "Or use your distro's package manager (yum, dnf, pacman, etc.)");
        }
        else if (os.contains("win")) {
            return makeErrorSVG(
                "To view parse trees on Windows, install:\n" +
                "  1. Ghostscript (ps2pdf): https://www.ghostscript.com/releases/gsdnld.html\n" +
                "  2. pdf2svg: https://github.com/dawbarton/pdf2svg\n" +
                "     or via MSYS2:  pacman -S mingw-w64-x86_64-pdf2svg\n" +
                "     or via Cygwin: apt-cyg install pdf2svg\n\n" +
                "After installing, ensure both are on your system PATH.");
        }
        else {
            return makeErrorSVG(
                "Unrecognized OS: " + os + "\n" +
                "To view parse trees, install these tools manually:\n" +
                "  - Ghostscript (ps2pdf): https://www.ghostscript.com/\n" +
                "  - pdf2svg: https://github.com/dawbarton/pdf2svg\n" +
                "  Then ensure they are on your PATH.");
        }
    }

    static boolean commandExists(String cmd) {
        try {
            String[] result = execInDir(null, "which", cmd);
            return result[0] != null && result[0].strip().length() > 0;
        }
        catch (RuntimeException e) {
            return false;
        }
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

    // Replaces us.parr.lib.ParrtSys.execInDir
    public static String[] execInDir(String execPath, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            if (execPath != null) {
                pb.directory(new File(execPath));
            }
            // Inherit PATH from the system environment (brew paths, etc.)
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null && !pathEnv.contains("/opt/homebrew/bin")) {
                pb.environment().put("PATH", pathEnv + ":/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/usr/bin:/bin");
            }
            pb.redirectErrorStream(false);
            Process process = pb.start();
            StreamVacuum stdoutVacuum = new StreamVacuum(process.getInputStream());
            StreamVacuum stderrVacuum = new StreamVacuum(process.getErrorStream());
            stdoutVacuum.start();
            stderrVacuum.start();
            process.waitFor();
            stdoutVacuum.join();
            stderrVacuum.join();
            return new String[]{stdoutVacuum.toString(), stderrVacuum.toString()};
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class StreamVacuum implements Runnable {
        private StringBuilder buf = new StringBuilder();
        private BufferedReader in;
        private Thread sucker;
        public StreamVacuum(InputStream in) {
            this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        public void start() {
            sucker = new Thread(this);
            sucker.start();
        }
        @Override
        public void run() {
            try {
                String line = in.readLine();
                while (line!=null) {
                    buf.append(line);
                    buf.append('\n');
                    line = in.readLine();
                }
            }
            catch (IOException ioe) {
                System.err.println("can't read output from process");
            }
        }
        public void join() throws InterruptedException {
            sucker.join();
        }
        @Override
        public String toString() {
            return buf.toString();
        }
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
