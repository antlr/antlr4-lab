import {Component} from "react";
import Welcome from "./Welcome";
import GrammarEditor from "./GrammarEditor";
import InputStartRuleAndResults from "./InputStartRuleAndResults";

export default class App extends Component {

    constructor() {
        super();
        this.state = { showWelcome: false, editorWidth: 50 };
    }

    render() {
        const leftStyle = { width: "" + this.state.editorWidth + "%", float: "left", padding: 0 };
        const rightStyle = { width: "" + (100 - this.state.editorWidth) + "%", float: "left", padding: 0 };
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
        return <div draggable={true} className="h-100 splitter" style={{width: "4px", float: "left"}} onDrag={(e)=>this.computeEditorWidth(e.currentTarget.offsetLeft, e.clientX)}/>;
    }

    computeEditorWidth(current, proposed) {
        if(proposed <= 0)
            return;
        const totalWidth = current * (100 / this.state.editorWidth);
        const editorWidth = proposed / totalWidth;
        // console.log("current: " + this.state.editorWidth + ", computed: " + editorWidth * 100);
        this.setState({editorWidth: editorWidth * 100});
    }

}
