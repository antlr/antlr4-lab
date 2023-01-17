import {Component} from "react";
import {Container} from "react-bootstrap";
import Welcome from "./Welcome";

export default class App extends Component {

    constructor() {
        super();
        this.state = { showWelcome: true };
    }

    render() {
        return <><Container fluid>

                </Container>
            { this.renderWelcome() }
            </>;
    }

    renderWelcome() {
        if(this.state.showWelcome)
            return <Welcome onClose={()=>this.setState({showWelcome: false})} />;
        else
            return null;
    }

}
