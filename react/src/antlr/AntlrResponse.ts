import {ToolError} from "./AntlrError";
import AntlrResult from "./AntlrResult";

export default interface AntlrResponse {
    lexer_grammar_errors: ToolError[];
    parser_grammar_errors: ToolError[];
    result: AntlrResult;
    warnings: ToolError[];
}
