const defaultState = {};

const metadataBySubject = (state = defaultState, action) => {
    switch (action.type) {
        case "METADATA_PENDING":
            return {
                ...state,
                [action.meta.subject]: {
                    pending: true,
                    error: false,
                    didInvalidate: false,
                    items: {}
                }
            }
        case "METADATA_FULFILLED":
            return {
                ...state,
                [action.meta.subject]: {
                    ...state[action.meta.subject],
                    pending: false,
                    didInvalidate: false,
                    items: action.payload
                }
            }
        case "METADATA_REJECTED":
            return {
                ...state,
                [action.meta.subject]: {
                    ...state[action.meta.subject],
                    pending: false,
                    didInvalidate: false,
                    error: action.payload || true
                }
            }
        case "INVALIDATE_METADATA":
            return {
                ...state,
                [action.subject]: {
                    ...state[action.subject],
                    didInvalidate: true
                }
            }
        default:
            return state;
    }
};

export default metadataBySubject;
