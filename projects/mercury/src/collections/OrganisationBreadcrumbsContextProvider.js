import React from "react";
import {Folder} from "@material-ui/icons";
import BreadcrumbsContext from "../common/contexts/BreadcrumbsContext";

export default ({children}) => (
    <BreadcrumbsContext.Provider value={{segments: [
        {
            label: 'Departments',
            icon: <Folder />,
            href: '/departments'
        }
    ]}}
    >
        {children}
    </BreadcrumbsContext.Provider>
);
