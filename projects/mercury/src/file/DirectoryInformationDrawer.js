// @flow
import React, {useContext, useState} from 'react';
import {Card, CardContent, CardHeader, Collapse, DialogContentText, IconButton, Typography} from '@material-ui/core';
import {withRouter} from 'react-router-dom';

import {CloudUpload, ExpandMore, FolderOpenOutlined, InsertDriveFileOutlined} from '@material-ui/icons';
import {makeStyles} from '@material-ui/core/styles';
import {useDropzone} from "react-dropzone";
import CircularProgress from "@material-ui/core/CircularProgress";
import Tooltip from "@material-ui/core/Tooltip";
import Link from "@material-ui/core/Link";
import table from "text-table";
import {SnackbarProvider, useSnackbar} from "notistack";
import {flatMap} from 'lodash';
import {LinkedDataEntityFormWithLinkedData} from '../metadata/common/LinkedDataEntityFormContainer';
import useAsync from '../common/hooks/UseAsync';
import {LocalFileAPI} from './FileAPI';
import MessageDisplay from '../common/components/MessageDisplay';
import ErrorDialog from "../common/components/ErrorDialog";
import VocabularyContext from "../metadata/vocabulary/VocabularyContext";
import {
    MACHINE_ONLY_URI,
    SHACL_CLASS,
    SHACL_DATATYPE,
    SHACL_DESCRIPTION,
    SHACL_MAX_COUNT,
    SHACL_MIN_COUNT,
    SHACL_NAME, SHACL_ORDER,
    SHACL_PATH
} from "../constants";
import {determinePropertyShapesForTypes, determineShapeForTypes, getLabelForType} from "../metadata/common/vocabularyUtils";
import {getFirstPredicateId, getFirstPredicateValue} from "../metadata/common/jsonLdUtils";
import {getBrowserSubpath, getHierarchyLevelByType, getPathHierarchy, isDirectory} from "./fileUtils";
import EmptyInformationDrawer from "../common/components/EmptyInformationDrawer";
import {compareBy, comparing} from "../common/utils/genericUtils";
import {hasEditAccess} from "../users/userUtils";

const useStyles = makeStyles((theme) => ({
    expandOpen: {
        transform: 'rotate(180deg)',
    },
    card: {
        marginTop: 0,
        marginBottom: 6,
        flex: 1,
        display: "flex",
        flexDirection: "column",
        outline: "none",
        transitionBorder: ".24s",
        easeInOut: true
    },
    activeStyle: {
        borderColor: theme.palette.info.main,
        borderWidth: 2,
        borderRadius: 2,
        borderStyle: "dashed",
        opacity: 0.4
    },
    acceptStyle: {
        borderColor: theme.palette.success.main
    },
    rejectStyle: {
        borderColor: theme.palette.error.main
    },
    typeNameStyle: {
        float: 'right',
        fontSize: '0.8em',
        marginTop: 2,
        borderColor: 'none',
        borderWidth: 0,
        borderStyle: 'none',
        opacity: 0.4
    }
}));

type ContextualDirectoryInformationDrawerProperties = {
    path: string;
    selected: any[];
    showDeleted: boolean;
    atLeastSingleRootDirectoryExists: boolean;
    refreshFiles: () => {};
};

