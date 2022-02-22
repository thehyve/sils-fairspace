import React from 'react';
import {VocabularyProvider} from '../metadata/vocabulary/VocabularyContext';
import MainMenu from './MainMenu';
import BrowserRoutes from '../routes/BrowserRoutes';
import {ServicesProvider} from '../common/contexts/ServicesContext';
import Layout from "./Layout";
import TopBar from "./TopBar";
import {UsersProvider} from "../users/UsersContext";
import {FeaturesProvider} from "../common/contexts/FeaturesContext";
import {MetadataViewProvider} from "../metadata/views/MetadataViewContext";
import {MetadataViewFacetsProvider} from "../metadata/views/MetadataViewFacetsContext";
import {ExternalStoragesProvider} from "../external-storage/ExternalStoragesContext";
import {StatusProvider} from "../status/StatusContext";

const BrowserLayout = () => (
    <StatusProvider>
        <UsersProvider>
            <VocabularyProvider>
                <ServicesProvider>
                    <FeaturesProvider>
                        <ExternalStoragesProvider>
                            <MetadataViewFacetsProvider>
                                <MetadataViewProvider>
                                    <Layout
                                        renderMenu={() => <MainMenu />}
                                        renderMain={() => <BrowserRoutes />}
                                        renderTopbar={() => <TopBar title="Browser" />}
                                    />
                                </MetadataViewProvider>
                            </MetadataViewFacetsProvider>
                        </ExternalStoragesProvider>
                    </FeaturesProvider>
                </ServicesProvider>
            </VocabularyProvider>
        </UsersProvider>
    </StatusProvider>
);

export default BrowserLayout;
