import React, {useEffect, useState} from 'react';
import {withRouter} from "react-router-dom";
import {withStyles} from "@material-ui/core";
import FileList from "./FileList";
import FileOperations from "./FileOperations";
import {encodePath, isDirectory} from "./fileUtils";
import {showCannotOverwriteDeletedError} from "./UploadsContext";
import type {OpenedDirectory} from "./DirectoryPage";
import type {HierarchyLevel} from "../metadata/common/vocabularyUtils";

const styles = (theme) => ({
    container: {
        height: "100%"
    },
    dropzone: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        outline: "none",
        transitionBorder: ".24s",
        easeInOut: true
    },
    activeStyle: {
        borderColor: theme.palette.info.main,
        borderWidth: 2,
        borderRadius: 2,
        borderStyle: "dashed",
        opacity: 0.4
    },
    acceptStyle: {
        borderColor: theme.palette.success.main
    },
    rejectStyle: {
        borderColor: theme.palette.error.main
    }
});

type ContextualFileBrowserProperties = {
    history: History;
    openedDirectory: OpenedDirectory,
    isOpenedPathDeleted: boolean,
    showDeleted: boolean,
    loading: boolean,
    error: Error,
    selection: any,
    preselectedFile: File,
    classes: any
}

type FileBrowserProperties = ContextualFileBrowserProperties & {
    files: File[];
    refreshFiles: () => void;
    fileActions: any;
};

export const FileBrowser = (props: FileBrowserProperties) => {
    const {
        openedDirectory = {},
        files = [],
        showDeleted = false,
        refreshFiles = () => {},
        fileActions = {},
        selection = {},
        preselectedFile = {},
        classes = {},
        history
    } = props;
    const isWritingEnabled = !openedDirectory.isDeleted;
    const [showCannotOverwriteWarning, setShowCannotOverwriteWarning] = useState(false);
    const [overwriteFileCandidateNames] = useState([]);
    const [overwriteFolderCandidateNames] = useState([]);

    // Deselect all files on history changes
    /* eslint-disable react-hooks/exhaustive-deps */
    useEffect(() => history.listen(() => selection.deselectAll()),
        [history]);

    useEffect(() => {
        if (showCannotOverwriteWarning) {
            setShowCannotOverwriteWarning(false);
            showCannotOverwriteDeletedError([...overwriteFileCandidateNames, ...overwriteFolderCandidateNames].length);
        }
    }, [overwriteFileCandidateNames, overwriteFolderCandidateNames, showCannotOverwriteWarning]);

    // A highlighting of a path means only this path would be selected/checked
    const handlePathHighlight = path => {
        const wasSelected = selection.isSelected(path.filename);
        selection.deselectAll();
        if (!wasSelected) {
            selection.select(path.filename);
        }
    };

    const handlePathDoubleClick = (path: File, linkedEntity: HierarchyLevel) => {
        if (isDirectory(path, linkedEntity)) {
            /* TODO Remove additional encoding (encodeURI) after upgrading to history to version>=4.10
             *      This version contains this fix: https://github.com/ReactTraining/history/pull/656
             *      It requires react-router-dom version>=6 to be released.
             */
            history.push(`/browser${encodeURI(encodePath(path.filename))}`);
        }
    };

    const renderFileOperations = () => (
        <div style={{marginTop: 8}}>
            <FileOperations
                selectedPaths={selection.selected}
                files={files}
                openedDirectory={openedDirectory}
                isWritingEnabled={isWritingEnabled}
                showDeleted={showDeleted}
                fileActions={fileActions}
                clearSelection={selection.deselectAll}
                refreshFiles={refreshFiles}
            />
        </div>
    );

    return (
        <div data-testid="files-view" className={classes.container}>
            <FileList
                files={files.map(item => ({...item, selected: selection.isSelected(item.filename)}))}
                onPathCheckboxClick={path => selection.toggle(path.filename)}
                onPathHighlight={handlePathHighlight}
                onPathDoubleClick={handlePathDoubleClick}
                onAllSelection={shouldSelectAll => (shouldSelectAll ? selection.selectAll(files.map(file => file.filename)) : selection.deselectAll())}
                showDeleted={showDeleted}
                preselectedFile={preselectedFile}
            />
            {renderFileOperations()}
        </div>
    );
};

export default withRouter(withStyles(styles)(FileBrowser));
