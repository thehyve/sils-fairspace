import React, {useContext, useState} from 'react';
import {Badge, IconButton, withStyles} from "@material-ui/core";
import {BorderColor, CreateNewFolder, Delete, RestoreFromTrash} from '@material-ui/icons';
import ContentCopy from "mdi-material-ui/ContentCopy";
import ContentCut from "mdi-material-ui/ContentCut";
import ContentPaste from "mdi-material-ui/ContentPaste";
import ErrorDialog from "../common/components/ErrorDialog";

import {canEditHierarchyLevel, getAllowedDirectoryTypes, getParentPath, getStrippedPath, joinPaths} from "./fileUtils";
import {COPY, CUT} from '../constants';
import FileOperationsGroup from "./FileOperationsGroup";
import ClipboardContext from '../common/contexts/ClipboardContext';
import ConfirmationButton from "../common/components/ConfirmationButton";
import CreateDirectoryButton from "./buttons/CreateDirectoryButton";
import ProgressButton from "../common/components/ProgressButton";
import RenameButton from "./buttons/RenameButton";
import styles from "./FileOperations.styles";
import VocabularyContext from "../metadata/vocabulary/VocabularyContext";
import UserContext from "../users/UserContext";
import {isAdmin} from "../users/userUtils";

export const Operations = {
    PASTE: 'PASTE',
    MKDIR: 'MKDIR',
    RENAME: 'RENAME',
    DELETE: 'DELETE',
    UNDELETE: 'UNDELETE',
    REVERT: 'REVERT'
};
Object.freeze(Operations);