const generateTemplate = (vocabulary, metadataType) => {
    const userProps = flatMap(
        [metadataType]
            .map(uri => determinePropertyShapesForTypes(vocabulary, [uri]))
    ).sort(comparing(
        compareBy(p => (
            typeof getFirstPredicateValue(p, SHACL_ORDER) === 'number' ? getFirstPredicateValue(p, SHACL_ORDER) : Number.MAX_SAFE_INTEGER)),
        compareBy(ps => getFirstPredicateValue(ps, SHACL_NAME))
    )).filter(ps => !getFirstPredicateValue(ps, MACHINE_ONLY_URI));

    const uniqueProps = [...new Set(userProps.map(ps => getFirstPredicateId(ps, SHACL_PATH)))
        .values()]
        .map(iri => userProps.find(ps => getFirstPredicateId(ps, SHACL_PATH) === iri));

    const typename = ps => {
        const datatype = getFirstPredicateId(ps, SHACL_DATATYPE);
        if (datatype) {
            return datatype.substring(datatype.lastIndexOf('#') + 1);
        }
        const type = getFirstPredicateId(ps, SHACL_CLASS);
        const classShape = determineShapeForTypes(vocabulary, [type]);
        return getFirstPredicateValue(classShape, SHACL_NAME);
    };

    const type = ps => {
        let shaclType = getFirstPredicateId(ps, SHACL_DATATYPE) || getFirstPredicateId(ps, SHACL_CLASS);
        if (!shaclType) {
            shaclType = "";
        }
        return shaclType.substring(shaclType.lastIndexOf('#') + 1);
    };

    const cardinality = ps => getFirstPredicateValue(ps, SHACL_MIN_COUNT, 0) + '..' + getFirstPredicateValue(ps, SHACL_MAX_COUNT, '*');

    const doc = uniqueProps.map(ps => [
        '# ',
        getFirstPredicateValue(ps, SHACL_NAME),
        getFirstPredicateValue(ps, SHACL_DESCRIPTION, ""),
        typename(ps),
        cardinality(ps),
        getFirstPredicateId(ps, SHACL_PATH)
    ]);

    const entityNames = uniqueProps.filter(ps => !getFirstPredicateId(ps, SHACL_DATATYPE))
        .map(ps => JSON.stringify(getFirstPredicateValue(ps, SHACL_NAME)).replaceAll('"', "'"));
    const sampleEntityNames = entityNames.length > 2 ? entityNames.slice(0, 2).join(' and ') : entityNames.join(' and ');
    const sampleRow = suffix => uniqueProps.map(prop => ((type(prop) === "string" || type(prop) === "markdown") ? "\"Sample text value\"" : `${type(prop)}${suffix}`));

    return '#   This section describes the CSV-based format used for bulk metadata uploads.\n'
        + `#   Entities (e.g. ${sampleEntityNames}) can be referenced by ID; multiple values must be separated by the pipe symbol |.\n`
        + '#   Boolean values should be \'true\' or \'false\'.\n'
        + '#   The \'DirectoryName\' column is the name of the directory you are uploading metadata for. This name is the same as the label for the upload target.\n'
        + '#\n'
        + table([
            ['#', 'COLUMN', 'DESCRIPTION', 'TYPE', 'CARDINALITY', 'PREDICATE'],
            ['#', 'DirectoryName', 'Use the name of the target, e.g. \'Sample 2020-09-05\'', 'string', '1..1', ''],
            ...doc]) + '\n#\n'
        + '"DirectoryName",' + uniqueProps.map(ps => JSON.stringify(getFirstPredicateValue(ps, SHACL_NAME))).join(',') + '\n'
        + '# PUT YOUR DATA BELOW FOLLOWING SAMPLE ROWS. REMOVE THIS LINE AND THE SAMPLE ROWS AFTERWARDS.\n'
        + '# Name_01,' + sampleRow("_0").join(',') + '\n'
        + '# Name_02,' + sampleRow("_1").join(',') + '\n';
};

const MetadataCard = (props) => {
    const {title, avatar, children, forceExpand, allowCsvUpload, metadataUploadPath, metadataUploadType, uploadDone} = props;
    const [expandedManually, setExpandedManually] = useState(null); // true | false | null
    const expanded = (expandedManually != null) ? expandedManually : forceExpand;
    const toggleExpand = () => setExpandedManually(!expanded === forceExpand ? null : !expanded);
    const classes = useStyles();
    const {vocabulary} = useContext(VocabularyContext);
    const fileTemplate = vocabulary && metadataUploadPath && generateTemplate(vocabulary, metadataUploadType);
    const [uploadingMetadata, setUploadingMetadata] = useState(false);
    const {enqueueSnackbar} = useSnackbar();

    const uploadMetadata = (file) => {
        setUploadingMetadata(true);
        LocalFileAPI.uploadMetadata(metadataUploadPath, file)
            .then(() => enqueueSnackbar('Metadata have been successfully uploaded'))
            .then(() => uploadDone())
            .catch(e => {
                const errorContents = (
                    <DialogContentText>
                        <Typography style={{fontFamily: 'Monospace', fontSize: 16}} component="p">
                            {e.message}
                        </Typography>
                    </DialogContentText>
                );
                ErrorDialog.showError('Error uploading metadata', errorContents);
            })
            .finally(() => setUploadingMetadata(false));
    };

    const {getRootProps, getInputProps, open, isDragActive, isDragAccept, isDragReject} = useDropzone({
        noClick: true,
        noKeyboard: true,
        accept: ".csv,text/csv",
        onDropAccepted: (files) => {
            if (files.length === 1) {
                uploadMetadata(files[0]);
            } else {
                ErrorDialog.showError("Please upload metadata files one by one");
            }
        }
    });

    const rootProps = metadataUploadPath && getRootProps();
    const inputProps = metadataUploadPath && getInputProps();
    const dropzoneClassName = () => `${classes.card} ${isDragActive && classes.activeStyle} ${isDragReject && classes.rejectStyle} ${isDragAccept && classes.acceptStyle}`;

    const csvUpload = () => (
        <>
            {metadataUploadPath && (uploadingMetadata
                ? <CircularProgress size={10} />
                : allowCsvUpload && (
                    <Tooltip
                        interactive
                        title={(
                            <>
                                <div>Upload metadata in CSV format.</div>
                                <div>
                                    {'Download '}
                                    <Link
                                        download="metadata.csv"
                                        href={'data:text/csv;charset=utf-8,' + encodeURIComponent(fileTemplate)}
                                    >template file
                                    </Link>
                                </div>
                            </>
                        )}
                    >
                        <IconButton onClick={open}><CloudUpload /></IconButton>
                    </Tooltip>
                ))}
            <IconButton
                onClick={toggleExpand}
                aria-expanded={expanded}
                aria-label="Show more"
                className={expanded ? classes.expandOpen : ''}
            >
                <ExpandMore />
            </IconButton>
        </>
    );

    return (
        <Card
            {...rootProps}
            className={dropzoneClassName()}
        >
            {inputProps && (<input {...inputProps} />)}
            <CardHeader
                titleTypographyProps={{variant: 'h6'}}
                title={title}
                subheader={metadataUploadPath && 'Drag \'n\' drop a metadata file here or click the edit button below to see all available fields.'}
                avatar={avatar}
                style={{wordBreak: 'break-word'}}
                action={csvUpload()}
            />
            <Collapse in={expanded} timeout="auto" unmountOnExit>
                <CardContent>
                    {children}
                </CardContent>
            </Collapse>
        </Card>
    );
};

