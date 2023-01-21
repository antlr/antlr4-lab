import React, {Component, MouseEvent, DragEvent, createRef} from "react";
import GrammarSample from "../data/GrammarSample";
import CSS from "csstype";
import {
    Button,
    ButtonGroup,
    Dropdown,
    FormCheck,
    FormControl,
    FormLabel,
    Image,
    OverlayTrigger,
    Popover, Tab, Tabs
} from "react-bootstrap";
import DropdownToggle from "react-bootstrap/DropdownToggle";
import DropdownMenu from "react-bootstrap/DropdownMenu";
import DropdownItem from "react-bootstrap/DropdownItem";
// @ts-ignore
import help from "../assets/helpicon.png";
import AceEditor from "react-ace";
import {IAceEditor} from "react-ace/lib/types";
import "ace-builds/src-noconflict/theme-chrome";
import {SAMPLE_INPUT} from "../data/Samples";
import AntlrInput from "../antlr/AntlrInput";
import AntlrResponse from "../antlr/AntlrResponse";
import {AntlrError, LexerError, ParserError, ToolError} from "../antlr/AntlrError";
import {clearSessionExtras} from "../ace/AceUtils";
import Chunk, {chunkifyInput} from "../ace/Chunk";
import {Ace, Range} from "ace-builds";
import AntlrToken from "../antlr/AntlrToken";
import TreeView from "./TreeView";
import HierarchyView from "./HierarchyView";
import ProfilerView from "./ProfilerView";

interface IProps { sample: GrammarSample, onRun: (input: AntlrInput) => void }
interface IState { exampleName: string, startRule: string, showProfiler: boolean, response: AntlrResponse, chunks: Chunk[], lastTokenRangeMarker: number, chunk: Chunk, dragOver: boolean }

// const EXAMPLE_PREFIX = "https://raw.githubusercontent.com/antlr/grammars-v4/master/";

export default class InputStartRuleAndResults extends Component<IProps, IState> {

    editorRef: any;

    constructor(props: IProps) {
        super(props);
        this.editorRef = createRef();
        this.state = { exampleName: this.props.sample.examples[0], startRule: this.props.sample.start, showProfiler: false, response: null, chunks: null, lastTokenRangeMarker: 0, chunk: null, dragOver: false };
    }

    componentDidMount() {
        this.initializeEditor();
        this.loadEditorWithInputSample();
    }

    get aceEditor(): IAceEditor {
        return (this.editorRef.current as AceEditor).editor;
    }

    initializeEditor() {
        this.aceEditor.setOptions({
            theme: 'ace/theme/chrome',
            "highlightActiveLine": false,
            "readOnly": false,
            "showLineNumbers": true,
            "showGutter": true,
            "printMargin": false
        });
    }

    loadEditorWithInputSample() {
        if(this.props.sample.name === "Sample")
            this.aceEditor.getSession().setValue(SAMPLE_INPUT);
        else {
            let url = this.props.sample.parser.substring(0, this.props.sample.parser.lastIndexOf("/"));
            url += "/examples/" + this.state.exampleName;
            fetch(url)
                .then(async response => {
                    const text = await response.text();
                    this.aceEditor.getSession().setValue(text);
                })
                .catch(reason => console.log(reason));
        }
    }

    componentDidUpdate(prevProps: Readonly<IProps>, prevState: Readonly<IState>, snapshot?: any) {
        if(this.props.sample !== prevProps.sample) {
            this.setState({ exampleName: this.props.sample.examples[0], startRule: this.props.sample.start, response: null, chunks: null, lastTokenRangeMarker: 0, chunk: null }, () => this.loadEditorWithInputSample());
        }
    }

    render() {
        // @ts-ignore
        return <div className="h-100">
            { this.renderHeader() }
            { this.renderEditor() }
            { this.renderStartRule() }
            { this.renderToolConsole() }
            { this.renderParserConsole() }
            { this.renderTree() }
            { this.renderProfileResults() }
        </div>;
    }

    renderHeader() {
        const style: CSS.Properties = {height: "32px" };
        return <div style={style}>
            <FormLabel className="selector-label">&nbsp;Input&nbsp;</FormLabel>
            <Dropdown as={ButtonGroup} className="selector" onSelect={(idx) => this.inputSelected(this.props.sample.examples[parseInt(idx)])}>
                <Button variant="secondary">{this.state.exampleName}</Button>
                <DropdownToggle split variant="secondary" />
                <DropdownMenu align="end">
                    { this.props.sample.examples.map((example, idx) => <DropdownItem key={"item-" + idx} eventKey={idx}>{example}</DropdownItem>) }
                </DropdownMenu>
            </Dropdown>
            <OverlayTrigger overlay={props => this.showHelp(props)} placement="bottom" >
                <Image style={{width: "20px", height: "20px"}} src={help} alt="" />
            </OverlayTrigger>
            { this.renderChunk() }
        </div>;
    }

