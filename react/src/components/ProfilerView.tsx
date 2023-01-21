import React, {Component} from "react";
import {Table} from "react-bootstrap";
import ProfilerData from "../antlr/ProfilerData";

interface IProps { profilerData: ProfilerData }
interface IState {}

export default class ProfilerView extends Component<IProps, IState> {

    render() {
        return <>
            <div className="chunk-header">Parser profile info</div>
            <Table className="profiler-table" striped bordered>
                <thead>
                    <tr>
                        { this.props.profilerData.colnames.map(name => <th key={name}>{name}</th>) }
                    </tr>
                </thead>
                <tbody>
                { this.props.profilerData.data.map( (row, idx) => {
                        return <tr key={"row-" + idx}>
                            { row.map( (cell, idx2) => <td key={"cell-" + idx2}>{cell}</td>) }
                        </tr>;
                    })
                }
                </tbody>
            </Table>
            </>;
    }
}
