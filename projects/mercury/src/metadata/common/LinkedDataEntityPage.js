import React, {useContext} from 'react';
import {Paper} from "@material-ui/core";
import useNamespacedIri from "../../common/hooks/UseNamespacedIri";
import useLinkedData from './UseLinkedData';
import LinkedDataEntityFormContainer from "./LinkedDataEntityFormContainer";
import LinkedDataEntityHeader from "./LinkedDataEntityHeader";
import BreadCrumbs from "../../common/components/BreadCrumbs";
import usePageTitleUpdater from "../../common/hooks/UsePageTitleUpdater";
import UserContext from "../../users/UserContext";
import {canEditMetadataType} from "../../users/userUtils";

export default ({title, subject}) => {
    const iri = useNamespacedIri(subject);
    usePageTitleUpdater(`${iri} - ${title}`);

    const {properties, values, linkedDataLoading, linkedDataError, typeInfo, updateLinkedData} = useLinkedData(subject);
    const {currentUser} = useContext(UserContext);
    const hasEditRight = canEditMetadataType(currentUser, typeInfo.isAdminEditOnly);

    return (
        <>
            <BreadCrumbs />
            <Paper style={{maxWidth: 800, padding: 20}}>
                <LinkedDataEntityHeader
                    subject={subject}
                    typeInfo={typeInfo}
                    values={values}
                    linkedDataLoading={linkedDataLoading}
                    linkedDataError={linkedDataError}
                    updateLinkedData={updateLinkedData}
                    enableDelete={false}
                />
                <LinkedDataEntityFormContainer
                    subject={subject}
                    typeInfo={typeInfo}
                    fullpage
                    properties={properties}
                    values={values}
                    linkedDataLoading={linkedDataLoading}
                    linkedDataError={linkedDataError}
                    updateLinkedData={updateLinkedData}
                    showEditButtons
                    hasEditRight={hasEditRight}
                />
            </Paper>
        </>
    );
};
