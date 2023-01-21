import {LexerError, ParserError} from "./AntlrError";
import AntlrToken from "./AntlrToken";
import AntlrTree from "./AntlrTree";

interface Profile {
    colnames: string[];
    data: string[][];
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
    tree: AntlrTree;
}
