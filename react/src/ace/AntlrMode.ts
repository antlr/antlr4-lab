interface IAceHighlightRules {
    $rules: any;
}

// @ts-ignore
class AntlrHighlightRules extends window.ace.acequire(
    "ace/mode/text_highlight_rules"
).TextHighlightRules {

    constructor() {
        super();
        const highlightRules: IAceHighlightRules = this as unknown as IAceHighlightRules;
        highlightRules.$rules = {
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
    }
}

interface IAceMode {
    $id: string;
    HighlightRules: any;
}

// @ts-ignore
export default class AntlrMode extends window.ace.acequire("ace/mode/text").Mode {

    constructor() {
        super();
        const mode: IAceMode = this as unknown as IAceMode;
        mode.$id = "ace/mode/antlr4";
        mode.HighlightRules = AntlrHighlightRules
    }
}

