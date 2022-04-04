import React from 'react';
import {shallow} from "enzyme";
import {act} from 'react-dom/test-utils';
import FileBrowser from '../FileBrowser';
import {DirectoryPage} from "../DirectoryPage";
import DirectoryInformationDrawer from "../DirectoryInformationDrawer";


function shallowRender(history, path, locationSearch = '') {
    const hierarchy = [];
    return shallow (
        <DirectoryPage
            openedDirectory={{path: path, iri: path ? "http://localhost:8080/api/webdav/My_department" : undefined}}
            hierarchy={hierarchy}
            files={[]}
            location={{search: locationSearch}}
            history={history}
            classes={{}}
            currentUser={{canViewPublicMetadata: true}}
            refreshFiles={() => {}}
            fileActions={[]}
            showDeleted={false}
            setShowDeleted={() => {}}
            views={[]}
        />
    );
};

describe('DirectoryPage', () => {
    let wrapper;

    it('renders a directory browser and information drawer', () => {
        const history = [];

        act(() => {
            wrapper = shallowRender(history, '');
        });

        const fileBrowserProps = wrapper.find(FileBrowser).first().props();
        expect(fileBrowserProps.openedDirectory.path).toBe('');
        expect(fileBrowserProps.showDeleted).toBe(false);
        const informationDrawerProps = wrapper.find(DirectoryInformationDrawer).first().props();
        expect(informationDrawerProps.atLeastSingleRootDirectoryExists).toBe(false);
    });

    it('renders a file browser and information drawer for a specified path', () => {
        const history = [];

        act(() => {
            wrapper = shallowRender(history, '/music/jazz');
        });

        const fileBrowserProps = wrapper.find(FileBrowser).first().props();
        expect(fileBrowserProps.openedDirectory.path).toBe('/music/jazz');
        expect(fileBrowserProps.showDeleted).toBe(false);
        const informationDrawerProps = wrapper.find(DirectoryInformationDrawer).first().props();
        expect(informationDrawerProps.atLeastSingleRootDirectoryExists).toBe(true);
    });
});
