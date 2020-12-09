import React, {useEffect, useState} from 'react';
import {
    FormControlLabel,
    IconButton,
    Paper,
    TableContainer,
    TablePagination,
    Typography,
    withStyles
} from '@material-ui/core';
import {useHistory} from "react-router-dom";
import {ViewColumn} from "@material-ui/icons";
import Checkbox from "@material-ui/core/Checkbox";
import FormControl from "@material-ui/core/FormControl";
import Popover from "@material-ui/core/Popover";
import CheckBoxOutlineBlankIcon from "@material-ui/icons/CheckBoxOutlineBlank";
import CheckBoxIcon from "@material-ui/icons/CheckBox";
import FormGroup from "@material-ui/core/FormGroup";
import type {MetadataViewColumn, MetadataViewFilter} from "./MetadataViewAPI";
import LoadingInlay from "../../common/components/LoadingInlay";
import MessageDisplay from "../../common/components/MessageDisplay";
import type {MetadataViewEntityWithLinkedFiles} from "./metadataViewUtils";
import useViewData from "./UseViewData";
import MetadataViewTable from "./MetadataViewTable";
import useStateWithSessionStorage from "../../common/hooks/UseSessionStorage";
import {
    LOCAL_STORAGE_METADATA_TABLE_ROWS_NUM_KEY,
    SESSION_STORAGE_VISIBLE_COLUMNS_KEY_PREFIX
} from "../../common/constants";
import useStateWithLocalStorage from "../../common/hooks/UseLocalStorage";


type MetadataViewTableContainerProperties = {
    columns: MetadataViewColumn[];
    filters: MetadataViewFilter[];
    toggleRow: () => {};
    view: string;
    locationContext: string;
    selected: MetadataViewEntityWithLinkedFiles;
    classes: any;
};

const styles = () => ({
    table: {
        maxHeight: 'calc(100vh - 215px)',
        overflowY: 'auto',
        overflowX: 'auto',
        '& .MuiTableCell-stickyHeader': {
            backgroundColor: "white"
        }
    },
    tableSettings: {
        position: 'relative',
        marginTop: -50,
        marginRight: 10,
        float: 'right',
        maxWidth: 50
    },
    viewColumnsFormControl: {
        padding: 10
    }
});

export const MetadataViewTableContainer = (props: MetadataViewTableContainerProperties) => {
    const {view, filters, columns, classes} = props;

    const [page, setPage] = useState(0);
    const [visibleColumnNames, setVisibleColumnNames] = useStateWithSessionStorage(
        `${SESSION_STORAGE_VISIBLE_COLUMNS_KEY_PREFIX}_${view.toUpperCase()}`,
        columns.map(c => c.name)
    );
    const [rowsPerPage, setRowsPerPage] = useStateWithLocalStorage(LOCAL_STORAGE_METADATA_TABLE_ROWS_NUM_KEY, 10);
    const [anchorEl, setAnchorEl] = useState(null);

    const idColumn = columns.find(c => c.type === 'id'); // first column of id type
    const columnSelectorOpen = Boolean(anchorEl);
    const history = useHistory();

    const {data, count, countTimeout, error, loading, refreshDataOnly} = useViewData(view, filters, rowsPerPage);

    useEffect(() => {setPage(0);}, [filters]);

    if (loading) {
        return <LoadingInlay />;
    }

    if (error && error.message) {
        return <MessageDisplay message={error.message} />;
    }

    if (!data || !data.rows || !data.rows.length) {
        return <MessageDisplay message="No results found." />;
    }

    const handleChangePage = (e, p) => {
        setPage(p);
        refreshDataOnly(p, rowsPerPage);
    };

    const handleChangeRowsPerPage = (e) => {
        setRowsPerPage(e.target.value);
        setPage(0);
        refreshDataOnly(0, e.target.value);
    };

    const handleVisibleColumnsChange = (event) => {
        if (event.target.checked) {
            setVisibleColumnNames([...visibleColumnNames, event.target.name]);
        } else if (event.target.name !== idColumn.name) {
            setVisibleColumnNames([...visibleColumnNames.filter(cs => cs !== event.target.name)]);
        }
    };

    const handleColumnSelectorButtonClick = (event) => {
        setAnchorEl(event.currentTarget);
    };

    const handleColumnSelectorClose = () => {
        setAnchorEl(null);
    };

    const renderWarning = () => {
        let warning = "";
        if (data.timeout) {
            warning = "Results shown below are incomplete. Fetching of data for the current page took too long.";
        } else if (countTimeout) {
            warning = "Fetching total count of results took too long. The count will not be shown.";
        }
        return warning && <Typography variant="body2" color="error" align="center">{warning}</Typography>;
    };

    const renderColumnSelector = () => (
        <Popover
            open={columnSelectorOpen}
            onClose={handleColumnSelectorClose}
            anchorEl={anchorEl}
            anchorOrigin={{vertical: 'center', horizontal: 'left'}}
            transformOrigin={{vertical: 'top', horizontal: 'right'}}
        >
            <FormControl className={classes.viewColumnsFormControl}>
                <Typography variant="caption">
                    Show/hide columns
                </Typography>
                <FormGroup>
                    {columns.map((column) => (
                        <FormControlLabel
                            key={column.name}
                            control={(
                                <Checkbox
                                    checked={visibleColumnNames.includes(column.name)}
                                    disabled={column.name === idColumn.name}
                                    onChange={handleVisibleColumnsChange}
                                    name={column.name}
                                    icon={<CheckBoxOutlineBlankIcon fontSize="small" />}
                                    checkedIcon={<CheckBoxIcon fontSize="small" />}
                                />
                            )}
                            label={column.title}
                        />
                    ))}
                </FormGroup>
            </FormControl>
        </Popover>
    );

    const renderTableSettings = () => (
        <div className={classes.tableSettings}>
            <IconButton
                aria-label="Show/hide columns"
                title="Show/hide columns"
                onClick={handleColumnSelectorButtonClick}
            >
                <ViewColumn color="primary" />
            </IconButton>
            {renderColumnSelector()}
        </div>
    );

    return (
        <Paper>
            {renderTableSettings()}
            {renderWarning()}
            <TableContainer className={classes.table}>
                <MetadataViewTable
                    {...props}
                    visibleColumnNames={visibleColumnNames}
                    idColumn={idColumn}
                    data={data}
                    history={history}
                />
                <TablePagination
                    rowsPerPageOptions={[5, 10, 25, 100]}
                    component="div"
                    count={count}
                    rowsPerPage={rowsPerPage}
                    page={page}
                    onChangePage={handleChangePage}
                    onChangeRowsPerPage={handleChangeRowsPerPage}
                    style={{overflowX: "hidden"}}
                />
            </TableContainer>
        </Paper>
    );
};

export default withStyles(styles)(MetadataViewTableContainer);
