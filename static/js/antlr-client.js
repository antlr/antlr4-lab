"use strict";

let ANTLR_SERVICE = "/parse/";

let SAMPLE_PARSER =
    "parser grammar ExprParser;\n" +
    "options { tokenVocab=ExprLexer; }\n" +
    "\n" +
    "program\n" +
    "    : stat EOF\n" +
    "    | def EOF\n" +
    "    ;\n" +
    "\n" +
    //"foo : 'a' 'abc' 'a\\'b' '\\u34ab' 'ab\\ncd' ;\n" +
    "stat: ID '=' expr ';'\n" +
    "    | expr ';'\n" +
    "    ;\n" +
    "\n" +
    "def : ID '(' ID (',' ID)* ')' '{' stat* '}' ;\n" +
    "\n" +
    "expr: ID\n" +
    "    | INT\n" +
    "    | func\n" +
    "    | 'not' expr\n" +
    "    | expr 'and' expr\n" +
    "    | expr 'or' expr\n" +
    "    ;\n" +
    "\n" +
    "func : ID '(' expr (',' expr)* ')' ;"

let SAMPLE_LEXER =
    "// DELETE THIS CONTENT IF YOU PUT COMBINED GRAMMAR IN Parser TAB\n" +
    "lexer grammar ExprLexer;\n" +
    "\n" +
    "AND : 'and' ;\n" +
    "OR : 'or' ;\n" +
    "NOT : 'not' ;\n" +
    "EQ : '=' ;\n" +
    "COMMA : ',' ;\n" +
    "SEMI : ';' ;\n" +
    "LPAREN : '(' ;\n" +
    "RPAREN : ')' ;\n" +
    "LCURLY : '{' ;\n" +
    "RCURLY : '}' ;\n" +
    "\n" +
    "INT : [0-9]+ ;\n" +
    "ID: [a-zA-Z_][a-zA-Z_0-9]* ;\n" +
    "WS: [ \\t\\n\\r\\f]+ -> skip ;";

let SAMPLE_INPUT =
    "f(x,y) {\n" +
    "    a = 3+foo;\n" +
    "    x and y;\n" +
    "}";


function processANTLRResults(response) {
    let parserSession = $("#grammar").data("parserSession")
    let lexerSession = $("#grammar").data("lexerSession")
    let session = $("#input").data("session");
    let I = session.getValue();
    let s = $('#start').text();

    if ( typeof(response.data)==="string" ) {
        // Didn't parse as json
        console.log("Bad JSON:")
        console.log(response.data);
        $("#tool_errors").html(`<span class="error">BAD JSON RESPONSE</span><br>`);
        $("#tool_errors").show();
        $("#tool_errors_header").show();
        return;
    }

    removeAllMarkers(parserSession);
    parserSession.setAnnotations(null);
    removeAllMarkers(lexerSession);
    lexerSession.setAnnotations(null);

    let result = response.data.result;
    // console.log(result);

    if ( "arg_error" in response.data ) {
        $("#tool_errors").html(`<span class="error">${response.data.arg_error}</span><br>`);
        $("#tool_errors").show();
        $("#tool_errors_header").show();
        $("#parse_errors").hide();
        $("#parse_errors_header").hide();
        return;
    }

    if ( "exception_trace" in response.data ) {
        $("#tool_errors").html(`<span class="error">${response.data.exception_trace}<br></span>`);
        $("#tool_errors").show();
        $("#tool_errors_header").show();
        $("#parse_errors").hide();
        $("#parse_errors_header").hide();
        return;
    }

    showToolErrors(response);

    let parser_grammar_errors = response.data.parser_grammar_errors;
    let lexer_grammar_errors = response.data.lexer_grammar_errors;

    let grammarAnnotations = [];
    for (let ei in parser_grammar_errors) {
        let e = parser_grammar_errors[ei];
        grammarAnnotations.push({
            row: e.line-1,
            text: e.msg,
            type: "error"
        });
    }
    parserSession.setAnnotations(grammarAnnotations);

    grammarAnnotations = [];
    for (let ei in lexer_grammar_errors) {
        let e = lexer_grammar_errors[ei];
        grammarAnnotations.push({
            row: e.line-1,
            text: e.msg,
            type: "error"
        });
    }
    lexerSession.setAnnotations(grammarAnnotations);

    if ( Object.keys(result).length===0 ) {
        return;
    }

    showParseErrors(response);

    let tokens = result.tokens;
    let symbols = result.symbols;
    let lex_errors = result.lex_errors;
    let parse_errors = result.parse_errors;

    let profile = result.profile;

    let charToChunk = chunkifyInput(I, tokens, symbols, lex_errors, parse_errors);
    $("#input").data("charToChunk", charToChunk);

    let Range = ace.require('ace/range').Range;

    removeAllMarkers(session);
    session.setAnnotations(null);

    let annotations = [];
    for (let ei in lex_errors) {
        let e = lex_errors[ei];
        let a = session.doc.indexToPosition(e.startidx);
        let b = session.doc.indexToPosition(e.erridx+1);
        let r = new Range(a.row, a.column, b.row, b.column);
        session.addMarker(r, "lexical_error_class", "text", false);
        annotations.push({
            row: a.row,
            text: `${e.line}:${e.pos} ${e.msg}`,
            type: "error"
        });
    }

    for (let ei in parse_errors) {
        let e = parse_errors[ei];
        let a = session.doc.indexToPosition(tokens[e.startidx].start);
        let b = session.doc.indexToPosition(tokens[e.stopidx].stop+1);
        let r = new Range(a.row, a.column, b.row, b.column);
        session.addMarker(r, "syntax_error_class", "text", false);
        annotations.push({
            row: a.row,
            text: `${e.line}:${e.pos} ${e.msg}`,
            type: "error"
        });
    }

    session.setAnnotations(annotations);

    let tree = result.tree;
    let buf = ['<ul id="treeUL">'];
    walk(tree, result, I, buf);
    buf.push('</ul>');
    $("#tree").html(buf.join('\n'))

    let svgtree = result.svgtree;
    if ( svgtree != null ) {
        console.log(svgtree);
        let b64_svgtree = btoa(unescape(encodeURIComponent(svgtree)))
        $("#svgtree").html("<img src='data:image/svg+xml;base64," + b64_svgtree + "'></img>");
    }

    initParseTreeView();

    buildProfileTableView(profile.colnames, profile.data);
}

