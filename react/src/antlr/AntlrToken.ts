export default interface AntlrToken {
    type: number;
    line: number;
    pos: number;
    start: number;
    stop: number;
    channel: number;
}
