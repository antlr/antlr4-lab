import React, {Component} from "react";
import AntlrResult from "../antlr/AntlrResult";
import AntlrTree from "../antlr/AntlrTree";
import CSS from "csstype";

interface IItemProps { level: number, tree: AntlrTree | number, input: string, result: AntlrResult}
interface IItemState { expanded: boolean }

class ItemView extends Component<IItemProps, IItemState> {

    constructor(props: IItemProps) {
        super(props);
        this.state = { expanded: true };
    }

    render() {
        const tree = this.props.tree;
        if(tree == null)
            return null;
        if(typeof(tree) == "number")
            return this.renderToken();
        else if(tree.error)
            return this.renderError()
        else if(tree.kids)
            return this.renderRuleWithChildren();
        else
            return this.renderSimpleRule();
    }

    renderSimpleRule() {
        const tree = this.props.tree as AntlrTree;
        let liClassName = "tree-root tree-level-" + this.props.level;
        return <li className={liClassName}>
            <span >{this.props.result.rules[tree.ruleidx]}</span>
        </li>

    }

    renderRuleWithChildren() {
        const tree = this.props.tree as AntlrTree;
        const liClassName = "tree-root tree-level-" + this.props.level;
        const iconClassName = this.state.expanded ? "bx bxs-down-arrow" : "bx bxs-right-arrow";
        const kidsStyle: CSS.Properties = this.state.expanded ? { display: "block" } : { display: "none" };
        return <li className={liClassName} >
            <span onClick={e => this.toggleExpand()}>
                <i className={iconClassName} />
                {this.props.result.rules[tree.ruleidx]}
            </span>
            <ul style={kidsStyle} >
                { tree.kids.map((kid, idx) => <ItemView key={"item-" + idx} level={this.props.level + 1} tree={kid} input={this.props.input} result={this.props.result} />)}
            </ul>
        </li>
    }

    toggleExpand() {
        this.setState({expanded: !this.state.expanded});
    }

    renderToken() {
        const tree = this.props.tree as number;
        const token = this.props.result.tokens[tree];
        const text = token.type === -1 ? "<EOF>" : this.props.input.slice(token.start, token.stop + 1)
        const liClassName = "tree-token tree-level-" + this.props.level;
        return <li className={liClassName}>{text}</li>;
    }

    renderError() {
        const tree = this.props.tree as AntlrTree;
        const liClassName = "tree-token tree-error tree-level-" + this.props.level;
        return <li className={liClassName}>&lt;error:{tree.error}&gt;</li>
    }

}

interface IProps { input: string, result: AntlrResult}
interface IState{}

export default class HierarchyView extends Component<IProps, IState> {

    render() {
        if(this.props.result && this.props.result.tree) {
            return <div className="hierarchy">
                <ul id="treeUL">
                    <ItemView tree={this.props.result.tree} input={this.props.input} result={this.props.result} level={0} />
                </ul>
            </div>
        }
        return <div>HierarchyView</div>;
    }
}
