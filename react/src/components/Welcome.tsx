import React, {Component} from "react";
import {Toast, ToastBody, ToastHeader} from "react-bootstrap";
// @ts-ignore
import logo from "../assets/antlrlogo.png";

interface IProps { onClose: () => void }
interface IState {}

export default class Welcome extends Component<IProps, IState> {

    render() {
        return <Toast className="welcome" onClose={this.props.onClose}>
            <ToastHeader>
                <img src={logo} alt="" />
                <strong style={{marginRight: "auto"}}>The lab</strong>
            </ToastHeader>
            <ToastBody>
                Welcome to the ANTLR lab, where you can learn about <a href="https://www.antlr.org/">ANTLR</a> or experiment with
                and test grammars!
                <br/>
                Just hit the <button type="button" className="run-button">Run</button> button to try out the sample grammar.
                <br/>
                To start developing with ANTLR, see&nbsp;<a href="https://github.com/antlr/antlr4/blob/master/doc/getting-started.md">getting started</a>.
                <br/>
                <a href="https://github.com/antlr/antlr4-lab/issues">Feedback/issues</a> are welcome.
                <br/>
                Brought to you by <a href="mailto:parrt@antlr.org">Terence Parr</a>, the maniac behind ANTLR.
                <br/>
                <p style={{marginTop: "10px", fontSize: "11px"}}>
                <b>Disclaimer</b>: <i>This website and related functionality are not meant to be used for private code, data, or other
                intellectual property. Assume everything you enter could become public!
                Grammars and input you enter are submitted to a unix box for execution and possibly persisted on disk or other mechanism.
                Please run antlr4-lab locally to avoid privacy concerns.</i>
                </p>
            </ToastBody>
        </Toast>
    }
}
