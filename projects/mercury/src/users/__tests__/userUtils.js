import {canAlterPermission, getDisplayName} from "../userUtils";

describe('usersUtils', () => {
    const mockUser = {
        iri: "http://example.com#b4804cdb-b690-41ef-a167-6af7ed983d8d",
        name: 'Daenarys Targaryen',
        email: 'user@example.com'
    };

    describe('get full name', () => {
        it('should return full name', () => {
            const res = getDisplayName(mockUser);
            expect(res).toEqual('Daenarys Targaryen');
        });
    });

    describe('canAlterPermission', () => {
        it('should return false if user cannot manage the collection', () => {
            expect(canAlterPermission(false, {iri: 'subj1'}, {iri: 'subj2'})).toBe(false);
        });
        it('should return false for non-admin user\'s own permission', () => {
            expect(canAlterPermission(true, {iri: 'subj2'}, {iri: 'subj2'})).toBe(false);
        });
        it('should return true for admin user\'s own permission', () => {
            expect(canAlterPermission(true, {iri: 'subj2', isAdmin: true}, {iri: 'subj2'})).toBe(true);
        });
        it('should return true if user cannot manage the collection for someone else\' permission', () => {
            expect(canAlterPermission(true, {iri: 'subj1'}, {iri: 'subj2'})).toBe(true);
        });
    });
});

