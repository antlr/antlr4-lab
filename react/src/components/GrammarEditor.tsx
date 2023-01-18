import React, {Component, createRef} from "react";
import AceEditor from "react-ace";
import CSS from "csstype";
import {ButtonGroup, DropdownButton, ToggleButton} from "react-bootstrap";
import GrammarSample from "../data/GrammarSample";
import DropdownItem from "react-bootstrap/DropdownItem";

enum GrammarType {
    LEXER,
    PARSER
}

interface IProps { samples: GrammarSample[]; sample: GrammarSample, sampleSelected: (sample: GrammarSample) => void }
interface IState { grammarType: GrammarType }

export default class GrammarEditor extends Component<IProps, IState> {

    editorRef: any;

    constructor(props: IProps) {
        super(props);
        this.editorRef = createRef();
        this.state = { grammarType: GrammarType.PARSER }
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
                <ToggleButton type="radio" variant="secondary" name="grammar-type" value={GrammarType.LEXER} checked={this.state.grammarType===GrammarType.LEXER} onClick={e => this.toggleGrammarType(GrammarType.LEXER)}>Lexer</ToggleButton>
                <ToggleButton type="radio" variant="secondary" name="grammar-type" value={GrammarType.PARSER} checked={this.state.grammarType===GrammarType.PARSER} onClick={e => this.toggleGrammarType(GrammarType.PARSER)}>Parser</ToggleButton>
            </ButtonGroup>
            <DropdownButton className="grammar-selector" title={this.props.sample.name} as={ButtonGroup} variant="secondary" onSelect={(idx) => this.props.sampleSelected(this.props.samples[parseInt(idx)])}>
                { this.props.samples.map((sample, idx) => <DropdownItem key={idx} eventKey={idx}>{sample.name}</DropdownItem>) }
            </DropdownButton>
        </div>;
    }

    toggleGrammarType(value: any) {
        // TODO toggle editor session
        this.setState({grammarType: value as GrammarType});
    }

    renderEditor() {
        const style: CSS.Properties = {position: "relative", width: "100%" };
        return <div className="calc-h-100-32" style={style}>
            <AceEditor ref={this.editorRef} width="100%" height="100%" mode="text" editorProps={{$blockScrolling: Infinity}} />
        </div>;
    }
}
