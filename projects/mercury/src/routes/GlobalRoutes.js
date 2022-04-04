import React from 'react';
import {Redirect, Route, Switch} from "react-router-dom";
import logout from "./logout";
import BrowserLayout from "../layout/BrowserLayout";

const GlobalRoutes = () => (
    <Switch>
        <Route
            path="/login"
            render={() => {
                window.location.href = new URLSearchParams(window.location.search).get('redirectUrl');
            }}
        />

        <Route
            path="/logout"
            render={() => logout()}
        />

        <Route
            path="/"
            component={BrowserLayout}
        />

        <Redirect to="/browser" />
    </Switch>
);

export default GlobalRoutes;
