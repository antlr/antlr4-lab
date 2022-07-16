function showToolErrors(response) {
    if (response.data.parser_grammar_errors.length > 0 ||
        response.data.lexer_grammar_errors.length > 0 ||
        response.data.warnings.length > 0)
    {
        let errors = "<ul>\n";
        response.data.parser_grammar_errors.forEach( function(e) {
            errors += `<li>${e.line}:${e.pos} ${e.msg}</li>`;
        });
        response.data.lexer_grammar_errors.forEach( function(e) {
            errors += `<li>${e.line}:${e.pos} ${e.msg}</li>`;
        });
        response.data.warnings.forEach( function(w) {
            errors += `<li>${w.line}:${w.pos} ${w.msg}</li>`;
        });
        errors += "</ul>\n";
        $("#console").html(errors);
    }
    else {
        $("#console").text("");
    }
}

function showParseErrors(response) {
    if (response.data.result.lex_errors.length > 0 ||
        response.data.result.parse_errors.length > 0 )
    {
        let errors = "<ul>\n";
        response.data.result.lex_errors.forEach( function(e) {
            errors += `<li>${e.line}:${e.pos} ${e.msg}</li>`;
        });
        response.data.result.parse_errors.forEach( function(e) {
            errors += `<li>${e.line}:${e.pos} ${e.msg}</li>`;
        });
        errors += "</ul>\n";
        $("#parse_errors").html(errors);
    }
    else {
        $("#parse_errors").text("");
    }
}

function processANTLRResults(response) {
    var g = $('#grammar').val();
    var lg = $('#lexgrammar').val();
    var I = $('#input').text();
    var s = $('#start').val();
    console.log(response.data.result);

    // $("#t1").tooltip( "option", "content", "Awesome title!" );
    showToolErrors(response);
    showParseErrors(response);

    let tokens = response.data.result.tokens;
    let symbols = response.data.result.symbols;
    let last = -1;
    let newInput = "";
    for (ti in tokens) {
        let t = tokens[ti];
        let toktext = I.slice(t.start, t.stop + 1);
        // console.log(t+' '+console.log(toktext);
        if (t.start != last + 1) {
            let skippedText = I.slice(last + 1, t.start);
            console.log("missing token '" + skippedText + "'");
            newInput += skippedText;
        }
        let tooltipText = `#${ti} Type ${symbols[t.type]} Line ${t.line}:${t.pos}`;
        newInput += `<span id='t${ti}' class='tooltip' title='${tooltipText}'>${toktext}</span>`
        last = t.stop;
    }
    // console.log(newInput);
    $("#input").html(newInput);

    $(function () {
        $('#input span').hover(function () {
            $(this)
                .css('text-decoration', 'underline')
                .css('font-weight', 'bold')
                .css('text-decoration-color', 'darkgray').text();
        }, function () {
            $(this)
                .css('text-decoration', '')
                .css('font-weight', 'normal')
        });
    });
    $('div span').tooltip({
        show: {duration: 0}, hide: {duration: 0}, tooltipClass: "mytooltip"
    });

    console.log(JSON.stringify(response.data.result.tree));

    tree = response.data.result.tree;
    buf = ['<ul id="treeUL">'];
    walk(tree, response.data.result, I, buf);
    buf.push('</ul>');
    console.log(buf.join('\n'));
    $("#tree").html(buf.join('\n'))

    initParseTreeView();
}

function walk(t, result, input, buf) {
    if (t == null) return;

    let symbols = result.symbols;
    let rulenames = result.rules;
    let tokens = result.tokens;
    let ruleidx = t.ruleidx;
    let alt = t.alt;
    // console.log(rulenames[ruleidx]);
    buf.push('<li><span class="box">'+rulenames[ruleidx]+'</span>')
    if (t.kids.length > 0) {
        buf.push('<ul class="nested">');
        for (let i = 0; i < t.kids.length; i++) {
            kid = t.kids[i];
            if (typeof (kid) == 'number') {
                let a = tokens[kid].start;
                let b = tokens[kid].stop;
                // buf.push(`<li>${symbols[tokens[kid].type]}:${input.slice(a, b + 1)}</li>`);
                buf.push(`<li>${input.slice(a, b + 1)}</li>`);
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
    var g = $('#grammar').val();
    var lg = $('#lexgrammar').val();
    var I = $('#input').text();
    var s = $('#start').val();

    await axios.post("http://localhost:8080/antlr/",
        null, // null data
        {params: {grammar: g, lexgrammar: lg, input: I, start: s}}
    )
        .then(processANTLRResults)
}

function initParseTreeView() {
    let toggler = document.getElementsByClassName("box");
    for (let i = 0; i < toggler.length; i++) {
        toggler[i].addEventListener("click", function () {
            this.parentElement.querySelector(".nested").classList.toggle("active");
            this.classList.toggle("check-box");
        });
    }
}

// MAIN
$(document).ready(function() {
    String.prototype.sliceReplace = function (start, end, repl) {
        return this.substring(0, start) + repl + this.substring(end);
    };

    $(document).tooltip();
});
