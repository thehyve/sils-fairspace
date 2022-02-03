import axios from 'axios';
import LinkedDataAPI from "./LinkedDataAPI";
import {extractJsonData, handleHttpError} from '../../common/utils/httpUtils';
import {parseHierachyNodes} from "./vocabularyUtils";

const requestOptions = {
    headers: {Accept: 'application/ld+json'}
};

export class LinkedDataVocabularyAPI extends LinkedDataAPI {
    constructor() {
        super('vocabulary');
    }

    getHierarchyNodes() {
        return axios.get(`${this.getStatementsUrl()}hierarchy/`, requestOptions)
            .then(extractJsonData)
            .then(parseHierachyNodes)
            .catch(handleHttpError("Failure when retrieving metadata"));
    }
}

export default new LinkedDataVocabularyAPI();
