import React, {useContext, useState} from 'react';
import useIsMounted from "react-is-mounted-hook";
import Switch from '@material-ui/core/Switch';
import FormGroup from '@material-ui/core/FormGroup';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import FileNameDialog from "./FileNameDialog";
import {useFormField} from "../../common/hooks/UseFormField";
import {getAllowedDirectoryTypes, isValidFileName} from "../fileUtils";
import ErrorDialog from "../../common/components/ErrorDialog";
import {getLabelForType} from "../../metadata/common/vocabularyUtils";
import VocabularyContext from "../../metadata/vocabulary/VocabularyContext";
import LinkedDataDropdown from "../../metadata/common/LinkedDataDropdown";

const CreateDirectoryButton = ({children, disabled, onCreate, parentDirectoryType}) => {
    const [opened, setOpened] = useState(false);
    const {vocabulary, hierarchy} = useContext(VocabularyContext);
    const isMounted = useIsMounted();
    const allowedTypes = getAllowedDirectoryTypes(hierarchy, parentDirectoryType);
    const entityType = allowedTypes.length > 0 ? allowedTypes[0] : "";

    const [linkedEntityIri, setLinkedEntityIri] = React.useState("");
    const [useExisting, setUseExisting] = React.useState(false);

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
        onCreate(nameControl.value, entityType, linkedEntityIri)
            .then(shouldClose => isMounted() && shouldClose && closeDialog());
    };

    const validateAndCreate = () => nameControl.valid && createDirectory();

    const handleCreateNewEntityChoice = (event) => {
        setUseExisting(event.target.checked);
        if (!event.target.checked) {
            setLinkedEntityIri("");
            nameControl.setValue("");
        }
    };

    const onLinkedEntitySelected = (value) => {
        if (value) {
            nameControl.setValue(value.label);
            setLinkedEntityIri(value.id);
        } else {
            setLinkedEntityIri("");
            nameControl.setValue("");
        }
    };

    const linkedDataSelector = () => {
        if (!entityType) {
            return [];
        }

        const p = {property: {className: entityType},
            clearTextOnSelection: false,
            onChange: onLinkedEntitySelected};
        return LinkedDataDropdown(p);
    };

    const entitySelector = (
        <FormGroup readOnly>
            <FormControlLabel
                label="Use existing"
                checked={useExisting}
                control={
                    <Switch onChange={handleCreateNewEntityChoice} />
                }
            />
            {useExisting && linkedDataSelector()}
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
                readonly={useExisting}
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
