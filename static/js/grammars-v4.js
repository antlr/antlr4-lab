"use strict";

let GRAMMAR_INDEX = "https://raw.githubusercontent.com/antlr/grammars-v4/master/grammars.json"

async function selectGrammar() {
    // Find.
    let name = $("#selectgrammar option:selected" ).text();
    let grammars = $("#selectgrammar").data("grammars")
    const found = grammars.find(function(element)
    {
        return element.name == name;
    });
    // Set grammar.
    if ( found && found.name!=="Sample" ) {
        if (found.lexer != "") {
            await axios.get(found.lexer).then(function (response) {
                $("#grammar").data("lexerSession").setValue(response.data);
                $("#grammar").data("editor").setSession($("#grammar").data("lexerSession")); // force redraw.
                $("#parsertab").removeClass("tabs-header-selected");
                $("#lexertab").addClass("tabs-header-selected");
            });
        }
        else {
            $("#grammar").data("lexerSession").setValue("");
            $("#grammar").data("editor").setSession($("#grammar").data("lexerSession")); // force redraw.
            $("#parsertab").removeClass("tabs-header-selected");
            $("#lexertab").addClass("tabs-header-selected");
        }
        await axios.get(found.parser).then(function (response) {
            $("#grammar").data("parserSession").setValue(response.data);
            $("#grammar").data("editor").setSession($("#grammar").data("parserSession")); // force redraw.
            $("#parsertab").addClass("tabs-header-selected");
            $("#lexertab").removeClass("tabs-header-selected");
        });
        let prefix = "https://raw.githubusercontent.com/antlr/grammars-v4/master/";
        let trunc = found.parser.substring(prefix.length);
        // remove parser grammar file name, assume that it's
        // the root (which is wrong!).
        let last = trunc.lastIndexOf("/");
        let x = trunc.substring(0, last);
        let fname = prefix + x + "/examples/" + found.example[0];
        await axios.get(fname).then(function (response) {
            $("#input").data("session").setValue(response.data);
        });
        $("#start").text(found.start);
        setupInputDropDownForGrammar(found);
    }
    else {
        $("#grammar").data("lexerSession").setValue(SAMPLE_LEXER);
        $("#grammar").data("parserSession").setValue(SAMPLE_PARSER);
        $("#input").data("session").setValue(SAMPLE_INPUT);
        $("#start").text("program");
        $("#grammar").data("editor").setSession($("#grammar").data("parserSession")); // force redraw.
        $("#parsertab").addClass("tabs-header-selected");
        $("#lexertab").removeClass("tabs-header-selected");
        setupInputDropDownForGrammar(grammars[0]);
    }
    let session = $("#input").data("session");
    session.setAnnotations(null);
    removeAllMarkers(session);
    let parserSession = $("#grammar").data("parserSession");
    parserSession.setAnnotations(null);
    removeAllMarkers(parserSession);
    let lexerSession = $("#grammar").data("lexerSession");
    lexerSession.setAnnotations(null);
    removeAllMarkers(lexerSession);
    $("#input").data("charToChunk", null);
}

async function selectInput() {
    // Find grammar.
    let name = $("#selectgrammar option:selected" ).text();
    let grammars = $("#selectgrammar").data("grammars")
    const found_grammar = grammars.find(function(element)
    {
        return element.name == name;
    });
    // Find selected input.
    name = $("#selectinput option:selected" ).text();
    let select = $("#selectinput").get(0);
    let j, L = select.options.length - 1;
    let found = false;
    for(j = L; j >= 0; j--) {
        let option = select.options[j];
        if (option.selected)
        {
            // Set input.
            let x = option.value;
            let prefix = "https://raw.githubusercontent.com/antlr/grammars-v4/master/";
            let trunc = found_grammar.parser.substring(prefix.length);
            // remove parser grammar file name, assume that it's
            // the root (which is wrong!).
            let last = trunc.lastIndexOf("/");
            let y = trunc.substring(0, last);
            let url = prefix + y + "/examples/" + x;
            await axios.get(url).then(function (response) {
                $("#input").data("session").setValue(response.data);
            });
            $("#start").text(found.start);
            found = true;
        }
    }
    if (! found) return;
    let session = $("#input").data("session");
    session.setAnnotations(null);
    removeAllMarkers(session);
    let parserSession = $("#grammar").data("parserSession");
    parserSession.setAnnotations(null);
    removeAllMarkers(parserSession);
    let lexerSession = $("#grammar").data("lexerSession");
    lexerSession.setAnnotations(null);
    removeAllMarkers(lexerSession);
    $("#input").data("charToChunk", null);
}

function setupInputDropDownForGrammar(grammar) {
    let selectInput = $("#selectinput").get(0);
    // remove all previous entries in the "input" select control.
    let j, L = selectInput.options.length - 1;
    for(j = L; j >= 0; j--) {
        selectInput.remove(j);
    }
    selectInput.selectedIndex = 0
    if ( grammar==="Sample" ) {
        selectInput.options[i] = new Option("sample", SAMPLE_INPUT);
        return;
    }
    let i = 0;
    for (const e of grammar.example) {
        let opt = new Option(e, e);
        selectInput.options[i] = opt;
        i++;
    }
}

function loadGrammarIndex(response) {
    let grammars = response.data;
    grammars.sort(function(a, b)
    {
        let fa = a.name.toLowerCase(),
            fb = b.name.toLowerCase();
        if (fa < fb) {
            return -1;
        }
        if (fa > fb) {
            return 1;
        }
        return 0;
    });
    // Add default sample first
    grammars.unshift({
        name: "Sample",
        lexer: "",
        parser: "ExprParser.g4",
        start: "program",
        example: [
            "sample.expr"
        ]
    })
    $("#selectgrammar").data("grammars", grammars); // save grammar index in dropdown element
    let selectGrammar = $("#selectgrammar").get(0);
    let i = 0;
    // Enter in hardwired "Expr" contained in this code.
    for (const g of grammars) {
        let opt = new Option(g.name, g.name);
        selectGrammar.options[i] = opt;
        i++;
    }
    setupInputDropDownForGrammar(grammars[0]);
}

async function setupSelectGrammarTable() {
    await axios.get(GRAMMAR_INDEX)
        .then(loadGrammarIndex)
        .catch((error) => {
            if( error.response ){
                console.log(error.response.data); // => the response payload
            }
        });
}