function walk(t, result, input, buf) {
    if (t == null) return;

    if ( 'error' in t ) {
        buf.push(`<li class="tree-token" style="color: #905857">&lt;error:${t.error}&gt;</li>`);
        return;
    }

    let symbols = result.symbols;
    let rulenames = result.rules;
    let tokens = result.tokens;
    let ruleidx = t.ruleidx;
    let alt = t.alt;
    // console.log(rulenames[ruleidx]);
    buf.push('<li><span class="tree-root expanded-tree">'+rulenames[ruleidx]+'</span>')
    if ( 'kids' in t && t.kids.length > 0) {
        buf.push('<ul class="nested active">');
        for (let i = 0; i < t.kids.length; i++) {
            let kid = t.kids[i];
            if (typeof (kid) == 'number') {
                let a = tokens[kid].start;
                let b = tokens[kid].stop;
                buf.push(`<li class="tree-token">${input.slice(a, b + 1)}</li>`);
                // console.log(`${symbols[tokens[kid].type]}:${input.slice(a, b + 1)}`);
            }
            else {
                walk(kid, result, input, buf);
            }
        }
        buf.push('</ul>');
    }
}

async function run_antlr() {
    let parserSession = $("#grammar").data("parserSession")
    let lexerSession = $("#grammar").data("lexerSession")
    let g = parserSession.getValue()
    let lg = lexerSession.getValue();
    let I = $("#input").data("session").getValue();
    let s = $('#start').text();

    $("#profile_choice").show();

    await axios.post(ANTLR_SERVICE,
        {grammar: g, lexgrammar: lg, input: I, start: s}
    )
        .then(processANTLRResults)
        .catch((error) => {
            if( error.response ){
                console.log(error.response.data); // => the response payload
            }
        });
}

function initParseTreeView() {
    $("#svgtreetab").show();
    $("#treetab").show();
    let toggler = document.getElementsByClassName("tree-root");
    for (let i = 0; i < toggler.length; i++) {
	// add event handler to open/close
        toggler[i].addEventListener("click", function () {
            let nested = this.parentElement.querySelector(".nested");
            if (nested != null) {
                nested.classList.toggle("active");
            }
            this.classList.toggle("expanded-tree");
        });
    }
}