const PathMetadata = React.forwardRef((
    {path, showDeleted, forceExpand, allowCsvUpload, onRename},
    ref
) => {
    const {data, error, loading} = useAsync(() => LocalFileAPI.stat(path, showDeleted), [path]);
    const {hierarchy, vocabulary} = useContext(VocabularyContext);
    const [updateDate, setUpdateDate] = useState(Date.now());
    const classes = useStyles();
    const uploadDone = () => {
        // eslint-disable-next-line
        console.log('before ' + updateDate);
        setUpdateDate(Date.now());
        // eslint-disable-next-line
        console.log('after ' + updateDate);
        // eslint-disable-next-line
        console.log('now  ' + Date.now());
    };
    let body;
    let linkedEntityType;
    let linkedEntityIri;
    let access;
    let hasEditRight = false;
    let isCurrentPathDirectory;
    let cardTitle = "Metadata";
    let avatar = <FolderOpenOutlined />;
    if (error) {
        body = <MessageDisplay message="An error occurred while determining metadata subject" />;
    } else if (loading) {
        body = <div>Loading...</div>;
    } else if (!data) {
        body = <div>No metadata found</div>;
    } else {
        ({linkedEntityIri, linkedEntityType, access} = data);
        const typeName = getLabelForType(vocabulary, linkedEntityType);
        hasEditRight = hasEditAccess(access);

        cardTitle = <span>{data.basename} <span className={classes.typeNameStyle}>{typeName}</span></span>;
        isCurrentPathDirectory = isDirectory(data, getHierarchyLevelByType(hierarchy, linkedEntityType));
        body = (
            <LinkedDataEntityFormWithLinkedData
                subject={linkedEntityIri}
                hasEditRight={hasEditRight}
                updateDate={updateDate}
                onRename={onRename}
            />
        );
        if (!isCurrentPathDirectory) {
            avatar = <InsertDriveFileOutlined />;
        }
    }

    return (
        <MetadataCard
            ref={ref}
            title={cardTitle}
            avatar={avatar}
            forceExpand={forceExpand}
            allowCsvUpload={allowCsvUpload}
            metadataUploadPath={hasEditRight && forceExpand && path}
            metadataUploadType={linkedEntityType}
            uploadDone={uploadDone}
        >
            {body}
        </MetadataCard>
    );
});

type DirectoryInformationDrawerProps = {
    path: string;
    atLeastSingleRootDirectoryExists: boolean;
    showDeleted: boolean;
    selected: string;
    allowCsvUpload: boolean;
};

export const DirectoryInformationDrawer = (props: DirectoryInformationDrawerProps) => {
    const {path, showDeleted, selected, atLeastSingleRootDirectoryExists, allowCsvUpload, refreshFiles, history} = props;

    const paths = getPathHierarchy(path);

    if (selected && selected.length === 1 && selected[0] !== path) {
        paths.push(selected[0]);
    }

    if (paths.length === 0 && (!selected || selected.length === 0)) {
        return atLeastSingleRootDirectoryExists ? (
            <EmptyInformationDrawer message="Select a file or a folder to display its metadata" />
        ) : <></>;
    }

    const onRename = () => {
        const currentPaths = getPathHierarchy(getBrowserSubpath(history.location.pathname));
        if (paths.length <= currentPaths.length) {
            history.push('/browser');
        } else {
            refreshFiles();
        }
    };

    return (
        paths.map((metadataPath, index) => (
            <PathMetadata
                key={metadataPath}
                path={metadataPath}
                showDeleted={showDeleted}
                forceExpand={index === paths.length - 1}
                allowCsvUpload={allowCsvUpload}
                onRename={onRename}
            />
        ))
    );
};

const ContextualDirectoryInformationDrawer = (props: ContextualDirectoryInformationDrawerProperties) => (
    <SnackbarProvider maxSnack={3}>
        <DirectoryInformationDrawer
            {...props}
        />
    </SnackbarProvider>
);

export default withRouter(ContextualDirectoryInformationDrawer);
