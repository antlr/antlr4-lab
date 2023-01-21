import {LexerError, ParserError} from "./AntlrError";
import AntlrToken from "./AntlrToken";
import AntlrTree from "./AntlrTree";
import ProfilerData from "./ProfilerData";


export default interface AntlrResult {
    input: string;
    lex_errors: LexerError[];
    parse_errors: ParserError[];
    profile: ProfilerData;
    rules: string[];
    svgtree: string;
    symbols: string[];
    tokens: AntlrToken[];
    tree: AntlrTree;
}