function buildProfileTableView(colnames, rows) {
    let table = "<table class='profile-table'>\n";
    table += "<thead>\n";
    table += "  <tr>\n";
    for (const name of colnames) {
        table += "<th>"+name+"</th>";
    }
    table += "  </tr>\n";
    table += "</thead>\n";

    table += "<tbody>\n";
    for (const row of rows) {
        table += "      <tr>";
        for (const v of row) {
            table += "<td>"+v+"</td>";
        }
        table += "</tr>\n";
    }
    table += "</tbody>\n";
    table += "</table>\n";
    $("#profile").html(table)
}

function chunkifyInput(input, tokens, symbols, lex_errors, parse_errors) {
    let charToChunk = new Array(input.length);
    for (let ti in tokens) {
        let t = tokens[ti];
        let toktext = input.slice(t.start, t.stop + 1);
        let tooltipText = `#${ti} Type ${symbols[t.type]} Line ${t.line}:${t.pos}`;
        let chunk = {tooltip:tooltipText, chunktext:toktext, "start":t.start, "stop":t.stop+1};
        for (let i = t.start; i <= t.stop; i++) {
            charToChunk[i] = chunk;
        }
    }
    for (let ei in lex_errors) { // set lex error tokens to just error tokens
        let e = lex_errors[ei];
        let errtext = input.slice(e.startidx, e.erridx + 1);
        let chunk = {tooltip:"token recognition error", chunktext:errtext, "start":e.startidx, "stop":e.erridx+1, error:true};
        for (let i = e.startidx; i <= e.erridx; i++) {
            charToChunk[i] = chunk;
        }
    }

    // chunkify skipped chars (adjacent into one chunk)
    let i = 0;
    while ( i<input.length ) {
        if ( charToChunk[i]==null ) {
            let a = i;
            while ( charToChunk[i]==null && i<input.length ) {
                i++;
            }
            let b = i;
            let skippedText = input.slice(a, b);
            let chunk = {tooltip:"Skipped", chunktext:skippedText, "start":a, "stop":b};
            for (let i = a; i < b; i++) {
                charToChunk[i] = chunk;
            }
        }
        else {
            i++;
        }
    }

    return charToChunk;
}

function mouseEventInsideInputText(session) {
    return function (e) {
        let pos = e.getDocumentPosition();
        let ci = session.doc.positionToIndex(pos)
        let charToChunk = $("#input").data("charToChunk");
        let lastTokenRangeMarker = $("#input").data("lastTokenRangeMarker");
        if (charToChunk != null) {
            if (ci >= charToChunk.length) {
                ci = charToChunk.length - 1;
            }
            let chunk = charToChunk[ci];
            if (chunk != null) {
                if ( 'error' in chunk ) {
                    $("#tokens").html('(<span style="color:#9A2E06;">'+chunk.tooltip+'</span>)');
                }
                else {
                    $("#tokens").html('('+chunk.tooltip+')')
                }
                let a = session.doc.indexToPosition(chunk.start);
                let b = session.doc.indexToPosition(chunk.stop);
                var Range = ace.Range;
                let r = new Range(a.row, a.column, b.row, b.column);
                if ( lastTokenRangeMarker !== null ) {
                    session.removeMarker(lastTokenRangeMarker);
                }
                lastTokenRangeMarker = session.addMarker(r, "token_range_class", "text");
                $("#input").data("lastTokenRangeMarker", lastTokenRangeMarker);
            }
            // console.log(pos, ci, chunk);
        } else {
            // console.log(pos, ci);
        }
    };
}

function showToolErrors(response) {
    if (response.data.parser_grammar_errors.length > 0 ||
        response.data.lexer_grammar_errors.length > 0 ||
        response.data.warnings.length > 0)
    {
        let errors = "";
        response.data.parser_grammar_errors.forEach( function(e) {
            errors += `<span class="error">${e.msg}</span><br>`;
        });
        response.data.lexer_grammar_errors.forEach( function(e) {
            errors += `<span class="error">${e.msg}</span><br>`;
        });
        response.data.warnings.forEach( function(w) {
            errors += `<span class="error">${w.msg}</span><br>`;
        });
        errors += "\n";
        $("#tool_errors").html(errors);
        $("#tool_errors").show();
        $("#tool_errors_header").show();
    }
    else {
        $("#tool_errors").hide();
        $("#tool_errors_header").hide();
    }
}