    renderChunk() {
        if(this.state.chunk) {
            const style: CSS.Properties = this.state.chunk.tooltip.indexOf("error") >= 0  ? { color: "#9A2E06" } : {};
            return <FormLabel className="selector-label" style={style}>&nbsp;({this.state.chunk.tooltip})&nbsp;</FormLabel>;
        } else
            return null;
    }

    inputSelected(example: string) {
        this.setState({ exampleName: example }, () => this.loadEditorWithInputSample());
    }

    showHelp(props: { [props: string]: any }) {
        return <Popover className="help-popover" {...props}>
            <Popover.Body >
                Enter text that follows the syntax described in your grammar. <br/>
                You can also drag and drop a text file. <br/>
                Or you can select a sample input  from the drop-down list.<br/>
                Then hit the Run button to test.<br/>
                You can then move mouse over text to see how the tokens were matched. <br/>
                Hover over red gutter annotation to see error messages.
            </Popover.Body>
        </Popover>;
    }

    renderEditor() {
        const className = "input-editor" + (this.state.dragOver ? " drag-over" : "");
        return <div onMouseUp={() => this.aceEditor.resize()} onMouseMove={e => this.showTokenRange(e)} onMouseLeave={() => this.clearTokenRange()}
                        onDragOver={this.onDragOver.bind(this)} onDragLeave={()=>this.setState({dragOver: false})} onDrop={this.onDrop.bind(this)}>
                    <AceEditor ref={this.editorRef} className={className} width="calc(100%-10px)" height="300px" mode="text" editorProps={{$blockScrolling: Infinity}} onChange={()=>this.inputChanged()}/>
                </div>;
    }

    onDragOver(event: DragEvent) {
        event.preventDefault(); // otherwise you don't get a drop
        if(event.dataTransfer.types.indexOf("Files") >= 0) {
            this.setState({dragOver: true});
        }
    }

    onDrop(event: DragEvent) {
        event.preventDefault();
        this.setState({dragOver: false});
        for(let i = 0; i < event.dataTransfer.items.length; i++ ) {
            const item = event.dataTransfer.items[i];
            if(item.kind === "file") {
                const file = item.getAsFile();
                file.text().then(text => {
                    const session = this.aceEditor.getSession();
                    clearSessionExtras(session);
                    session.setValue(text);
                    this.setState({ response: null, chunks: null, lastTokenRangeMarker: 0, chunk: null });
                });
            }
        }
    }

    showTokenRange(event: MouseEvent) {
        if(!this.state.chunks)
            return;
        // @ts-ignore
        const pos: Position = this.aceEditor.renderer.screenToTextCoordinates(event.clientX, event.clientY);
        const session = this.aceEditor.getSession();
        this.clearTokenRange();
        let ci = session.doc.positionToIndex(pos);
        const chunks = this.state.chunks;
        if(ci >= chunks.length)
            ci = chunks.length - 1;
        const chunk = chunks[ci];
        if(chunk) {
            const a = session.doc.indexToPosition(chunk.start, 0);
            const b = session.doc.indexToPosition(chunk.stop, 0);
            const r = new Range(a.row, a.column, b.row, b.column);
            const marker = session.addMarker(r, "token_range_class", "text");
            this.setState({lastTokenRangeMarker: marker, chunk: chunk});
        } else
            this.setState({lastTokenRangeMarker: 0, chunk: null});
    }

    clearTokenRange() {
        if(this.state.lastTokenRangeMarker)
            this.aceEditor.getSession().removeMarker(this.state.lastTokenRangeMarker);
        this.setState({lastTokenRangeMarker: 0, chunk: null});
    }

    inputChanged() {
        clearSessionExtras(this.aceEditor.getSession());
        this.setState({ response: null, chunks: null, lastTokenRangeMarker: 0, chunk: null });
    }

    renderStartRule() {
        return <>
                 <OverlayTrigger overlay={props => this.showHelpStartRule(props)} placement="bottom" >
                     <div className="run-label-box">
                         <h6 style={{float: "left", paddingLeft: "4px", paddingTop: "6px", paddingRight: "6px"}}>Start rule</h6>
                        <Image style={{width: "20px", height: "20px"}} src={help} alt="" />
                     </div>
                </OverlayTrigger>
                <div className="run-button-box">
                    <FormControl className="start-rule-input" value={this.state.startRule} onChange={e => this.setState({startRule: e.currentTarget.value})} />
                    <button type="button" className="run-button" onClick={()=>this.runAntlr()}>Run</button>
                    <OverlayTrigger overlay={props => this.showHelpProfiler(props)} placement="bottom" >
                        <FormCheck className="profiler-switch" type="switch" label="Show profiler" onClick={()=>this.setState({showProfiler: !this.state.showProfiler})}/>
                    </OverlayTrigger>
                </div>
                </>;
    }

    runAntlr() {
        const input: AntlrInput = {
            lexgrammar: null,
            grammar: null,
            start: this.state.startRule,
            input: this.aceEditor.getSession().getValue()
        }
        this.props.onRun(input);
    }

