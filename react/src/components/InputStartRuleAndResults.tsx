import React, {Component, createRef} from "react";
import GrammarSample from "../data/GrammarSample";
import CSS from "csstype";
import {Button, ButtonGroup, Dropdown, FormLabel, Image, OverlayTrigger, Popover} from "react-bootstrap";
import DropdownToggle from "react-bootstrap/DropdownToggle";
import DropdownMenu from "react-bootstrap/DropdownMenu";
import DropdownItem from "react-bootstrap/DropdownItem";
// @ts-ignore
import help from "../assets/helpicon.png";
import AceEditor from "react-ace";
import {IAceEditor} from "react-ace/lib/types";
import "ace-builds/src-noconflict/theme-chrome";
import {SAMPLE_INPUT} from "../data/Samples";

interface IProps { sample: GrammarSample }
interface IState { exampleName: string }

const EXAMPLE_PREFIX = "https://raw.githubusercontent.com/antlr/grammars-v4/master/";

export default class InputStartRuleAndResults extends Component<IProps, IState> {

    editorRef: any;

    constructor(props: IProps) {
        super(props);
        this.editorRef = createRef();
        this.state = { exampleName: this.props.sample.examples[0] };
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
            this.setState({ exampleName: this.props.sample.examples[0] }, () => this.loadEditorWithInputSample());
        }
    }

    render() {
        // @ts-ignore
        return <div className="h-100">
            { this.renderHeader() }
            { this.renderEditor() }
            { this.renderConsole() }
            { this.renderTree() }
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
                    { this.props.sample.examples.map((example, idx) => <DropdownItem key={idx} eventKey={idx}>{example}</DropdownItem>) }
                </DropdownMenu>
            </Dropdown>
            <OverlayTrigger overlay={props => this.showHelp(props)} placement="bottom" >
                <Image style={{width: "20px", height: "20px"}} src={help} alt="" />
            </OverlayTrigger>
        </div>;
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
        return <AceEditor className="input-editor" ref={this.editorRef} width="calc(100%-10px)" height="300px" mode="text" editorProps={{$blockScrolling: Infinity}} />;
    }

    renderConsole() {
        return <div/>;
    }

    renderTree() {
        return <div/>;
    }
}
