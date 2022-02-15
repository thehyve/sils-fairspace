import React, {useContext, useState} from 'react';
import useIsMounted from "react-is-mounted-hook";
import FileNameDialog from "./FileNameDialog";
import {useFormField} from "../../common/hooks/UseFormField";
import {isValidFileName} from "../fileUtils";
import VocabularyContext from "../../metadata/vocabulary/VocabularyContext";

const CreateDirectoryButton = ({children, disabled, onCreate, parentEntityType}) => {
    const [opened, setOpened] = useState(false);
    const isMounted = useIsMounted();

    const {hierarchy} = useContext(VocabularyContext);

    let allowedTypes = "";
    if (!hierarchy) {
        throw new Error("No valid hierarchy structure was found.");
    } else if (!parentEntityType) {
        allowedTypes = hierarchy.filter(node => node.isRoot)[0].children;
    } else {
        allowedTypes = hierarchy.filter(node => node.nodeType === parentEntityType)[0].children;
    }

    const entityType = allowedTypes[0];

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

    return (
        <>
            <span style={{display: 'inherit'}} onClick={e => !disabled && openDialog(e)}>
                {children}
            </span>
            {opened ? (
                <FileNameDialog
                    onClose={closeDialog}
                    onSubmit={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        validateAndCreate();
                    }}
                    submitDisabled={Boolean(!nameControl.valid)}
                    title={"Create new " + entityType}
                    control={nameControl}
                />
            ) : null}
        </>
    );
};

export default CreateDirectoryButton;
