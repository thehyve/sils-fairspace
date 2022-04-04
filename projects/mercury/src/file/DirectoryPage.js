import React, {useContext, useEffect, useState} from 'react';
import Grid from '@material-ui/core/Grid';
import {withRouter} from "react-router-dom";
import queryString from "query-string";

import FormControlLabel from "@material-ui/core/FormControlLabel";
import {Divider, Switch, withStyles} from "@material-ui/core";
import Button from "@material-ui/core/Button";
import FileBrowser from "./FileBrowser";
import DirectoryInformationDrawer from './DirectoryInformationDrawer';
import {getHierarchyRoot, getValidPath, splitPathIntoArray} from "./fileUtils";
import * as consts from '../constants';
import BreadcrumbsContextProvider from "../common/contexts/BreadcrumbsContextProvider";
import {useMultipleSelection} from "./UseSelection";
import SearchBar from "../search/SearchBar";
import BreadCrumbs from "../common/components/BreadCrumbs";
import usePageTitleUpdater from "../common/hooks/UsePageTitleUpdater";
import styles from "./DirectoryPage.styles";
import {getMetadataViewsPath, RESOURCES_VIEW} from "../metadata/views/metadataViewUtils";
import UserContext from "../users/UserContext";
import MetadataViewContext from "../metadata/views/MetadataViewContext";
import type {User} from "../users/UsersAPI";
import {MetadataViewOptions} from "../metadata/views/MetadataViewAPI";
import type {Match} from "../types";
import {handleTextSearchRedirect} from "../search/searchUtils";
import {useFiles} from "./UseFiles";
import LoadingInlay from "../common/components/LoadingInlay";
import MessageDisplay from "../common/components/MessageDisplay";
import VocabularyContext from "../metadata/vocabulary/VocabularyContext";
import useAsync from "../common/hooks/UseAsync";
import {LocalFileAPI} from "./FileAPI";
import type {HierarchyLevel} from "../metadata/common/vocabularyUtils";

export type OpenedDirectory = {
    iri: string,
    path: string,
    directoryType: string,
    isDeleted: boolean,
}

type ContextualDirectoryPageProperties = {
    match: Match;
    history: History;
    location: Location;
    classes: any;
};

type DirectoryPageProperties = ContextualDirectoryPageProperties & {
    openedDirectory: OpenedDirectory;
    files: File[];
    hierarchy: HierarchyLevel[];
    fileActions: any;
    currentUser: User;
    views: MetadataViewOptions[];
    showDeleted: boolean;
    setShowDeleted: (boolean) => void;
    refreshFiles: () => void;
};