export const FileOperations = ({
    isWritingEnabled,
    currentUser = {},
    showDeleted,
    isExternalStorage = false,
    openedDirectory = {},
    allowedTypes = [],
    selectedPaths,
    clearSelection,
    fileActions = {},
    files,
    refreshFiles,
    clipboard
}) => {
    const [activeOperation, setActiveOperation] = useState();
    const busy = !!activeOperation;

    const noPathSelected = selectedPaths.length === 0;
    const selectedItems = files.filter(f => selectedPaths.includes(f.filename)) || [];
    const selectedItem = selectedItems && selectedItems.length === 1 ? selectedItems[0] : {};
    const moreThanOneItemSelected = selectedPaths.length > 1;
    const selectedDeletedItems = selectedItems.filter(f => f.dateDeleted);
    const isDeletedItemSelected = selectedDeletedItems.length > 0;
    const isDisabledForMoreThanOneSelection = selectedPaths.length === 0 || moreThanOneItemSelected;
    const isClipboardItemsOnOpenedPath = !clipboard.isEmpty() && clipboard.filenames.map(f => getParentPath(f)).includes(openedDirectory.path);
    const isLinkedEntityTypeValidForParent = !clipboard.isEmpty() && allowedTypes.includes(clipboard.linkedEntityType);
    const isPasteDisabled = !isWritingEnabled || clipboard.isEmpty() || (isClipboardItemsOnOpenedPath && clipboard.method === CUT) || !isLinkedEntityTypeValidForParent;
    const isAdminOnlyOperationEnabled = isAdmin(currentUser);

    const fileOperation = (operationCode, operationPromise) => {
        setActiveOperation(operationCode);
        return operationPromise
            .then(r => {
                setActiveOperation();
                refreshFiles();
                clearSelection();
                return r;
            })
            .catch(e => {
                setActiveOperation();
                return Promise.reject(e);
            });
    };

    const handleCut = e => {
        if (e) e.stopPropagation();
        clipboard.cut(selectedPaths, selectedItem.linkedEntityType);
    };

    const handleCopy = e => {
        if (e) e.stopPropagation();
        clipboard.copy(selectedPaths, selectedItem.linkedEntityType);
    };

    const handlePaste = e => {
        if (e) e.stopPropagation();

        let operation;

        if (clipboard.method === CUT) {
            operation = fileActions.movePaths(clipboard.filenames);
        }
        if (clipboard.method === COPY) {
            operation = fileActions.copyPaths(clipboard.filenames);
        }

        if (operation) {
            return fileOperation(Operations.PASTE, operation)
                .then(clipboard.clear)
                .catch(err => ErrorDialog.showError("An error occurred while pasting your contents", err));
        }

        return Promise.resolve();
    };

    const handleCreateDirectory = (name, entityType, linkedEntityIri) => (
        fileOperation(Operations.MKDIR, fileActions.createDirectory(joinPaths(openedDirectory.path, name), entityType, linkedEntityIri))
            .catch((err) => {
                if (err.message.includes('status code 409')) {
                    ErrorDialog.showError(
                        'Directory name must be unique',
                        'Directory with this name already exists and was marked as deleted.\n'
                        + 'Please delete the existing directory permanently or choose a unique name.'
                    );
                    return true;
                }
                ErrorDialog.showError(
                    "An error occurred while creating directory",
                    err,
                    () => handleCreateDirectory(name, entityType)
                );
                return true;
            })
    );

    const handlePathRename = (path, newName) => fileOperation(Operations.RENAME, fileActions.renameFile(path.basename, newName))
        .catch((err) => {
            ErrorDialog.showError("An error occurred while renaming file or directory", err, () => handlePathRename(path, newName));
            return false;
        });

    const addBadgeIfNotEmpty = (badgeContent, children) => {
        if (badgeContent) {
            return (
                <Badge badgeContent={badgeContent} color="primary">
                    {children}
                </Badge>
            );
        }
        return children;
    };

    const handleDelete = () => fileOperation(Operations.DELETE, fileActions.deleteMultiple(selectedPaths))
        .catch((err) => {
            ErrorDialog.showError("An error occurred while deleting file or directory", err, () => handleDelete());
        });

    const getDeletionConfirmationMessage = () => {
        if (isDeletedItemSelected) {
            if (selectedDeletedItems.length === 1 && selectedItems.length === 1) {
                return 'Selected item is already marked as deleted. '
                    + 'By clicking "Remove" you agree to remove the item permanently!';
            }
            return `${selectedDeletedItems.length} of ${selectedPaths.length} selected items are already marked as deleted. 
            By clicking "Remove" you agree to remove these items permanently!`;
        }
        return `Are you sure you want to remove ${selectedPaths.length} item(s)? `;
    };

    const handleUndelete = () => fileOperation(Operations.UNDELETE, fileActions.undeleteMultiple(selectedPaths))
        .catch((err) => {
            ErrorDialog.showError("An error occurred while undeleting file or directory", err, () => handleUndelete());
        });
    const isRoot = !!openedDirectory.path && getStrippedPath(openedDirectory.path) === "";

    return (
        <>
            <FileOperationsGroup>
                {isWritingEnabled && (
                    <>
                        <ProgressButton active={activeOperation === Operations.MKDIR}>
                            <CreateDirectoryButton
                                onCreate={(name, entityType, linkedEntityIri) => handleCreateDirectory(name, entityType, linkedEntityIri)}
                                disabled={busy}
                                allowedTypes={allowedTypes}
                                locationIsRoot={isRoot}
                            >
                                <IconButton
                                    aria-label="Create directory"
                                    title="Create directory"
                                    disabled={busy}
                                >
                                    <CreateNewFolder />
                                </IconButton>
                            </CreateDirectoryButton>
                        </ProgressButton>
                    </>
                )}
            </FileOperationsGroup>
            <FileOperationsGroup>
                {isWritingEnabled && (
                    <>
                        <ProgressButton active={activeOperation === Operations.RENAME}>
                            <RenameButton
                                currentName={selectedItem.basename}
                                onRename={newName => handlePathRename(selectedItem, newName)}
                                disabled={isDisabledForMoreThanOneSelection || isDeletedItemSelected || busy}
                            >
                                <IconButton
                                    title={`Rename ${selectedItem.basename}`}
                                    aria-label={`Rename ${selectedItem.basename}`}
                                    disabled={isDisabledForMoreThanOneSelection || isDeletedItemSelected || busy}
                                >
                                    <BorderColor />
                                </IconButton>
                            </RenameButton>
                        </ProgressButton>
                        <ProgressButton active={activeOperation === Operations.DELETE}>
                            <ConfirmationButton
                                message={getDeletionConfirmationMessage()}
                                agreeButtonText="Remove"
                                dangerous
                                onClick={handleDelete}
                                disabled={noPathSelected || busy || (isDeletedItemSelected && !isAdminOnlyOperationEnabled)}
                            >
                                <IconButton
                                    title="Delete"
                                    aria-label="Delete"
                                    disabled={noPathSelected || busy || (isDeletedItemSelected && !isAdminOnlyOperationEnabled)}
                                >
                                    <Delete />
                                </IconButton>
                            </ConfirmationButton>
                        </ProgressButton>
                        {showDeleted && (
                            <ProgressButton active={activeOperation === Operations.UNDELETE}>
                                <ConfirmationButton
                                    message={`Are you sure you want to undelete ${selectedPaths.length} item(s)?`}
                                    agreeButtonText="Undelete"
                                    dangerous
                                    onClick={handleUndelete}
                                    disabled={noPathSelected || (selectedDeletedItems.length !== selectedItems.length) || busy}
                                >
                                    <IconButton
                                        title="Undelete"
                                        aria-label="Undelete"
                                        disabled={noPathSelected || (selectedDeletedItems.length !== selectedItems.length) || busy}
                                    >
                                        <RestoreFromTrash />
                                    </IconButton>
                                </ConfirmationButton>
                            </ProgressButton>
                        )}
                    </>
                )}
            </FileOperationsGroup>
            <FileOperationsGroup>
                {!isExternalStorage && !isRoot && (
                    <IconButton
                        aria-label="Copy"
                        title="Copy"
                        onClick={e => handleCopy(e)}
                        disabled={noPathSelected || isDeletedItemSelected || busy}
                    >
                        <ContentCopy />
                    </IconButton>
                )}
                {isWritingEnabled && !isRoot && (
                    <>
                        <IconButton
                            aria-label="Cut"
                            title="Cut"
                            onClick={e => handleCut(e)}
                            disabled={noPathSelected || isDeletedItemSelected || busy}
                        >
                            <ContentCut />
                        </IconButton>
                        <ProgressButton active={activeOperation === Operations.PASTE}>
                            <IconButton
                                aria-label="Paste"
                                title="Paste"
                                onClick={e => handlePaste(e)}
                                disabled={isPasteDisabled || isDeletedItemSelected || busy}
                            >
                                {addBadgeIfNotEmpty(clipboard.length(), <ContentPaste />)}
                            </IconButton>
                        </ProgressButton>
                    </>
                )}
            </FileOperationsGroup>
        </>
    );
};

const ContextualFileOperations = props => {
    const clipboard = useContext(ClipboardContext);
    const {hierarchy} = useContext(VocabularyContext);
    const {currentUser} = useContext(UserContext);

    const allowedTypes = getAllowedDirectoryTypes(hierarchy, props.openedDirectory.directoryType);

    if (allowedTypes.length > 0 && !canEditHierarchyLevel(currentUser, hierarchy, allowedTypes[0])) {
        return <></>;
    }

    return <FileOperations clipboard={clipboard} allowedTypes={allowedTypes} currentUser={currentUser} {...props} />;
};

export default withStyles(styles)(ContextualFileOperations);
