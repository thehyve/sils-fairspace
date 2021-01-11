/* eslint-disable react-hooks/exhaustive-deps */
import {useCallback, useEffect, useState} from "react";
import axios from "axios";
import MetadataViewAPI from "./MetadataViewAPI";
import type {MetadataViewData, MetadataViewFilter} from "./MetadataViewAPI";

const LOCATION_FILTER_FIELD = 'location';

export type Count = {
    count: number;
    timeout: boolean;
}

export type ViewData = {
    data: MetadataViewData;
    count: Count;
    loading: boolean;
    loadingCount: boolean;
    error: any;
    refreshDataOnly: boolean;
};

const useViewData = (view: string, filters: MetadataViewFilter[], locationContext: string, rowsPerPage: number): ViewData => {
    const [data, setData] = useState({});
    const [count, setCount] = useState({});
    const [loading, setLoading] = useState(true);
    const [loadingCount, setLoadingCount] = useState(true);
    const [error, setError] = useState();
    const [countRequestCancelToken, setCountRequestCancelToken] = useState();
    const [viewDataRequestCancelToken, setViewDataRequestCancelToken] = useState();

    const locationFilter: MetadataViewFilter = {
        field: LOCATION_FILTER_FIELD,
        values: [locationContext]
    };

    const allFilters = !locationContext ? (
        [...filters]
    ) : (
        [...filters.filter(f => ![LOCATION_FILTER_FIELD].includes(f.field)), locationFilter]
    );

    const fetchCount = () => {
        setCount({count: -1});
        setLoadingCount(true);
        if (countRequestCancelToken) {
            countRequestCancelToken.cancel("Fetching count operation canceled due to new request.");
        }
        const token = axios.CancelToken.source();
        setCountRequestCancelToken(token);
        MetadataViewAPI.getCount(token, view, allFilters).then(res => {
            if (res) {
                if (res.count == null) {
                    res.count = -1;
                }
                setCount(res);
                setLoadingCount(false);
            }
        });
    };

    const fetchViewData = (newPage: number, newRowsPerPage: number): Promise<MetadataViewData> => {
        if (viewDataRequestCancelToken) {
            viewDataRequestCancelToken.cancel("Fetching data operation canceled due to new request.");
        }
        const token = axios.CancelToken.source();
        setViewDataRequestCancelToken(token);
        return MetadataViewAPI.getViewData(token, view, newPage, newRowsPerPage, allFilters);
    };

    const refreshAll = useCallback(() => {
        setLoading(true);
        setCount({count: -1});
        fetchViewData(0, rowsPerPage)
            .then((d: MetadataViewData) => {
                setData(d);
                if (d) {
                    if (!d.hasNext) {
                        if (viewDataRequestCancelToken) {
                            viewDataRequestCancelToken.cancel("Fetching count operation canceled due to new data.");
                        }
                        setCount({count: d.rows.length, timeout: false});
                        setLoadingCount(false);
                    } else {
                        fetchCount();
                    }
                }
                setError(undefined);
            })
            .catch((e) => {
                setError(e || true);
                console.error(e || new Error('Unknown error'));
            })
            .finally(() => setLoading(false));
    });

    const refreshDataOnly = useCallback((newPage, newRowsPerPage) => {
        setLoading(true);
        fetchViewData(newPage, newRowsPerPage).then(d => {
            setData(d);
            setError(undefined);
        })
            .catch((e) => {
                setError(e || true);
                console.error(e || new Error('Unknown error'));
            })
            .finally(() => setLoading(false));
    });

    useEffect(() => {refreshAll();}, [view, filters, locationContext]);

    return {
        data,
        count,
        loading,
        loadingCount,
        error,
        refreshDataOnly
    };
};

export default useViewData;