    showHelpStartRule(props: { [props: string]: any }) {
        return <Popover {...props}>
            <Popover.Body >
                Enter a rule name here from your grammar to the left where parsing should begin for the input specified above.<br/>
                Hit Run to test!
            </Popover.Body>
        </Popover>;
    }

    showHelpProfiler(props: { [props: string]: any }) {
        return <Popover {...props}>
            <Popover.Body >
                Info on the parsing decisions made by the parse for this input.<br/>
                The deeper the lookahead (max k), the more expensive the decision.
            </Popover.Body>
        </Popover>;
    }

    renderToolConsole() {
        let errors: ToolError[] = [];
        if(this.state.response) {
            if(this.state.response.parser_grammar_errors)
                errors = errors.concat(this.state.response.parser_grammar_errors);
            if(this.state.response.lexer_grammar_errors)
                errors = errors.concat(this.state.response.lexer_grammar_errors);
            if(this.state.response.warnings)
                errors = errors.concat(this.state.response.warnings);
        }
        if(errors.length) {
            // use a double span to avoid incorrect react warning when using fragment
            return <>
                <div className="chunk-header">Tool console</div>
                <div className="console">
                    { errors.map((error, idx) => <span key={"span-" + idx} ><span className="error-message">{error.msg}</span><br/></span>) }
                </div>
            </>
        } else
            return null;
    }

    renderParserConsole() {
        let errors: AntlrError[] = [];
        if(this.state.response && this.state.response.result) {
            if(this.state.response.result.lex_errors)
                errors = errors.concat(this.state.response.result.lex_errors);
            if(this.state.response.result.parse_errors)
                errors = errors.concat(this.state.response.result.parse_errors);
        }
        if(errors.length) {
            // use a double span to avoid incorrect react warning when using fragment
            return <>
                <div className="chunk-header">Parser console</div>
                <div className="console">
                    { errors.map((error,idx) => <span key={"span-" + idx}><span className="error-message">{"" + error.line + ":" + error.pos + " " + error.msg}</span><br /></span>) }
                </div>
            </>
        } else
            return null;
    }

    renderTree() {
        if(this.state.response && this.state.response.result) {
            const input = this.aceEditor.getSession().getValue();
            return <Tabs defaultActiveKey="tree" className="tree-tabs">
                <Tab eventKey="tree" title="Tree">
                    <TreeView result={this.state.response.result}/>
                </Tab>
                <Tab eventKey="hierarchy" title="Hierarchy">
                    <HierarchyView input={input} result={this.state.response.result}/>
                </Tab>
            </Tabs>;
        } else
            return null;
     }

    renderProfileResults() {
        if(this.state.response && this.state.response.result && this.state.response.result.profile && this.state.showProfiler)
            return <ProfilerView profilerData={this.state.response.result.profile}/>;
        else
            return null;
    }

    processResponse(response: AntlrResponse) {
        if(!this.aceEditor) // happens during mount
            return;
        const session = this.aceEditor.getSession();
        clearSessionExtras(session);
        if(response) {
            let annotations: Ace.Annotation[] = [];
            if (response.result.lex_errors)
                annotations = annotations.concat(InputStartRuleAndResults.addLexerMarkersAndAnnotations(session, response.result.lex_errors));
            if (response.result.lex_errors)
                annotations = annotations.concat(InputStartRuleAndResults.addParserMarkersAndAnnotations(session, response.result.parse_errors, response.result.tokens));
            session.setAnnotations(annotations);
            const chunks = chunkifyInput(session.getValue(), response.result);
            this.setState({response: response, chunks: chunks, lastTokenRangeMarker: 0, chunk: null});
        } else
            this.setState({response: null, chunks: null, lastTokenRangeMarker: 0, chunk: null});
    }

    static addLexerMarkersAndAnnotations(session: Ace.EditSession, errors: LexerError[]): Ace.Annotation[] {
        return errors.map(error => {
            const a = session.doc.indexToPosition(error.startidx, 0);
            const b = session.doc.indexToPosition(error.erridx + 1, 0);
            const r = new Range(a.row, a.column, b.row, b.column);
            session.addMarker(r, "lexical_error_class", "text", false);
            return { row: a.row, text: `${error.line}:${error.pos} ${error.msg}`, type: "error"};
        });
    }

    static addParserMarkersAndAnnotations(session: Ace.EditSession, errors: ParserError[], tokens: AntlrToken[]): Ace.Annotation[] {
        return errors.map(error => {
            const a = session.doc.indexToPosition(tokens[error.startidx].start, 0);
            const b = session.doc.indexToPosition(tokens[error.stopidx].stop + 1, 0);
            const r = new Range(a.row, a.column, b.row, b.column);
            session.addMarker(r, "syntax_error_class", "text", false);
            return { row: a.row, text: `${error.line}:${error.pos} ${error.msg}`, type: "error"};
        });
    }

}
