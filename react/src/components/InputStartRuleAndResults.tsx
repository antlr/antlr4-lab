import React, {Component} from "react";
import GrammarSample from "../data/GrammarSample";
import CSS from "csstype";
import {Button, ButtonGroup, Dropdown, FormLabel, Image, OverlayTrigger, Popover} from "react-bootstrap";
import DropdownToggle from "react-bootstrap/DropdownToggle";
import DropdownMenu from "react-bootstrap/DropdownMenu";
import DropdownItem from "react-bootstrap/DropdownItem";
// @ts-ignore
import help from "../assets/helpicon.png";

interface IProps { sample: GrammarSample }
interface IState { sample: GrammarSample, exampleName: string }

// const EXAMPLE_PREFIX = "https://raw.githubusercontent.com/antlr/grammars-v4/master/";

export default class InputStartRuleAndResults extends Component<IProps, IState> {

    constructor(props: IProps) {
        super(props);
        this.state = { sample: this.props.sample, exampleName: this.props.sample.examples[0] };
    }

    static getDerivedStateFromProps(props: IProps, state: IState): IState {
        if(props.sample !== state.sample) {
            return { sample: props.sample, exampleName: props.sample.examples[0] };
        } else
            return null;
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
        return <div/>;
    }

    renderConsole() {
        return <div/>;
    }

    renderTree() {
        return <div/>;
    }
}
