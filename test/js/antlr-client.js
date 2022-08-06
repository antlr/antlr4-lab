"use strict";

function processANTLRResults(response) {
    var g = $('#grammar').text();
    var lg = $('#lexgrammar').text();
    var I = $('#input').text();
    var s = $('#start').text();
    console.log(response.data.result);

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

    // $("#t1").tooltip( "option", "content", "Awesome title!" );
    showToolErrors(response);
    showParseErrors(response);

    let tokens = response.data.result.tokens;
    let symbols = response.data.result.symbols;
    let lex_errors = response.data.result.lex_errors;
    let parse_errors = response.data.result.parse_errors;

    let profile = response.data.result.profile;

    let chunks = chunkifyInput(I, tokens, symbols, lex_errors, parse_errors);
    chunks = chunks.map(c =>
         `<span ${'error' in c ? 'style="color:#E93A2B; text-decoration: underline dotted #E93A2B;"' : ""} class='tooltip' title='${c.tooltip}'>${c.chunktext}</span>`
    );
    let newInput = chunks.join('');

    $("#input").html(newInput);

    $('#input span').hover(function (event) {
        if ( !event.ctrlKey ) return;
        let oldStyle = $(this).css('text-decoration');
        if ( $(this).css("cursor")!=="pointer" ) {
            $(this).css('cursor','pointer');
        }
        $(this).data( "text-decoration", oldStyle ); // save
        $(this)
            .css('text-decoration', 'underline')
            .css('font-weight', 'bold')
            .css('text-decoration-color', 'darkgray').text();
    }, function () {
        let oldStyle = $(this).data( "text-decoration");
        if ( $(this).css("cursor")==="pointer" ) {
            $(this).css('cursor','auto');
        }
        $(this)
            .css('text-decoration', oldStyle)
            .css('font-weight', 'normal')
    });

    $('div span').tooltip({
        show: {duration: 0}, hide: {duration: 0}, tooltipClass: "mytooltip"
    });

    // $(document).tooltip("disable").tooltip("hide");
    $(document).keydown(
        e => e.ctrlKey ? $(document).tooltip("enable") : null
    );
    $(document).keyup(
        e => $(document).tooltip("disable")
    );
    $(document).tooltip("disable");

    let tree = response.data.result.tree;
    let buf = ['<ul id="treeUL">'];
    walk(tree, response.data.result, I, buf);
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
    buf.push('<li><span class="tree-root">'+rulenames[ruleidx]+'</span>')
    if ( 'kids' in t && t.kids.length > 0) {
        buf.push('<ul class="nested">');
        for (let i = 0; i < t.kids.length; i++) {
            let kid = t.kids[i];
            if (typeof (kid) == 'number') {
                let a = tokens[kid].start;
                let b = tokens[kid].stop;
                // buf.push(`<li>${symbols[tokens[kid].type]}:${input.slice(a, b + 1)}</li>`);
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
    var g = $('#grammar').text();
    var lg = $('#lexgrammar').text();
    var I = $('#input').text();
    var s = $('#start').text();

    $("#profile_choice").show();

    await axios.post("http://localhost:80/antlr/",
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

// MAIN
$(document).ready(function() {
    String.prototype.sliceReplace = function (start, end, repl) {
        return this.substring(0, start) + repl + this.substring(end);
    };

    $(document).tooltip();

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
