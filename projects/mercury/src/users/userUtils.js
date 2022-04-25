import React from "react";
import {Create, MenuBook, Settings} from "@material-ui/icons";
import type {User} from "./UsersAPI";

export type AccessLevel = 'None' | 'Read' | 'Write' | 'Manage';

export function getDisplayName(user: User) {
    return (user && user.name) || '';
}

export function getEmail(user: User) {
    return (user && user.email) || '';
}

export const isAdmin = (user: User) => user && user.isAdmin;

/**
 * Check if collaborator can alter permission. User can alter permission if:
 * - has manage access to a resource
 * - permission is not his/hers, unless is admin
 */
export const canAlterPermission = (canManage, user, currentLoggedUser) => {
    const isSomeoneElsePermission = currentLoggedUser.iri !== user.iri;
    return canManage && (isSomeoneElsePermission || !!isAdmin(user));
};

export const accessIcon = (access: AccessLevel, fontSize: 'inherit' | 'default' | 'small' | 'large' = 'default') => {
    switch (access) {
        case 'Read':
            return <MenuBook titleAccess={`${access} access`} fontSize={fontSize} />;
        case 'Write':
            return <Create titleAccess={`${access} access`} fontSize={fontSize} />;
        case 'Manage':
            return <Settings titleAccess={`${access} access`} fontSize={fontSize} />;
        case 'None':
        default:
            return <></>;
    }
};

export const hasEditAccess = (access: AccessLevel) => access === 'Write' || access === 'Manage';

export const canEditMetadataType = (user, isAdminEditOnly) => isAdmin(user) || !isAdminEditOnly;