function showParseErrors(response) {
    if (response.data.result.lex_errors.length > 0 ||
        response.data.result.parse_errors.length > 0 )
    {
        let errors = "";
        response.data.result.lex_errors.forEach( function(e) {
            errors += `<span class="error">${e.line}:${e.pos} ${e.msg}</span><br>`;
        });
        response.data.result.parse_errors.forEach( function(e) {
            errors += `<span class="error">${e.line}:${e.pos} ${e.msg}</span><br>`;
        });
        errors += "\n";
        $("#parse_errors").html(errors);
        $("#parse_errors").show();
        $("#parse_errors_header").show();
    }
    else {
        $("#parse_errors").hide();
        $("#parse_errors_header").hide();
    }
}

function createAceANTLRMode() {
    var ANTLR4HighlightRules = function() {
        this.$rules = {
            "start": [
                { token : "string.single",  regex : '[\'](?:(?:\\\\.)|(?:\\\\u....)|(?:[^\'\\\\]))*?[\']' },
                { token : "comment.line", regex : "//.*$" },
                {
                    token : "comment", // multi line comment
                    regex : "\\/\\*",
                    next : "comment"
                },
                { token: "keyword", regex: "grammar|options|header|parser|lexer|returns|fragment" },
                { token: "entity.name.function", regex: "[a-z][a-zA-Z0-9_]*\\b" },
                { token: "variable", regex: "[A-Z][a-zA-Z0-9_]*\\b" },  // tokens start with uppercase char
                { token : "punctuation.operator", regex : "\\?|\\:|\\||\\;" },
                { token : "paren.lparen", regex : "[[({]" },
                { token : "paren.rparen", regex : "[\\])}]" },
            ],
            "comment" : [
                {
                    token : "comment", // closing comment
                    regex : "\\*\\/",
                    next : "start"
                }, {
                    defaultToken : "comment"
                }
            ]
        };
    };

    var ANTLR4Mode = function() {
        this.HighlightRules = ANTLR4HighlightRules;
    };

    ace.define('ace/mode/antlr4-mode',
        ["require", "exports", "module", "ace/lib/oop", "ace/mode/text",
            "ace/mode/text_highlight_rules", "ace/worker/worker_client"],
        function (require, exports, module) {
            var oop = require("ace/lib/oop");
            var TextMode = require("ace/mode/text").Mode;
            var TextHighlightRules = require("ace/mode/text_highlight_rules").TextHighlightRules;

            oop.inherits(ANTLR4HighlightRules, TextHighlightRules);
            oop.inherits(ANTLR4Mode, TextMode);

            exports.Mode = ANTLR4Mode;
        });
}

function createGrammarEditor() {
    var parserSession = ace.createEditSession(SAMPLE_PARSER);
    var lexerSession = ace.createEditSession(SAMPLE_LEXER);
    var editor = ace.edit("grammar");

    $("#grammar").data("parserSession", parserSession);
    $("#grammar").data("lexerSession", lexerSession);
    $("#grammar").data("editor", editor)

    editor.setSession(parserSession);
    editor.setOptions({
        theme: 'ace/theme/chrome',
        "highlightActiveLine": false,
        "readOnly": false,
        "showLineNumbers": true,
        "showGutter": true,
        "printMargin": false
    });
    // $("#grammar").resize()

    $("#grammar").keyup(function(e) {
        if ( (e.key.length === 1 && !e.ctrlKey && !e.metaKey) || e.keyCode==='\n' ) {
            parserSession.setAnnotations(null);
            removeAllMarkers(parserSession);
            lexerSession.setAnnotations(null);
            removeAllMarkers(lexerSession);
        }
    });

    createAceANTLRMode()
    parserSession.setMode("ace/mode/antlr4-mode")
    lexerSession.setMode("ace/mode/antlr4-mode")

    return editor;
}

function removeAllMarkers(session) {
    const markers = session.getMarkers();
    if (markers) {
        const keys = Object.keys(markers);
        for (let item of keys) {
            session.removeMarker(markers[item].id);
        }
    }
}

