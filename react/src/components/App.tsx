import React, {Component, createRef, DragEvent} from "react";
import Welcome from "./Welcome";
import GrammarEditor from "./GrammarEditor";
import InputStartRuleAndResults from "./InputStartRuleAndResults";
import CSS from 'csstype';
import GrammarSample from "../data/GrammarSample";
import {fetchGrammarSamples} from "../data/SamplesUtils";
import {SAMPLE_GRAMMAR} from "../data/Samples";
import AntlrInput from "../antlr/AntlrInput";
import GrammarType from "../antlr/GrammarType";
import AntlrResponse from "../antlr/AntlrResponse";

const ANTLR_SERVICE = "/parse/";

interface IProps {}
interface IState { showWelcome: boolean; editorWidth: number, samples: GrammarSample[], sample: GrammarSample }

export default class App extends Component<IProps, IState> {

    grammarEditorRef: any;
    inputEditorRef: any;

    constructor(props: IProps) {
        super(props);
        this.grammarEditorRef = createRef();
        this.inputEditorRef = createRef();
        this.state = { showWelcome: true, editorWidth: 50, samples: [ SAMPLE_GRAMMAR ], sample: SAMPLE_GRAMMAR };
    }

    componentDidMount() {
        fetchGrammarSamples(samples => this.setState({samples: samples, sample: samples[0]}), reason => console.log(reason));
    }

    render() {
        const leftStyle: CSS.Properties = { width: "" + this.state.editorWidth + "%", float: "left", padding: 0 };
        const rightStyle: CSS.Properties = { width: "" + (100 - this.state.editorWidth) + "%", float: "left", padding: 0 };
        return <>
                <div className="h-100 w-100">
                   <div className="h-100" style={leftStyle}>
                       <GrammarEditor ref={this.grammarEditorRef} samples={this.state.samples} sample={this.state.sample} sampleSelected={sample => this.setState({sample: sample})} onChange={() => this.grammarChanged()}/>
                   </div>
                   <div className="h-100" style={rightStyle}>
                       { this.renderSplitter() }
                       <InputStartRuleAndResults ref={this.inputEditorRef} sample={this.state.sample} onRun={this.runAntlr.bind(this)}/>
                   </div>
                </div>
                { this.renderWelcome() }
            </>;
    }

    grammarChanged() {
        const inputEditor: InputStartRuleAndResults = this.inputEditorRef.current;
        if(!inputEditor) // happens during initial mount
            return;
        inputEditor.processResponse(null);
    }

    runAntlr(input: AntlrInput) {
        const grammarEditor: GrammarEditor = this.grammarEditorRef.current;
        input.lexgrammar = grammarEditor.grammar(GrammarType.LEXER);
        input.grammar = grammarEditor.grammar(GrammarType.PARSER);
        const url = document.URL.indexOf("localhost")>=0 ? "http://localhost" + ANTLR_SERVICE : ANTLR_SERVICE;
        fetch(url, { method: "POST", headers: { "Accept": "application/json", "Content-Type": "application/json"}, body: JSON.stringify(input)})
            .then(async resp => {
                const json = await resp.json();
                const response = json as AntlrResponse;
                grammarEditor.processResponse(response);
                const inputEditor: InputStartRuleAndResults = this.inputEditorRef.current;
                inputEditor.processResponse(response);
            })
            .catch(reason => console.log(reason));
    }

    renderWelcome() {
        if(this.state.showWelcome)
            return <Welcome onClose={()=>this.setState({showWelcome: false})} />;
        else
            return null;
    }

    renderSplitter() {
        return <div draggable={true} className="h-100 splitter" style={{width: "4px", float: "left"}}
                    onDragStart={e => this.preventAnimation()}
                    onDragEnd={e => this.unpreventAnimation()}
                    onDrag={(e)=>this.updateEditorWidth(e)}
                    />;
    }

    preventAnimation() {
        document.addEventListener('dragover', event => event.preventDefault() );
    }

    unpreventAnimation() {
        document.removeEventListener('dragover', event => event.preventDefault() );
    }

    updateEditorWidth(event: DragEvent<HTMLDivElement>) {
        const currentWidth = event.currentTarget.offsetLeft;
        const proposedWidth = event.clientX;
        if(proposedWidth >= 300) {
            const totalWidth = currentWidth * (100 / this.state.editorWidth);
            const editorWidth = proposedWidth / totalWidth;
            this.setState({editorWidth: editorWidth * 100});
        }
    }

}
