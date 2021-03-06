import React, {useContext, useEffect, useMemo, useState} from 'react';
import {
    Checkbox,
    Link,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TablePagination,
    TableRow,
    TableSortLabel,
    withStyles
} from "@material-ui/core";
import {FolderOpen, NoteOutlined} from "@material-ui/icons";

import styles from './FileList.styles';
import {compareBy, formatDateTime, stableSort} from "../common/utils/genericUtils";
import useSorting from "../common/hooks/UseSorting";
import usePagination from "../common/hooks/UsePagination";
import ColumnFilterInput from "../common/components/ColumnFilterInput";
import MessageDisplay from "../common/components/MessageDisplay";
import TablePaginationActions from "../common/components/TablePaginationActions";
import VocabularyContext from "../metadata/vocabulary/VocabularyContext";
import {getAllowedDirectoryTypes, getHierarchyLevelByType, isDirectory} from "./fileUtils";
import {getLabelPluralForType} from "../metadata/common/vocabularyUtils";

export const FileList = ({
    files, onPathCheckboxClick, onPathDoubleClick,
    selectionEnabled, onAllSelection, onPathHighlight,
    showDeleted, preselectedFile, hierarchy = [],
    openedDirectoryType, classes = {}
}) => {
    const [hoveredFileName, setHoveredFileName] = useState('');

    const columns = {
        name: {
            valueExtractor: f => f.basename,
            label: 'Name'
        },
        type: {
            valueExtractor: f => f.linkedEntityType,
            label: 'Type'
        },
        dateDeleted: {
            valueExtractor: f => f.dateDeleted,
            label: 'Deleted'
        }
    };

    const [filterValue, setFilterValue] = useState("");
    const [filteredFiles, setFilteredFiles] = useState(files);
    const {orderedItems, orderAscending, orderBy, toggleSort} = useSorting(filteredFiles, columns, 'name');
    const directoriesBeforeFiles = useMemo(
        () => stableSort(orderedItems, compareBy('type')),
        [orderedItems]
    );

    const {page, setPage, rowsPerPage, setRowsPerPage, pagedItems} = usePagination(directoriesBeforeFiles);

    useEffect(() => {
        if (!filterValue) {
            setFilteredFiles(files);
        } else {
            setFilteredFiles(files.filter(f => f.basename.toLowerCase().includes(filterValue.toLowerCase())));
        }
    }, [files, filterValue]);

    useEffect(() => {
        setPage(0);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filterValue]);

    useEffect(() => {
        if (preselectedFile) {
            const preselectedFileIndex = directoriesBeforeFiles.findIndex(f => f.filename === preselectedFile);
            if (preselectedFileIndex > -1) {
                const preselectedFilePage = Math.floor(preselectedFileIndex / rowsPerPage);
                setPage(preselectedFilePage);
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [preselectedFile]);

    if (!files || files.length === 0 || files[0] === null) {
        return (
            <MessageDisplay
                message={"Empty " + openedDirectoryType + " folder"}
                variant="h6"
                withIcon={false}
                isError={false}
                noWrap={false}
                messageColor="textSecondary"
            />
        );
    }

    let checkboxHeader = null;

    if (selectionEnabled) {
        const numOfSelected = files.filter(f => f.selected).length;
        const allItemsSelected = files.length === numOfSelected;
        checkboxHeader = (
            <TableCell padding="none" style={{verticalAlign: "bottom"}}>
                <Checkbox
                    indeterminate={numOfSelected > 0 && numOfSelected < files.length}
                    checked={allItemsSelected}
                    onChange={(event) => onAllSelection(event.target.checked)}
                />
            </TableCell>
        );
    }

    const renderFileFilter = () => (
        <ColumnFilterInput placeholder="Filter by name" filterValue={filterValue} setFilterValue={setFilterValue} />
    );

    return (
        <Paper className={classes.root}>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            {checkboxHeader}
                            <TableCell padding="none" />
                            <TableCell className={classes.headerCell}>
                                <TableSortLabel
                                    active={orderBy === 'name'}
                                    direction={orderAscending ? 'asc' : 'desc'}
                                    onClick={() => toggleSort('name')}
                                >
                                Name
                                </TableSortLabel>
                                {renderFileFilter()}
                            </TableCell>
                            <TableCell align="right" className={classes.headerCell}>
                                <TableSortLabel
                                    active={orderBy === 'entityType'}
                                    direction={orderAscending ? 'asc' : 'desc'}
                                    onClick={() => toggleSort('entityType')}
                                >
                                Type
                                </TableSortLabel>
                            </TableCell>
                            {showDeleted && (
                                <TableCell align="right" className={classes.headerCell}>
                                    <TableSortLabel
                                        active={orderBy === 'dateDeleted'}
                                        direction={orderAscending ? 'asc' : 'desc'}
                                        onClick={() => toggleSort('dateDeleted')}
                                    >
                                    Deleted
                                    </TableSortLabel>
                                </TableCell>
                            )}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {pagedItems.map((file) => {
                            const checkboxVisibility = hoveredFileName === file.filename || file.selected ? 'visible' : 'hidden';
                            const linkedEntity = getHierarchyLevelByType(hierarchy, file.linkedEntityType);

                            return (
                                <TableRow
                                    hover
                                    key={file.filename}
                                    selected={file.selected}
                                    onClick={() => onPathHighlight(file)}
                                    onDoubleClick={() => onPathDoubleClick(file, linkedEntity)}
                                    onMouseEnter={() => setHoveredFileName(file.filename)}
                                    onMouseLeave={() => setHoveredFileName('')}
                                    className={file.dateDeleted && classes.deletedFileRow}
                                >
                                    {
                                        selectionEnabled ? (
                                            <TableCell
                                                data-testid="checkbox-cell"
                                                padding="none"
                                                onDoubleClick={(e) => e.stopPropagation()}
                                                onClick={(e) => {e.stopPropagation(); onPathCheckboxClick(file);}}
                                            >
                                                <Checkbox
                                                    style={{visibility: checkboxVisibility}}
                                                    checked={file.selected}
                                                />
                                            </TableCell>
                                        ) : null
                                    }

                                    <TableCell style={{padding: 5}} align="left">
                                        {isDirectory(file, linkedEntity) ? <FolderOpen /> : <NoteOutlined />}
                                    </TableCell>
                                    <TableCell>
                                        {isDirectory(file, linkedEntity) ? (
                                            <Link
                                                onClick={(e) => {e.stopPropagation(); onPathDoubleClick(file, linkedEntity);}}
                                                color="inherit"
                                                variant="body2"
                                                component="button"
                                                underline="hover"
                                            >
                                                {file.basename}
                                            </Link>
                                        ) : (
                                            <span>{file.basename}</span>
                                        )}
                                    </TableCell>
                                    <TableCell align="right">
                                        {file.linkedEntityType ? linkedEntity.label : null}
                                    </TableCell>
                                    {showDeleted && (
                                        <TableCell align="right">
                                            {file.dateDeleted ? formatDateTime(file.dateDeleted) : null}
                                        </TableCell>
                                    )}
                                </TableRow>
                            );
                        })}
                    </TableBody>
                </Table>
                <TablePagination
                    rowsPerPageOptions={[5, 10, 25, 100]}
                    component="div"
                    count={filteredFiles.length}
                    rowsPerPage={rowsPerPage}
                    page={page}
                    onPageChange={(e, p) => setPage(p)}
                    onRowsPerPageChange={e => setRowsPerPage(e.target.value)}
                    style={{overflowX: "hidden"}}
                    ActionsComponent={TablePaginationActions}
                />
            </TableContainer>
        </Paper>
    );
};

const ContextualFileList = props => {
    const {hierarchy, vocabulary} = useContext(VocabularyContext);
    let typeName = "";
    if (props.openedDirectory) {
        const currentLevelType = getAllowedDirectoryTypes(hierarchy, props.openedDirectory.directoryType)[0];
        typeName = getLabelPluralForType(vocabulary, currentLevelType);
    }

    return <FileList hierarchy={hierarchy} openedDirectoryType={typeName} {...props} />;
};

export default withStyles(styles)(ContextualFileList);
