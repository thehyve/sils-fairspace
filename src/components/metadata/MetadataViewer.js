import React from 'react';
import { JsonToTable } from "react-json-to-table";

/**
 * This compp
 */
class MetadataViewer extends React.Component {

    constructor(props) {
        super(props);
        this.props = props;
        this.idsMap = {};
        this.contentMap = {};
        this.metadata = props.metadata;
        this.vocab =  props.vocab;
    }

    addtoIdsMap(key, metadata) {
        if (Object.keys(this.idsMap).includes(key)){
            let itemList = [];
            if (typeof  this.idsMap[key] === "object") {
                itemList = itemList.concat(this.idsMap[key]);
            }
            else {
                itemList.push(this.idsMap[key]);
            }
            itemList.push(metadata[key]);
            this.idsMap[key] = itemList;
        }
        else {
            this.idsMap[key] = metadata[key];
        }
        delete metadata[key];
        return metadata;
    }

    getIds(metadata) {
        for (let key in metadata) {
            if (key.includes(":")) {
                metadata = this.addtoIdsMap(key, metadata);
            }
            else if (typeof metadata[key] === 'object' || typeof key === 'number') {
                this.getIds(metadata[key]);
            }
        }
    }

    getLabels() {
        for (let label in this.idsMap) {
            let found = false;
            for (const labelObject of this.vocab["@graph"]) {
                if (labelObject["@id"] === label) {
                    this.contentMap[labelObject["rdfs:label"]] = this.idsMap[label];
                    found = true;
                }
            }
            // TODO what if label is not found?
            if (!found) {
                this.contentMap[label] = this.idsMap[label];
            }
        }
    }

    componentWillMount() {
        this.getIds(this.metadata);
        this.getLabels();
    }

    render() {
        return (
            <div>
                <JsonToTable json={this.contentMap} />
            </div>
        )

    }

}

export default MetadataViewer
