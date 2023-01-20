import AntlrResult from "../antlr/AntlrResult";

export default interface Chunk {
    tooltip: string;
    chunktext: string;
    start: number;
    stop: number
}


export function chunkifyInput(input: string, result: AntlrResult): Chunk[] {
    const chunks: Chunk[] = new Array(input.length);
    for (let ti in result.tokens) {
        const t = result.tokens[ti];
        const toktext = input.slice(t.start, t.stop + 1);
        const tooltipText = `#${ti} Type ${result.symbols[t.type]} Line ${t.line}:${t.pos}`;
        const chunk = { tooltip:tooltipText, chunktext:toktext, start: t.start, stop: t.stop + 1 };
        for (let i = t.start; i <= t.stop; i++) {
            chunks[i] = chunk;
        }
    }
    for (let ei in result.lex_errors) { // set lex error tokens to just error tokens
        let e = result.lex_errors[ei];
        let errtext = input.slice(e.startidx, e.erridx + 1);
        let chunk = {tooltip:"token recognition error", chunktext:errtext, start: e.startidx, stop: e.erridx+1, error:true};
        for (let i = e.startidx; i <= e.erridx; i++) {
            chunks[i] = chunk;
        }
    }
    // chunkify skipped chars (adjacent to one chunk)
    let i = 0;
    while ( i<input.length ) {
        if ( chunks[i]==null ) {
            let a = i;
            while ( chunks[i]==null && i<input.length ) {
                i++;
            }
            let b = i;
            let skippedText = input.slice(a, b);
            let chunk = {tooltip:"Skipped", chunktext:skippedText, start: a, stop: b};
            for (let i = a; i < b; i++) {
                chunks[i] = chunk;
            }
        }
        else {
            i++;
        }
    }
    return chunks;
}

