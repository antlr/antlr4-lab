function processANTLRResults(response) {
    var g = $('#grammar').val();
    var lg = $('#lexgrammar').val();
    var I = $('#input').text();
    var s = $('#start').val();
    console.log(response.data.result);

    // $("#t1").tooltip( "option", "content", "Awesome title!" );

    if (response.data.parser_grammar_errors.length > 0 || response.data.lexer_grammar_errors.length > 0 || response.data.warnings.length > 0) {
        $("#console").text(JSON.stringify(response.data.parser_grammar_errors) + "\n" + JSON.stringify(response.data.lexer_grammar_errors) + "\n" + JSON.stringify(response.data.warnings));
    } else {
        $("#console").text("");
    }
    let tokens = response.data.result.tokens;
    let symbols = response.data.result.symbols;
    let last = -1;
    let newInput = "";
    for (ti in tokens) {
        let t = tokens[ti];
        let toktext = I.slice(t.start, t.stop + 1);
        console.log(t);
        console.log(toktext);
        if (t.start != last + 1) {
            let skippedText = I.slice(last + 1, t.start);
            console.log("missing token '" + skippedText + "'");
            newInput += skippedText;
        }
        let tooltipText = '#' + ti + ' Type ' + symbols[t.type] + ' Line ' + t.line + ':' + t.pos;
        newInput += "<span class='tooltip' title='" + tooltipText + "'>" + toktext + "</span>"
        last = t.stop;
        // newInput = newInput.sliceReplace(t.start,t.stop+1, "<span title='foo'>"+toktext+"</span>");
        // newInput += "<span title='foo'>"+toktext+"</span>";
    }
    console.log(newInput);
    console.log(response.data.result.lex_errors)
    console.log(response.data.result.parse_errors)
    $("#input").html(newInput);

    $(function () {
        $('div span').hover(function () {
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
}

run_antlr = async function () {
    var g = $('#grammar').val();
    var lg = $('#lexgrammar').val();
    var I = $('#input').text();
    var s = $('#start').val();
    // console.log(I)
    // console.log(s)

    const res = await axios.post("http://localhost:8080/antlr/", null, // null data
        {params: {grammar: g, lexgrammar: lg, input: I, start: s}})
        .then(processANTLRResults)
}

String.prototype.sliceReplace = function (start, end, repl) {
    return this.substring(0, start) + repl + this.substring(end);
};

$( function() { $( document ).tooltip(); } );
