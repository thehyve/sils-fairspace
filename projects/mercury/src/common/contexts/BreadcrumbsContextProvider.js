import React from "react";
import {Folder} from "@material-ui/icons";
import BreadcrumbsContext from "./BreadcrumbsContext";

export default ({label, href, children}) => (
    <BreadcrumbsContext.Provider value={{segments: [
        {
            label,
            icon: <Folder />,
            href
        }
    ]}}
    >
        {children}
    </BreadcrumbsContext.Provider>
);
