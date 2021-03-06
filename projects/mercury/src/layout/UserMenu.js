import React, {useContext, useState} from 'react';
import {
    Avatar,
    Button,
    ClickAwayListener,
    Grow,
    ListItem, ListItemIcon, ListItemText,
    MenuItem,
    MenuList,
    Paper,
    Popper
} from '@material-ui/core';
import {withStyles} from '@material-ui/core/styles';
import {ErrorOutline} from '@material-ui/icons';

import Divider from '@material-ui/core/Divider';
import ExitToAppIcon from '@material-ui/icons/ExitToApp';
import UserContext from "../users/UserContext";
import LogoutContext from "../users/LogoutContext";
import {getDisplayName} from "../users/userUtils";

const styles = {
    row: {
        display: 'flex',
        justifyContent: 'center',
        paddingTop: 0,
        paddingBottom: 0
    },
    avatar: {
        margin: 10,
    },
    logout: {
        width: 50
    },
    menu: {
        paddingTop: 0
    },
    userMenu: {
        borderLeft: '8px solid #999'
    }
};

const UserMenu = ({classes}) => {
    const [anchorEl, setAnchorEl] = useState(null);
    const {currentUser, currentUserLoading, currentUserError} = useContext(UserContext);
    const logout = useContext(LogoutContext);

    const handleClick = (event) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    const handleLogout = () => {
        handleClose();
        logout();
    };

    if (currentUserLoading) {
        return '...';
    }

    if (currentUserError || !currentUser) {
        return <ErrorOutline style={{fontSize: '2em'}} color="inherit" />;
    }

    return (
        <>
            <Button
                aria-owns={anchorEl ? 'user-menu' : null}
                aria-haspopup="true"
                color="inherit"
                onClick={handleClick}
                className={classes.row}
            >
                <Avatar alt={currentUser.name} src="/public/images/avatar.png" className={classes.avatar} />
                <span>
                    {getDisplayName(currentUser)}
                </span>
            </Button>
            <Popper open={Boolean(anchorEl)} anchorEl={anchorEl} transition disablePortal>
                {({TransitionProps, placement}) => (
                    <Grow
                        {...TransitionProps}
                        id="menu-list-grow"
                        style={{transformOrigin: placement === 'bottom' ? 'center top' : 'center bottom'}}
                    >
                        <Paper>
                            <ClickAwayListener onClickAway={handleClose}>
                                <MenuList className={classes.menu}>
                                    <ListItem className={classes.userMenu}>
                                        <ListItemText
                                            primary={currentUser.username}
                                            secondary={currentUser.email}
                                            style={{cursor: 'default'}}
                                        />
                                    </ListItem>
                                    <Divider />
                                    <MenuItem onClick={handleLogout}>
                                        <ListItemIcon>
                                            <ExitToAppIcon />
                                        </ListItemIcon>
                                        <ListItemText primary="Logout" />
                                    </MenuItem>
                                </MenuList>
                            </ClickAwayListener>
                        </Paper>
                    </Grow>
                )}
            </Popper>
        </>
    );
};

export default withStyles(styles)(UserMenu);
