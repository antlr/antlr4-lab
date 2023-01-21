
export default interface AntlrTree {
    alt: number;
    kids: (AntlrTree | number)[];
    ruleidx: number;
    error: string;
}
