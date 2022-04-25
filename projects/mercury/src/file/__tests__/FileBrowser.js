/* eslint-disable react/jsx-props-no-spreading */
import React from 'react';
import {cleanup, render} from '@testing-library/react';
import '@testing-library/jest-dom/extend-expect';

import {FileBrowser} from '../FileBrowser';
import {UploadsProvider} from "../UploadsContext";
import {VocabularyProvider} from "../../metadata/vocabulary/VocabularyContext";

afterEach(cleanup);

const openedDirectory = {
    iri: "http://localhost/iri/86a2f097-adf9-4733-a7b4-53da7a01d9f0",
    dateCreated: "2019-09-12T10:34:53.585Z",
    createdBy: "http://localhost/iri/6e6cde34-45bc-42d8-8cdb-b6e9faf890d3",
    dateModified: "2019-09-12T10:34:53.585Z",
    modifiedBy: "http://localhost/iri/6e6cde34-45bc-42d8-8cdb-b6e9faf890d3",
    dateDeleted: null,
    deletedBy: null,
    name: "asd",
    description: "",
    creatorObj: {
        iri: "http://localhost/iri/6e6cde34-45bc-42d8-8cdb-b6e9faf890d3",
        name: "John Snow",
        email: "user@example.com"
    }
};

const fileActionsMock = {
    getDownloadLink: () => 'http://a',
    createDirectory: () => Promise.resolve(),
    deleteMultiple: () => Promise.resolve(),
    renameFile: () => Promise.resolve(),
    copyPaths: () => new Promise(resolve => setTimeout(resolve, 500))
};

const selectionMock = {
    isSelected: () => false,
    selected: []
};

const initialProps = {
    openedPath: '/',
    history: {
        listen: () => {}
    },
    files: [{
        filename: 'a'
    }],
    selection: selectionMock,
    classes: {}
};

describe('FileBrowser', () => {
    const renderWithProviders = children => render(
        <VocabularyProvider>
            {children}
        </VocabularyProvider>
    );

    it('renders proper view', () => {
        const {queryByTestId} = renderWithProviders(
            <FileBrowser
                openedDirectory={openedDirectory}
                fileActions={fileActionsMock}
                {...initialProps}
            />
        );

        expect(queryByTestId('files-view')).toBeInTheDocument();
    });

    it('cleans up listener after unmount', () => {
        const cleanupFn = jest.fn();

        const {unmount} = renderWithProviders(
            <FileBrowser
                {...initialProps}
                history={{
                    listen: () => cleanupFn
                }}
            />
        );

        unmount();

        expect(cleanupFn).toHaveBeenCalledTimes(1);
    });
});
