import GrammarSample from "./GrammarSample";

const SAMPLE_PARSER = `parser grammar ExprParser;

options { tokenVocab=ExprLexer; }

program
    : stat EOF
    | def EOF
    ;

//"foo : 'a' 'abc' 'a\\'b' '\\u34ab' 'ab\\ncd' ;
stat: ID '=' expr ';'
    | expr ';'
    ;

def : ID '(' ID (',' ID)* ')' '{' stat* '}' ;

expr: ID
    | INT
    | func
    | 'not' expr
    | expr 'and' expr
    | expr 'or' expr
    ;

func : ID '(' expr (',' expr)* ')' ;`;

const SAMPLE_LEXER = `// DELETE THIS CONTENT IF YOU PUT COMBINED GRAMMAR IN Parser TAB
lexer grammar ExprLexer;

AND : 'and' ;
OR : 'or' ;
NOT : 'not' ;
EQ : '=' ;
COMMA : ',' ;
SEMI : ';' ;
LPAREN : '(' ;
RPAREN : ')' ;
LCURLY : '{' ;
RCURLY : '}' ;

INT : [0-9]+ ;
ID: [a-zA-Z_][a-zA-Z_0-9]* ;
WS: [ \\t\\n\\r\\f]+ -> skip ;`;

const SAMPLE_INPUT = `f(x,y) {
    a = 3+foo;
    x and y;
}`

const SAMPLE_GRAMMAR: GrammarSample = { name: "Sample", lexer: "ExprLexer.g4", parser: "ExprParser.g4", start: "program", examples: [ "sample.expr" ] };

export { SAMPLE_GRAMMAR, SAMPLE_LEXER, SAMPLE_PARSER, SAMPLE_INPUT }