export const DirectoryPage = (props: DirectoryPageProperties) => {
    const {
        openedDirectory = {},
        showDeleted = false,
        setShowDeleted = () => {},
        views = [],
        files = [],
        hierarchy = [],
        fileActions, refreshFiles, currentUser, location, history, classes
    } = props;

    const selection = useMultipleSelection();

    // TODO: this code could be buggy, if the url had an invalid file name it will still be part of the selection.
    // I suggest that the selection state be part of a context (FilesContext ?)..
    //
    // Check whether a filename is specified in the url for selection
    // If so, select it on first render
    const preselectedFile = location.search ? decodeURIComponent(queryString.parse(location.search).selection) : undefined;

    const hierarchyRoot = getHierarchyRoot(hierarchy);

    const getLocationContext = () => {
        if (!openedDirectory.iri) {
            return "";
        }
        return encodeURI(openedDirectory.iri);
    };

    const getBrowserPathPrefix = () => consts.PATH_SEPARATOR + "browser" + consts.PATH_SEPARATOR;

    const getMetadataSearchRedirect = () => `${getMetadataViewsPath()}?${queryString.stringify({view: RESOURCES_VIEW, context: getLocationContext()})}`;

    const getSearchPlaceholder = () => {
        const openedFolderName = openedDirectory.path ? openedDirectory.path.substring(openedDirectory.path.lastIndexOf('/') + 1) : null;
        return openedFolderName ? `Search in ${openedFolderName}` : `Search in all ${hierarchyRoot.labelPlural || hierarchyRoot.label}`;
    };

    const handleTextSearch = (value) => handleTextSearchRedirect(history, value, getLocationContext());

    useEffect(() => {
        if (preselectedFile) {
            selection.select(preselectedFile);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [preselectedFile]);
    const pathSegments = splitPathIntoArray(openedDirectory.path);
    const breadcrumbSegments = pathSegments.map((segment, idx) => ({
        label: segment,
        href: getBrowserPathPrefix() + pathSegments.slice(0, idx + 1).map(encodeURIComponent).join(consts.PATH_SEPARATOR)
    }));

    usePageTitleUpdater(`${breadcrumbSegments.map(s => s.label).join(' / ')} /`);

    // Path for which metadata should be rendered
    const path = (selection.selected.length === 1) ? selection.selected[0] : openedDirectory.path;

    const showMetadataSearchButton: boolean = (
        currentUser && currentUser.canViewPublicMetadata && views && views.some(v => v.name === RESOURCES_VIEW)
        && !openedDirectory.isDeleted
    );

    const allowCsvUpload = openedDirectory && openedDirectory.path.length < path.length;

    return (
        <BreadcrumbsContextProvider
            label={hierarchyRoot.labelPlural || hierarchyRoot.label}
            href="/browser"
        >
            <div className={classes.breadcrumbs}>
                <BreadCrumbs additionalSegments={breadcrumbSegments} />
            </div>
            <Grid container justifyContent="space-between" spacing={1}>
                <Grid item className={classes.topBar}>
                    <Grid container>
                        <Grid item xs={6}>
                            <SearchBar
                                placeholder={getSearchPlaceholder()}
                                onSearchChange={handleTextSearch}
                            />
                        </Grid>
                        {showMetadataSearchButton && (
                            <Grid item container xs={4} justifyContent="flex-end">
                                <Grid item>
                                    <Button
                                        variant="text"
                                        color="primary"
                                        href={getMetadataSearchRedirect()}
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
                                        disabled={openedDirectory.isDeleted}
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
                        openedDirectory={openedDirectory}
                        selection={selection}
                        preselectedFile={preselectedFile}
                        showDeleted={showDeleted}
                        files={files}
                        refreshFiles={refreshFiles}
                        fileActions={fileActions}
                    />
                </Grid>
                <Grid item className={classes.sidePanel}>
                    <DirectoryInformationDrawer
                        path={path}
                        selected={selection.selected}
                        showDeleted={showDeleted}
                        atLeastSingleRootDirectoryExists={!!openedDirectory.iri || files.length > 0}
                        allowCsvUpload={allowCsvUpload}
                        refreshFiles={refreshFiles}
                    />
                </Grid>
            </Grid>
        </BreadcrumbsContextProvider>
    );
};

const ContextualDirectoryPage = (props: ContextualDirectoryPageProperties) => {
    const [showDeleted, setShowDeleted] = useState(false);
    const {currentUser} = useContext(UserContext);
    const {views} = useContext(MetadataViewContext);
    const {hierarchy} = useContext(VocabularyContext);
    const {params} = props.match;
    const openedPath = getValidPath(params.path);
    const {files, loading, error, refresh, fileActions} = useFiles(openedPath, showDeleted);
    const {data: currentDir = {}, error: currentDirError, loading: currentDirLoading} = useAsync(
        () => (LocalFileAPI.stat(openedPath, true)),
        [openedPath]
    );

    if (error || currentDirError) {
        return (<MessageDisplay message="An error occurred while loading files" />);
    }
    if (loading || currentDirLoading) {
        return <LoadingInlay />;
    }

    const openedDirectory: OpenedDirectory = {
        iri: currentDir.iri,
        path: openedPath,
        directoryType: currentDir.linkedEntityType,
        isDeleted: currentDir.isDeleted || false
    };

    return (
        <DirectoryPage
            openedDirectory={openedDirectory}
            files={files}
            hierarchy={hierarchy}
            refreshFiles={refresh}
            fileActions={fileActions}
            showDeleted={showDeleted}
            setShowDeleted={setShowDeleted}
            currentUser={currentUser}
            views={views}
            {...props}
        />
    );
};

export default withRouter(withStyles(styles)(ContextualDirectoryPage));
