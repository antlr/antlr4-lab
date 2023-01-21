import React, {Component} from "react";
import AntlrResult from "../antlr/AntlrResult";

interface IProps { result: AntlrResult}
interface IState { src: string }

export default class TreeView extends Component<IProps, IState> {

    constructor(props: IProps) {
        super(props);
        let src: string = null;
        if(this.props.result && this.props.result.svgtree) {
            const data = btoa(unescape(encodeURIComponent(this.props.result.svgtree)));
            src = "data:image/svg+xml;base64," + data;
        }
        this.state = { src: src };
    }
    render() {
        if(this.state.src)
            return <img src={this.state.src} alt="" />;
        else
            return null;
    }
}
