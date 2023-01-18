import React, {Component} from "react";
import Welcome from "./Welcome";
import GrammarEditor from "./GrammarEditor";
import InputStartRuleAndResults from "./InputStartRuleAndResults";
import CSS from 'csstype';

interface IProps {}
interface IState { showWelcome: boolean; editorWidth: number }

export default class App extends Component<IProps, IState> {

    constructor(props: IProps) {
        super(props);
        this.state = { showWelcome: false, editorWidth: 50 };
    }

    render() {
        const leftStyle: CSS.Properties = { width: "" + this.state.editorWidth + "%", float: "left", padding: 0 };
        const rightStyle: CSS.Properties = { width: "" + (100 - this.state.editorWidth) + "%", float: "left", padding: 0 };
        return <><div className="h-100 w-100">
                   <div className="h-100" style={leftStyle}>
                       <GrammarEditor />
                   </div>
                   <div className="h-100" style={rightStyle}>
                       { this.renderSplitter() }
                       <InputStartRuleAndResults/>
                   </div>
                </div>
            { this.renderWelcome() }
            </>;
    }

    renderWelcome() {
        if(this.state.showWelcome)
            return <Welcome onClose={()=>this.setState({showWelcome: false})} />;
        else
            return null;
    }

    renderSplitter() {
        return <div draggable={true} className="h-100 splitter" style={{width: "4px", float: "left"}} onDrag={(e)=>this.updateEditorWidth(e.currentTarget.offsetLeft, e.clientX)}/>;
    }

    updateEditorWidth(current: number, proposed: number) {
        if(proposed <= 0)
            return;
        const totalWidth = current * (100 / this.state.editorWidth);
        const editorWidth = proposed / totalWidth;
        this.setState({editorWidth: editorWidth * 100});
    }

}
