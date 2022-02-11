import React, {useContext, useEffect, useState} from 'react';
import Grid from '@material-ui/core/Grid';
import {withRouter} from "react-router-dom";
import queryString from "query-string";

import FormControlLabel from "@material-ui/core/FormControlLabel";
import {Divider, Switch, withStyles} from "@material-ui/core";
import Button from "@material-ui/core/Button";
import FileBrowser from "./FileBrowser";
import CollectionInformationDrawer from '../collections/CollectionInformationDrawer';
import {getPathInfoFromParams, splitPathIntoArray} from "./fileUtils";
import * as consts from '../constants';
import BreadcrumbsContextProvider from "../common/contexts/BreadcrumbsContextProvider";
import {useMultipleSelection} from "./UseSelection";
import LoadingOverlay from "../common/components/LoadingOverlay";
import SearchBar from "../search/SearchBar";
import BreadCrumbs from "../common/components/BreadCrumbs";
import usePageTitleUpdater from "../common/hooks/UsePageTitleUpdater";
import styles from "./FilesPage.styles";
import useAsync from "../common/hooks/UseAsync";
import {LocalFileAPI} from "./FileAPI";
import {getMetadataViewsPath, RESOURCES_VIEW} from "../metadata/views/metadataViewUtils";
import UserContext from "../users/UserContext";
import MetadataViewContext from "../metadata/views/MetadataViewContext";
import type {Collection} from "../collections/CollectionAPI";
import type {User} from "../users/UsersAPI";
import {MetadataViewOptions} from "../metadata/views/MetadataViewAPI";
import type {Match} from "../types";
import {handleTextSearchRedirect} from "../search/searchUtils";
import {useFiles} from "./UseFiles";
import LoadingInlay from "../common/components/LoadingInlay";
import MessageDisplay from "../common/components/MessageDisplay";

type ContextualDirectoryPageProperties = {
    match: Match;
    history: History;
    location: Location;
    classes: any;
};

type ParentAwareDirectoryPageProperties = ContextualDirectoryPageProperties & {
    directory: Collection;
    currentUser: User;
    openedPath: string;
    views: MetadataViewOptions[];
    loading: boolean;
    error: Error;
    showDeleted: boolean;
    setShowDeleted: (boolean) => void;
}

type DirectoryPageProperties = ParentAwareDirectoryPageProperties & {
    isOpenedPathDeleted: boolean;
};

export const DirectoryPage = (props: DirectoryPageProperties) => {
    const {
        loading = false,
        isOpenedPathDeleted = false,
        showDeleted = false,
        setShowDeleted = () => {},
        openedPath = "",
        views = [],
        currentUser, error, location, history, directory, classes
    } = props;

    const selection = useMultipleSelection();
    const [busy, setBusy] = useState(false);

    // TODO: this code could be buggy, if the url had an invalid file name it will still be part of the selection.
    // I suggest that the selection state be part of a context (FilesContext ?)..
    //
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
        href: consts.PATH_SEPARATOR + consts.ROOT_PATH + consts.PATH_SEPARATOR
            + pathSegments.slice(0, idx + 1).map(encodeURIComponent).join(consts.PATH_SEPARATOR)
    }));

    usePageTitleUpdater(`${breadcrumbSegments.map(s => s.label).join(' / ')} /`);

    // Path for which metadata should be rendered
    const path = (selection.selected.length === 1) ? selection.selected[0] : openedPath;

    const showMetadataSearchButton: boolean = (
        currentUser && currentUser.canViewPublicMetadata && views && views.some(v => v.name === RESOURCES_VIEW)
        && !isOpenedPathDeleted
    );

    return (
        <BreadcrumbsContextProvider
            label="Departments"
            href="/departments"
        >
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
                        openedCollection={directory}
                        openedPath={openedPath}
                        isOpenedPathDeleted={isOpenedPathDeleted}
                        loading={loading}
                        error={error}
                        selection={selection}
                        preselectedFile={preselectedFile}
                        showDeleted={showDeleted}
                    />
                </Grid>
                <Grid item className={classes.sidePanel}>
                    <CollectionInformationDrawer
                        setBusy={setBusy}
                        path={path}
                        showDeleted={showDeleted}
                    />
                </Grid>
            </Grid>
            <LoadingOverlay loading={busy} />
        </BreadcrumbsContextProvider>
    );
};

const ParentAwareDirectoryPage = (props: ParentAwareDirectoryPageProperties) => {
    const {data, error, loading, refresh} = useAsync(
        () => (LocalFileAPI.stat(props.openedPath, true)),
        [props.openedPath]
    );

    useEffect(() => {refresh();}, [props.directory.dateDeleted, refresh]);

    const isParentFolderDeleted = data && data.props && !!data.props.dateDeleted;
    const isOpenedPathDeleted = !!props.directory.dateDeleted || isParentFolderDeleted;

    return (
        <DirectoryPage
            isOpenedPathDeleted={isOpenedPathDeleted}
            loading={loading && props.loading}
            error={error && props.error}
            {...props}
        />
    );
};

const ContextualDirectoryPage = (props: ContextualDirectoryPageProperties) => {
    const [showDeleted, setShowDeleted] = useState(false);
    const {currentUser} = useContext(UserContext);
    const {views} = useContext(MetadataViewContext);
    const {params} = props.match;
    const {collectionName, openedPath} = getPathInfoFromParams(params);
    const {files, loading, error} = useFiles(openedPath, showDeleted);

    if (error) {
        return (<MessageDisplay message="An error occurred while loading files" />);
    }
    if (loading) {
        return <LoadingInlay />;
    }
    const rootDirectory = files.find(f => ('/' + f.filename.toLowerCase()) === (openedPath.toLowerCase()));
    const rootFolders = files.filter(f => f !== rootDirectory);
    const rootFolder = rootFolders.find(c => c.name === collectionName) || {};

    return showDeleted ? (
        <ParentAwareDirectoryPage
            directory={rootFolder}
            openedPath={openedPath}
            loading={loading}
            error={error}
            showDeleted={showDeleted}
            setShowDeleted={setShowDeleted}
            currentUser={currentUser}
            views={views}
            {...props}
        />
    ) : (
        <DirectoryPage
            directory={rootFolder}
            openedPath={openedPath}
            loading={loading}
            error={error}
            showDeleted={showDeleted}
            setShowDeleted={setShowDeleted}
            currentUser={currentUser}
            views={views}
            {...props}
        />
    );
};

export default withRouter(withStyles(styles)(ContextualDirectoryPage));
