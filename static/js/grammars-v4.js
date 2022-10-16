let GRAMMAR_INDEX = "https://raw.githubusercontent.com/antlr/grammars-v4/master/grammars.json"

var grammars_v4 = []; // TODO remove


function select_grammar(selectedValue){
    // Find.
    var name = $("#selectgrammar option:selected" ).text();
    const found = grammars_v4.find(function(element)
    {
        return element.name == name;
    });
    // Set grammar.
    if (found)
    {
        if (found.lexer != "") {
            $.get(found.lexer).done(function(data){
                $("#grammar").data("lexerSession").setValue(data);
                $("#grammar").data("editor").setSession($("#grammar").data("lexerSession")); // force redraw.
                $("#parsertab").removeClass("tabs-header-selected");
                $("#lexertab").addClass("tabs-header-selected");
            });
        } else {
            $("#grammar").data("lexerSession").setValue("");
            $("#grammar").data("editor").setSession($("#grammar").data("lexerSession")); // force redraw.
            $("#parsertab").removeClass("tabs-header-selected");
            $("#lexertab").addClass("tabs-header-selected");
        }
        $.get(found.parser).done(function(data){
            $("#grammar").data("parserSession").setValue(data);
            $("#grammar").data("editor").setSession($("#grammar").data("parserSession")); // force redraw.
            $("#parsertab").addClass("tabs-header-selected");
            $("#lexertab").removeClass("tabs-header-selected");
        });
        var prefix = "https://raw.githubusercontent.com/antlr/grammars-v4/master/";
        var trunc = found.parser.substring(prefix.length);
        // remove parser grammar file name, assume that it's
        // the root (which is wrong!).
        var last = trunc.lastIndexOf("/");
        var x = trunc.substring(0, last);
        var fname = prefix + x + "/examples/" + found.example[0];
        $.get(fname).done(function(data){
            $("#input").data("session").setValue(data);
        });
        $("#start").text(found.start);
        setupSelectInputTable(found);
    }
    else {
        $("#grammar").data("lexerSession").setValue(SAMPLE_LEXER);
        $("#grammar").data("parserSession").setValue(SAMPLE_PARSER);
        $("#input").data("session").setValue(SAMPLE_INPUT);
        $("#start").text("program");
        $("#grammar").data("editor").setSession($("#grammar").data("parserSession")); // force redraw.
        $("#parsertab").addClass("tabs-header-selected");
        $("#lexertab").removeClass("tabs-header-selected");
        setupSelectInputTable(grammars_v4[0]);
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

function select_input(selectedValue){
    // Find grammar.
    var name = $("#selectgrammar option:selected" ).text();
    const found_grammar = grammars_v4.find(function(element)
    {
        return element.name == name;
    });
    // Find selected input.
    var name = $("#selectinput option:selected" ).text();
    var select = $("#selectinput").get(0);
    var j, L = select.options.length - 1;
    var found = false;
    for(j = L; j >= 0; j--) {
        var option = select.options[j];
        if (option.selected)
        {
            // Set input.
            var x = option.value;
            var prefix = "https://raw.githubusercontent.com/antlr/grammars-v4/master/";
            var trunc = found_grammar.parser.substring(prefix.length);
            // remove parser grammar file name, assume that it's
            // the root (which is wrong!).
            var last = trunc.lastIndexOf("/");
            var y = trunc.substring(0, last);
            var url = prefix + y + "/examples/" + x;
            $.get(url).done(function(data){
                $("#input").data("session").setValue(data);
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

function setupSelectInputTable(grammar) {
    var select = $("#selectinput").get(0);
    // remove all previous entries in the "input" select control.
    var j, L = select.options.length - 1;
    for(j = L; j >= 0; j--) {
        select.remove(j);
    }
    select.selectedIndex = 0
    var i = 0;
    for (const e of grammar.example) {
        var opt = new Option(e, e);
        select.options[i] = opt;
        i = i + 1;
    }
}

function loadGrammarIndex(response) {
    console.log(response)
    let g_before = response.data;
    g_before.sort(function(a, b)
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
    grammars_v4 = g_before;
    let selectgrammar = $("#selectgrammar").get(0);
    let i = 0;
    // Enter in hardwired "Expr" contained in this code.
    let hw = new Option("Expr", "Expr");
    selectgrammar.options[i] = hw;
    ++i;
    for (const g of grammars_v4) {
        var opt = new Option(g.name, g.name);
        selectgrammar.options[i] = opt;
        i = i + 1;
    }
    setupSelectInputTable(grammars_v4[0]);
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
