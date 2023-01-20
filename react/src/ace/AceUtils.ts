import {Ace} from "ace-builds";

export function clearSessionExtras(session: Ace.EditSession) {
    session.setAnnotations(null);
    clearSessionMarkers(session);
}

export function clearSessionMarkers(session: Ace.EditSession) {
    const markers = session.getMarkers();
    if(markers) {
        const keys = Object.keys(markers);
        for(let key of keys)
            // @ts-ignore
            session.removeMarker(markers[key].id);
    }
}
