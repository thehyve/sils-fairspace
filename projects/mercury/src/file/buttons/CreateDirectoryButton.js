/* eslint-disable no-unused-vars */
import React, {useContext, useState} from 'react';
import useIsMounted from "react-is-mounted-hook";
import Switch from '@material-ui/core/Switch';
import FormGroup from '@material-ui/core/FormGroup';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import {ListItem} from "@material-ui/core";
import FileNameDialog from "./FileNameDialog";
import {useFormField} from "../../common/hooks/UseFormField";
import {getAllowedDirectoryTypes, isValidFileName} from "../fileUtils";
import ErrorDialog from "../../common/components/ErrorDialog";
import {getLabelForType} from "../../metadata/common/vocabularyUtils";
import VocabularyContext from "../../metadata/vocabulary/VocabularyContext";
import LinkedDataProperty from "../../metadata/common/LinkedDataProperty";
import LinkedDataDropdown from "../../metadata/common/LinkedDataDropdown";
import UseLinkedData from "../../metadata/common/UseLinkedData";

const CreateDirectoryButton = ({children, disabled, onCreate, parentDirectoryType}) => {
    const [opened, setOpened] = useState(false);
    const [useExistingEntity, setUseExistingEntity] = React.useState(true);

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

    const handleCreateNewEntityChoice = (event) => {
        setUseExistingEntity(event.target.checked);
    };

    const onLinkedEntitySelected = (value) => {
        if (value) {
            nameControl.setValue(value.label);
        }
    };

    const linkedDataSelector = () => {
        if (!entityType) {
            return [];
        }

        const properties = {property: {className: entityType},
            clearTextOnSelection: false,
            onChange: onLinkedEntitySelected};
        return LinkedDataDropdown(properties);
    };

    const entitySelector = (
        <FormGroup readOnly>
            <FormControlLabel
                label="Use existing"
                checked={useExistingEntity}
                control={
                    <Switch onChange={handleCreateNewEntityChoice} />
                }
            />
            {useExistingEntity && linkedDataSelector()}
        </FormGroup>
    );

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
                entitySelector={entitySelector}
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
