import React from 'react';
import {IconButton} from "@material-ui/core";
import FolderOpen from "@material-ui/icons/FolderOpen";
import {Link} from "react-router-dom";

import {COLLECTION_URI, DIRECTORY_URI, FILE_URI} from "../../constants";
import {hasValue} from "./metadataUtils";
import {getParentPath} from "../../file/fileUtils";

const DirectoryBrowserLink = ({type, filePath}) => {
    if (![COLLECTION_URI, DIRECTORY_URI, FILE_URI].includes(type)) {
        return '';
    }

    if (!hasValue(filePath)) {
        return '';
    }

    const resourceLocation = filePath[0].value;
    const linkLocation = type === FILE_URI ? getParentPath(resourceLocation) : resourceLocation;

    return (
        <Link to={`browser/${linkLocation}`}>
            <IconButton
                aria-label="Go to item"
                title="Go"
            >
                <FolderOpen />
            </IconButton>
        </Link>
    );
};

export default DirectoryBrowserLink;
