import React, {useEffect, useState} from 'react';

import VocabularyAPI from '../common/VocabularyAPI';
import useAsync from "../../common/hooks/UseAsync";
import {determineHierarchy} from "../common/vocabularyUtils";

const VocabularyContext = React.createContext();

export const VocabularyProvider = ({children}) => {
    const {data: vocabulary = [], loading: vocabularyLoading, error: vocabularyError} = useAsync(() => VocabularyAPI.get());
    const [hierarchy, setHierarchy] = useState([]);

    useEffect(() => {
        if (vocabulary && vocabulary.length > 0) {
            setHierarchy(determineHierarchy(vocabulary));
        }
    }, [vocabulary]);

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
