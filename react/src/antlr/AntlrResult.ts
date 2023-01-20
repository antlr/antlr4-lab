import {LexerError, ParserError} from "./AntlrError";
import AntlrToken from "./AntlrToken";

interface Profile {
    colnames: string[];
    data: string[][];
}

interface Tree {
    alt: number;
    kids: (Tree | number)[];
    ruleidx: number;
}

export default interface AntlrResult {
    input: string;
    lex_errors: LexerError[];
    parse_errors: ParserError[];
    profile: Profile;
    rules: string[];
    svgtree: string;
    symbols: string[];
    tokens: AntlrToken[];
    tree: Tree;
}
