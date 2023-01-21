import React, {Component, DragEvent, createRef} from "react";
import AceEditor from "react-ace";
import CSS from "csstype";
import {
    Button,
    ButtonGroup,
    Dropdown,
    Image,
    OverlayTrigger, Popover,
    ToggleButton
} from "react-bootstrap";
import GrammarSample from "../data/GrammarSample";
import DropdownItem from "react-bootstrap/DropdownItem";
import DropdownToggle from "react-bootstrap/DropdownToggle";
import DropdownMenu from "react-bootstrap/DropdownMenu";
// @ts-ignore
import help from "../assets/helpicon.png";
import {IAceEditor} from "react-ace/lib/types";
import { createEditSession, Ace } from "ace-builds";
import AntlrMode from "../ace/AntlrMode";
import "ace-builds/src-noconflict/theme-chrome";
import {SAMPLE_LEXER, SAMPLE_PARSER} from "../data/Samples";
import GrammarType from "../antlr/GrammarType";
import AntlrResponse from "../antlr/AntlrResponse";
import {clearSessionExtras} from "../ace/AceUtils";
import {ToolError} from "../antlr/AntlrError";

interface IProps { samples: GrammarSample[]; sample: GrammarSample; sampleSelected: (sample: GrammarSample) => void, onChange: () => void }
interface IState { grammarType: GrammarType, dragOver: boolean }

export default class GrammarEditor extends Component<IProps, IState> {

    editorRef: any;
    grammarSessions: Ace.EditSession[] = [ null, null ];

    constructor(props: IProps) {
        super(props);
        this.editorRef = createRef();
        this.state = { grammarType: GrammarType.PARSER, dragOver: false }
    }

   componentDidMount() {
        this.initializeEditor();
        this.loadEditorWithGrammarSample(this.props.sample);
    }

    get aceEditor(): IAceEditor {
        return (this.editorRef.current as AceEditor).editor;
    }

    grammar(grammarType: GrammarType): string {
        return this.grammarSessions[grammarType].getValue();
    }

    initializeEditor() {
        const antlrMode: Ace.SyntaxMode = new AntlrMode() as unknown as Ace.SyntaxMode;
        this.grammarSessions[GrammarType.LEXER] = createEditSession("", antlrMode);
        this.grammarSessions[GrammarType.PARSER] = createEditSession("", antlrMode);
        this.aceEditor.setSession(this.grammarSessions[GrammarType.PARSER]);
        this.aceEditor.setOptions({
            theme: 'ace/theme/chrome',
            "highlightActiveLine": false,
            "readOnly": false,
            "showLineNumbers": true,
            "showGutter": true,
            "printMargin": false
        });
    }

    loadEditorWithGrammarSample(sample: GrammarSample) {
        if(!sample)
            return;
        if(sample.name === "Sample" )
            this.loadEditorWithBuiltInGrammarSample();
        else
            this.loadEditorWithRemoteGrammarSample(sample);
        this.clearEditorExtras();
    }

    loadEditorWithBuiltInGrammarSample() {
        this.grammarSessions[GrammarType.LEXER].setValue(SAMPLE_LEXER);
        this.grammarSessions[GrammarType.PARSER].setValue(SAMPLE_PARSER);
    }

    loadEditorWithRemoteGrammarSample(sample: GrammarSample) {
        if(sample.lexer) {
            fetch(sample.lexer)
                .then(async response => {
                    const text = await response.text();
                    this.grammarSessions[GrammarType.LEXER].setValue(text);
                })
                .catch(reason => console.log(reason));
        } else
            this.grammarSessions[GrammarType.LEXER].setValue("");
        fetch(sample.parser)
            .then(async response => {
                const text = await response.text();
                this.grammarSessions[GrammarType.PARSER].setValue(text);
            })
            .catch(reason => console.log(reason));
    }

    clearEditorExtras() {
        this.grammarSessions.forEach(session => clearSessionExtras(session), this);
    }

