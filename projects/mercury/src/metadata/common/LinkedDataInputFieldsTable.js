import React from 'react';
import PropTypes from "prop-types";
import LinkedDataValuesList from "./LinkedDataValuesList";

const LinkedDataInputFieldsTable = (
    {property, values, validationErrors, onAdd, onChange, onDelete, subject, canEdit,
        labelId, addComponent, editComponent: EditComponent}
) => {
    // For input fields there is only a single input field
    const hasErrors = validationErrors && validationErrors.length > 0;

    const columnDefinition = {
        id: property.key,
        label: '',
        getValue: (entry, idx) => (
            <EditComponent
                property={property}
                entry={entry}
                onChange={value => onChange(value, idx)}
                aria-labelledby={labelId}
                error={hasErrors}
            />
        )
    };

    return (
        <LinkedDataValuesList
            onAdd={onAdd}
            onDelete={onDelete}
            subject={subject}
            columnDefinition={columnDefinition}
            property={property}
            values={values}
            showHeader={false}
            labelId={labelId}
            canEdit={canEdit}
            addComponent={addComponent}
        />
    );
};

LinkedDataInputFieldsTable.propTypes = {
    onChange: PropTypes.func,
    onDelete: PropTypes.func,
    addComponent: PropTypes.func,
    property: PropTypes.object,
    subject: PropTypes.string,
    labelId: PropTypes.string,
    canEdit: PropTypes.bool
};

LinkedDataInputFieldsTable.defaultProps = {
    onChange: () => {},
    onDelete: () => {},
    subject: {}
};

export default LinkedDataInputFieldsTable;
