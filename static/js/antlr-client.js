"use strict";

let ANTLR_SERVICE = "http://lab.antlr.org/antlr/";
// let ANTLR_SERVICE = "http://localhost/antlr/";

async function processANTLRResults(response) {
    var g =  await getEditorContent('grammar');
    var lg = await getEditorContent('lexgrammar');
    var I = await getEditorContent('input');
    var s = $('#start').text();
    let result = response.data.result;
    console.log(result);

    if ( "arg_error" in response.data ) {
        $("#tool_errors").html(`<span class="error">${response.data.arg_error}</span><br>`);
        $("#tool_errors").show();
        $("#tool_errors_header").show();
        return;
    }

    if ( "exception" in response.data ) {
        $("#tool_errors").html(`<span class="error">${response.data.exception_trace}<br></span>`);
        $("#tool_errors").show();
        $("#tool_errors_header").show();
        return;
    }

    showToolErrors(response);

    if ( Object.keys(result).length===0 ) {
        return;
    }

    showParseErrors(response);

    let tokens = result.tokens;
    let symbols = result.symbols;
    let lex_errors = result.lex_errors;
    let parse_errors = result.parse_errors;

    let profile = result.profile;

    let chunks = chunkifyInput(I, tokens, symbols, lex_errors, parse_errors);
    let errorlist = $('#errors');
    errorlist.html('');
    chunks.forEach(c => {
        if('error' in c) {
            errorlist.append(`<li><strong>${c.chunktext}</strong>: ${c.tooltip}</li>`);
        }
    });

    let tree = result.tree;
    let buf = ['<ul id="treeUL">'];
    walk(tree, result, I, buf);
    buf.push('</ul>');
    console.log(buf.join('\n'));
    $("#tree").html(buf.join('\n'))

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
    var g =  await getEditorContent('grammar');
    var lg = await getEditorContent('lexgrammar');
    var I = await getEditorContent('input');
    var s = $('#start').text();

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
    $("#tree_header").show();
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
        let chunk = {tooltip:tooltipText, chunktext:toktext};
        for (let i = t.start; i <= t.stop; i++) {
            charToChunk[i] = chunk;
        }
    }
    for (let ei in lex_errors) {
        let e = lex_errors[ei];
        let errtext = input.slice(e.startidx, e.erridx + 1);
        let tooltipText = `${e.line}:${e.pos} ${e.msg}`;
        let chunk = {tooltip:tooltipText, chunktext:errtext, error:true};
        for (let i = e.startidx; i <= e.erridx; i++) {
            charToChunk[i] = chunk;
        }
    }
    // augment tooltip for any tokens covered by parse error range
    for (let ei in parse_errors) {
        let e = parse_errors[ei];
        let tooltipText = `${e.line}:${e.pos} ${e.msg}`;
        for (let i = tokens[e.startidx].start; i <= tokens[e.stopidx].stop; i++) {
            charToChunk[i].tooltip += '\n'+tooltipText;
            charToChunk[i].error = true;
        }
    }

    // chunkify skipped chars (adjacent into one chunk)
    let i = 0;
    while ( i<input.length ) {
        if ( charToChunk[i]==null ) {
            let a = i;
            while ( charToChunk[i]==null ) {
                i++;
            }
            let b = i;
            let skippedText = input.slice(a, b);
            let chunk = {tooltip:"Skipped", chunktext:skippedText};
            for (let i = a; i < b; i++) {
                charToChunk[i] = chunk;
            }
        }
        else {
            i++;
        }
    }

    // Walk input again to get unique chunks
    i = 0;
    let chunks = [];
    let previousChunk = null;
    while ( i<input.length ) {
        let currentChunk = charToChunk[i];
        if ( currentChunk!=previousChunk ) {
            chunks.push(currentChunk);
        }
        previousChunk = currentChunk;
        i++;
    }
    console.log(chunks);
    return chunks;
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

async function getEditorContent(id) {
    let editors = await monaco.editor.getEditors();
    let index = {'grammar': 0, 'lexgrammar': 1, 'input': 2};
    return editors[index[id]].getValue();
}

function createEditor(id){
    var content = $('#' + id).text();
    $('#' + id).html('');
    monaco.editor.create(document.getElementById(id),{
        value: content,
        lineNumbers: 'on',
        theme:'vs-dark',
        glyphMargin : false,
        fontSize: '12px',
        columnSelection: false,
        wordWrap: 'on',
        dragAndDrop: true,
        bracketPairColorization : {independentColorPoolPerBracketType:true, enabled:true},
        automaticLayout: true
    });
}

// MAIN
$(document).ready(function() {
    String.prototype.sliceReplace = function (start, end, repl) {
        return this.substring(0, start) + repl + this.substring(end);
    };

    $(document).tooltip();

    monaco.languages.register({ id: 'bnf' });

    createEditor('grammar');
    createEditor('lexgrammar');
    createEditor('input');

    $("#tree_header").hide();
    $("#profile_header").hide();

    $("#grammar").show();
    $("#lexgrammar").hide();
    $("#parsertab").addClass("tabs-header-selected");
    $("#lexertab").removeClass("tabs-header-selected");

    $( "#parsertab" ).click(function() {
        $("#grammar").show();
        $("#lexgrammar").hide();
        $("#parsertab").addClass("tabs-header-selected");
        $("#lexertab").removeClass("tabs-header-selected");
    });
    $( "#lexertab" ).click(function() {
        $("#grammar").hide();
        $("#lexgrammar").show();
        $("#parsertab").removeClass("tabs-header-selected");
        $("#lexertab").addClass("tabs-header-selected");
    });

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
});