    render() {
        // @ts-ignore
        return <div className="h-100">
            { this.renderHeader() }
            { this.renderEditor() }
        </div>;
    }

    renderHeader() {
        const style: CSS.Properties = {height: "32px" };
        return <div style={style}>
            <ButtonGroup className="grammar-type">
                <ToggleButton type="radio" variant="secondary" name="grammar-type" value={GrammarType.LEXER} checked={this.state.grammarType===GrammarType.LEXER}
                              onClick={e => this.toggleGrammarType(GrammarType.LEXER)}>Lexer</ToggleButton>
                <ToggleButton type="radio" variant="secondary" name="grammar-type" value={GrammarType.PARSER} checked={this.state.grammarType===GrammarType.PARSER}
                              onClick={e => this.toggleGrammarType(GrammarType.PARSER)}>Parser</ToggleButton>
            </ButtonGroup>
            <Dropdown as={ButtonGroup} className="selector" onSelect={(idx) => this.sampleSelected(this.props.samples[parseInt(idx)])}>
                <Button variant="secondary">{this.props.sample.name}</Button>
                <DropdownToggle split variant="secondary" />
                <DropdownMenu align="end">
                    { this.props.samples.map((sample, idx) => <DropdownItem key={idx} eventKey={idx}>{sample.name}</DropdownItem>) }
                </DropdownMenu>
            </Dropdown>
            <OverlayTrigger overlay={props => this.showHelp(props)} placement="bottom" >
                <Image style={{width: "20px", height: "20px"}} src={help} alt="" />
            </OverlayTrigger>
        </div>;
    }

    sampleSelected(sample: GrammarSample): void {
        this.props.sampleSelected(sample);
        this.loadEditorWithGrammarSample(sample);
        this.toggleGrammarType(GrammarType.PARSER);
    }

    showHelp(props: { [props: string]: any }) {
        return <Popover className="help-popover" {...props}>
            <Popover.Body >
            Enter your grammar here using ANTLR notation.<br/>
            You can also drag and drop a .g4 file.<br/>
            Or you can select a grammar from the drop-down list.<br/>
            (the list is sourced from https://github.com/antlr/grammars-v4).<br/>
            Put combined grammars in the Parser tab and erase content from the Lexer tab.<br/>
            Hover over red gutter annotation to see error messages.<br/>
            Enter some input to the right and hit the Run button to test.
            </Popover.Body>
        </Popover>;
    }

    toggleGrammarType(value: any) {
        this.setState({grammarType: value as GrammarType}, () => this.aceEditor.setSession(this.grammarSessions[value]));
    }

    renderEditor() {
        const className = "grammar-editor" + (this.state.dragOver ? " drag-over" : "");
        const style: CSS.Properties = {position: "relative", width: "100%" };
        return <div className="calc-h-100-32" style={style}
                    onDragOver={this.onDragOver.bind(this)} onDragLeave={()=>this.setState({dragOver: false})} onDrop={this.onDrop.bind(this)}>
            <AceEditor ref={this.editorRef} className={className} width="100%" height="100%" mode="text" editorProps={{$blockScrolling: Infinity}} onChange={()=>this.grammarChanged()}/>
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
                    const session = this.grammarSessions[this.state.grammarType];
                    clearSessionExtras(session);
                    session.setValue(text);
                    this.props.onChange();
                });
            }
        }
    }

    grammarChanged() {
        clearSessionExtras(this.grammarSessions[this.state.grammarType]);
        this.props.onChange();
    }

    processResponse(response: AntlrResponse) {
        this.clearEditorExtras();
        this.grammarSessions[GrammarType.LEXER].setAnnotations(this.createToolErrorAnnotations(response.lexer_grammar_errors));
        this.grammarSessions[GrammarType.PARSER].setAnnotations(this.createToolErrorAnnotations(response.parser_grammar_errors));
    }

    createToolErrorAnnotations(errors: ToolError[]) {
        return errors.map(error => ({row: error.line - 1, text: error.msg, type: "error"}));
    }
}
