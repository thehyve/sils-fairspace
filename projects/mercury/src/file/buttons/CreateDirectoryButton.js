import React, {useContext, useState} from 'react';
import useIsMounted from "react-is-mounted-hook";
import FileNameDialog from "./FileNameDialog";
import {useFormField} from "../../common/hooks/UseFormField";
import {getAllowedDirectoryTypes, isValidFileName} from "../fileUtils";
import ErrorDialog from "../../common/components/ErrorDialog";
import {getLabelForType} from "../../metadata/common/vocabularyUtils";
import VocabularyContext from "../../metadata/vocabulary/VocabularyContext";

const CreateDirectoryButton = ({children, disabled, onCreate, parentDirectoryType}) => {
    const [opened, setOpened] = useState(false);
    const {vocabulary, hierarchy} = useContext(VocabularyContext);
    const isMounted = useIsMounted();

    const allowedTypes = getAllowedDirectoryTypes(hierarchy, parentDirectoryType);
    const entityType = allowedTypes.length > 0 ? allowedTypes[0] : "";

    const nameControl = useFormField('', value => (
        !!value && isValidFileName(value)
    ));
    const openDialog = (e) => {
        if (e) e.stopPropagation();
        nameControl.setValue('');
        setOpened(true);
    };

    const closeDialog = (e) => {
        if (e) e.stopPropagation();
        setOpened(false);
    };

    const createDirectory = () => {
        onCreate(nameControl.value, entityType)
            .then(shouldClose => isMounted() && shouldClose && closeDialog());
    };

    const validateAndCreate = () => nameControl.valid && createDirectory();

    const renderFileNameDialog = () => {
        if (!entityType) {
            return ErrorDialog.showError(
                "Error creating directory",
                "No entity type available. Directory cannot be created on the current level.",
                null,
                closeDialog
            );
        }
        return (
            <FileNameDialog
                onClose={closeDialog}
                onSubmit={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    validateAndCreate();
                }}
                submitDisabled={Boolean(!nameControl.valid)}
                title={"Create new " + getLabelForType(vocabulary, entityType)}
                control={nameControl}
            />
        );
    };

    return (
        <>
            <span style={{display: 'inherit'}} onClick={e => !disabled && openDialog(e)}>
                {children}
            </span>
            {opened ? (renderFileNameDialog()) : null}
        </>
    );
};

export default CreateDirectoryButton;
