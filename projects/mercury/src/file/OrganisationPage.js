import React, {useContext, useEffect, useState} from 'react';
import Grid from '@material-ui/core/Grid';
import {withRouter} from "react-router-dom";
import queryString from "query-string";

import FormControlLabel from "@material-ui/core/FormControlLabel";
import {Divider, Switch, withStyles} from "@material-ui/core";
import Button from "@material-ui/core/Button";
import FileBrowser from "./FileBrowser";
import {getPathInfoFromParams, splitPathIntoArray} from "./fileUtils";
import * as consts from '../constants';
import OrganisationBreadcrumbsContextProvider from "../collections/OrganisationBreadcrumbsContextProvider";
import {useMultipleSelection} from "./UseSelection";
import LoadingOverlay from "../common/components/LoadingOverlay";
import SearchBar from "../search/SearchBar";
import BreadCrumbs from "../common/components/BreadCrumbs";
import usePageTitleUpdater from "../common/hooks/UsePageTitleUpdater";
import styles from "./FilesPage.styles";
import {getMetadataViewsPath, RESOURCES_VIEW} from "../metadata/views/metadataViewUtils";
import UserContext from "../users/UserContext";
import MetadataViewContext from "../metadata/views/MetadataViewContext";
import type {User} from "../users/UsersAPI";
import {MetadataViewOptions} from "../metadata/views/MetadataViewAPI";
import type {Match} from "../types";
import {handleTextSearchRedirect} from "../search/searchUtils";

type ContextualOrganisationPageProperties = {
    match: Match;
    history: History;
    location: Location;
    classes: any;
};

type ParentAwareOrganisationPageProperties = ContextualOrganisationPageProperties & {
    currentUser: User;
    openedPath: string;
    views: MetadataViewOptions[];
}

type OrganisationPageProperties = ParentAwareOrganisationPageProperties & {
    isOpenedPathDeleted: boolean;
};

export const OrganisationPage = (props: OrganisationPageProperties) => {
    const {
        loading = false,
        isOpenedPathDeleted = false,
        openedPath = "",
        views = [],
        currentUser, error, location, history, collection, classes
    } = props;

    const selection = useMultipleSelection();
    const [busy] = useState(false);
    const [showDeleted, setShowDeleted] = useState(false);

    // Check whether a filename is specified in the url for selection
    // If so, select it on first render
    const preselectedFile = location.search ? decodeURIComponent(queryString.parse(location.search).selection) : undefined;

    const getLocationContext = () => encodeURI(openedPath);

    const getMetadataSearchRedirect = () => `${getMetadataViewsPath()}?${queryString.stringify({view: RESOURCES_VIEW, context: getLocationContext()})}`;

    const handleTextSearch = (value) => handleTextSearchRedirect(history, value, getLocationContext());

    useEffect(() => {
        if (preselectedFile) {
            selection.select(preselectedFile);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [preselectedFile]);

    const pathSegments = splitPathIntoArray(openedPath);
    const breadcrumbSegments = pathSegments.map((segment, idx) => ({
        label: segment,
        href: consts.PATH_SEPARATOR + consts.DEPARTMENTS_PATH + consts.PATH_SEPARATOR
            + pathSegments.slice(0, idx + 1).map(encodeURIComponent).join(consts.PATH_SEPARATOR)
    }));

    usePageTitleUpdater(`${breadcrumbSegments.map(s => s.label).join(' / ')} /`);

    const showMetadataSearchButton: boolean = (
        currentUser && currentUser.canViewPublicMetadata && views && views.some(v => v.name === RESOURCES_VIEW)
        && !isOpenedPathDeleted
    );

    return (
        <OrganisationBreadcrumbsContextProvider>
            <div className={classes.breadcrumbs}>
                <BreadCrumbs additionalSegments={breadcrumbSegments} />
            </div>
            <Grid container justifyContent="space-between" spacing={1}>
                <Grid item className={classes.topBar}>
                    <Grid container>
                        <Grid item xs={6}>
                            <SearchBar
                                placeholder={`Search in ${openedPath.substring(openedPath.lastIndexOf('/') + 1)}`}
                                onSearchChange={handleTextSearch}
                            />
                        </Grid>
                        {showMetadataSearchButton && (
                            <Grid item container xs={4} justifyContent="flex-end">
                                <Grid item>
                                    <Button
                                        variant="text"
                                        color="primary"
                                        href={getMetadataSearchRedirect(RESOURCES_VIEW)}
                                    >
                                        Metadata search
                                    </Button>
                                </Grid>
                                <Grid item><Divider orientation="vertical" /></Grid>
                            </Grid>
                        )}
                        <Grid item xs={2} className={classes.topBarSwitch}>
                            <FormControlLabel
                                control={(
                                    <Switch
                                        color="primary"
                                        checked={showDeleted}
                                        onChange={() => setShowDeleted(!showDeleted)}
                                        disabled={isOpenedPathDeleted}
                                    />
                                )}
                                label="Show deleted"
                            />
                        </Grid>
                    </Grid>
                </Grid>
            </Grid>
            <Grid container spacing={1}>
                <Grid item className={classes.centralPanel}>
                    <FileBrowser
                        data-testid="file-browser"
                        openedCollection={collection}
                        openedPath={openedPath}
                        isOpenedPathDeleted={isOpenedPathDeleted}
                        loading={loading}
                        error={error}
                        selection={selection}
                        preselectedFile={preselectedFile}
                        showDeleted={showDeleted}
                    />
                </Grid>
            </Grid>
            <LoadingOverlay loading={busy} />
        </OrganisationBreadcrumbsContextProvider>
    );
};

const ContextualOrganisationPage = (props: ContextualOrganisationPageProperties) => {
    const {currentUser} = useContext(UserContext);
    const {views} = useContext(MetadataViewContext);
    const {params} = props.match;
    const {openedPath} = getPathInfoFromParams(params);

    return (
        <OrganisationPage
            openedPath={openedPath}
            currentUser={currentUser}
            views={views}
            {...props}
        />
    );
};

export default withRouter(withStyles(styles)(ContextualOrganisationPage));