function createInputEditor() {
    var input = ace.edit("input");
    let session = ace.createEditSession(SAMPLE_INPUT);
    $("#input").data("session", session);
    $("#input").data("editor", input);
    input.setSession(session);
    input.setOptions({
        theme: 'ace/theme/chrome',
        "highlightActiveLine": false,
        "readOnly": false,
        "showLineNumbers": true,
        "showGutter": true,
        "printMargin": false
    });

    $("#input").on('mouseleave', function() {
        $("#tokens").html("");
        let lastTokenRangeMarker = $("#input").data("lastTokenRangeMarker")
        session.removeMarker(lastTokenRangeMarker);
    });

    $("#input").on('mouseup', function() {
        input.resize();
    });

    $("#input").keyup(function(e) {
        if (e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
            session.setAnnotations(null);
            removeAllMarkers(session);
        }
    });

    input.on("mousemove", mouseEventInsideInputText(session));
}

function setupGrammarTabs(editor) {
    $("#parsertab").addClass("tabs-header-selected");
    $("#lexertab").removeClass("tabs-header-selected");

    $("#parsertab").click(function () {
        editor.setSession($("#grammar").data("parserSession"));
        $("#parsertab").addClass("tabs-header-selected");
        $("#lexertab").removeClass("tabs-header-selected");
    });
    $("#lexertab").click(function () {
        editor.setSession($("#grammar").data("lexerSession"));
        $("#parsertab").removeClass("tabs-header-selected");
        $("#lexertab").addClass("tabs-header-selected");
    });
}

function setupTreeTabs() {
    $("#svgtreetab").hide();
    $("#treetab").hide();
    $("#svgtreetab").addClass("tabs-header-selected");
    $("#treetab").removeClass("tabs-header-selected");
    $("#svgtree").show();
    $("#tree").hide();

    $("#svgtreetab").click(function () {
        $("#svgtree").show();
        $("#tree").hide();
        $("#svgtreetab").addClass("tabs-header-selected");
        $("#treetab").removeClass("tabs-header-selected");
    });
    $("#treetab").click(function () {
        $("#svgtree").hide();
        $("#tree").show();
        $("#svgtreetab").removeClass("tabs-header-selected");
        $("#treetab").addClass("tabs-header-selected");
    });
}

function dragOverHandler(e,whichEditor) {
    // Prevent default behavior (Prevent file from being opened)
    e.preventDefault();
    e.stopPropagation();
    $("#"+whichEditor).addClass("drag-over");
}

function dragLeaveHandler(e,whichEditor) {
    // Prevent default behavior (Prevent file from being opened)
    $("#"+whichEditor).removeClass("drag-over");
}

function dropHandler(e,whichEditor) {
    e.preventDefault();
    // See https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API/File_drag_and_drop
    // Use DataTransferItemList interface to access the file(s)
    let editor = $("#"+whichEditor).data("editor");
    let session = editor.session;
    for (let f of e.dataTransfer.items) {
        // If dropped items aren't files, reject them
        if (f.kind === 'file') {
            const file = f.getAsFile();
            session.setAnnotations(null);
            removeAllMarkers(session);
            file.text().then((content)=> {
                session.setValue(content);
                $("#"+whichEditor).removeClass("drag-over");
            });
        }
    }
}

function setUpDragAndDrop() {
    for (let el of ["grammar", "input"]) {
        $("#"+el).on('dragover', (e) => {
            dragOverHandler(e, el);
        });
        $("#"+el).on('dragleave', (e) => {
            dragLeaveHandler(e, el);
        });
        $("#"+el).on('drop', (e) => {
            dropHandler(e.originalEvent, el);
        });
    }
}

// MAIN
$(document).ready(function() {
    String.prototype.sliceReplace = function (start, end, repl) {
        return this.substring(0, start) + repl + this.substring(end);
    };

    $(document).tooltip();

    var editor = createGrammarEditor();
    setupGrammarTabs(editor);
    createInputEditor();

    setupTreeTabs();

    $("#profile_choice").hide();
    $("#profile_header").hide();
    $("#profile").hide();
    $("#profile_choice").click(function () {
        if ( $("#profile_choice").text().startsWith("Show") ) {
            $("#profile_choice").text("Hide profiler");
            $("#profile_header").show();
            $("#profile").show();
        }
        else {
            $("#profile_choice").text("Show profiler");
            $("#profile_header").hide();
            $("#profile").hide();
        }
    });

    $("#tool_errors").hide();
    $("#parse_errors").hide();
    $("#tool_errors_header").hide();
    $("#parse_errors_header").hide();

    setUpDragAndDrop();
    setupSelectGrammarTable();

    // Remove any ?hash=UUID arguments from the URL in browser window (messes up backup button)
    history.replaceState(null, null, "/");
});
