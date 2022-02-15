import React from 'react';

import VocabularyAPI from '../common/VocabularyAPI';
import useAsync from "../../common/hooks/UseAsync";

const VocabularyContext = React.createContext();

export const VocabularyProvider = ({children}) => {
    const {data: vocabulary = [], loading: vocabularyLoading, error: vocabularyError} = useAsync(() => VocabularyAPI.get());
    const {data: hierarchy} = useAsync(() => VocabularyAPI.getHierarchyNodes());
    return (
        <VocabularyContext.Provider
            value={{
                vocabulary,
                vocabularyLoading,
                vocabularyError,
                hierarchy
            }}
        >
            {children}
        </VocabularyContext.Provider>
    );
};

export default VocabularyContext;
