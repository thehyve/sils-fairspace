import React, {useState} from 'react';
import {COPY, CUT} from "../../constants";

const ClipboardContext = React.createContext({
    cut: () => {},
    copy: () => {},
    isEmpty: () => {},
    length: () => {},
    clear: () => {},
    method: '',
    filenames: [],
    linkedEntityType: null
});

export const ClipboardProvider = ({children}) => {
    const [method, setMethod] = useState(CUT);
    const [filenames, setFilenames] = useState([]);
    const [linkedEntityType, setLinkedEntityType] = useState(null);

    const cut = (paths, linkedEntityTypeOfSource) => {
        setMethod(CUT);
        setFilenames(paths);
        setLinkedEntityType(linkedEntityTypeOfSource);
    };

    const copy = (paths, linkedEntityTypeOfSource) => {
        setMethod(COPY);
        setFilenames(paths);
        setLinkedEntityType(linkedEntityTypeOfSource);
    };

    const isEmpty = () => filenames.length === 0;
    const length = () => filenames.length;
    const clear = () => {
        setFilenames([]);
        setLinkedEntityType(null);
    };

    return (
        <ClipboardContext.Provider
            value={{
                cut,
                copy,
                isEmpty,
                length,
                clear,

                method,
                filenames,
                linkedEntityType
            }}
        >
            {children}
        </ClipboardContext.Provider>
    );
};

export default ClipboardContext;
