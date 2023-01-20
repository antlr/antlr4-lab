export interface AntlrError {
    line: number;
    pos: number;
    msg: string;
}

export interface ToolError extends AntlrError {
    type: string;
}

export interface LexerError extends AntlrError {
    startidx: number;
    erridx: number;
}

export interface ParserError extends AntlrError {
    startidx: number;
    stopidx: number;
}
