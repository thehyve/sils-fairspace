import * as jsonld from 'jsonld/dist/jsonld';
import Config from "../Config/Config";
import vocabulary from './vocabulary.json';
import failOnHttpError from "../../utils/httputils";
import Vocabulary from "./Vocabulary";
import {PROPERTY_URI, DOMAIN_URI} from '../../constants';

const GET_ENTITIES_SPARQL = `
    PREFIX rdfs:  <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    CONSTRUCT {?s rdf:type ?t . ?s rdfs:label ?l}
    WHERE {
        { ?s rdf:type ?t FILTER(?t in (%types))}
        OPTIONAL { ?s rdfs:label ?l}
    }
`;

class MetadataAPI {
    static getParams = {
        method: 'GET',
        headers: new Headers({Accept: 'application/ld+json'}),
        credentials: 'same-origin'
    };

    constructor() {
        // Initialize the vocabulary
        this.vocabularyPromise = jsonld.expand(vocabulary)
            .then(expandedVocabulary => new Vocabulary(expandedVocabulary));
    }

    get(params) {
        const query = Object.keys(params).map(key => `${key}=${encodeURIComponent(params[key])}`).join('&');
        return fetch(`${Config.get().urls.metadata.extendedStatements}?${query}`, MetadataAPI.getParams)
            .then(failOnHttpError("Failure when retrieving metadata"))
            .then(response => response.json())
            .then(jsonld.expand);
    }

    /**
     * Update values in the metadata store
     * @param subject   Single URI representing the subject to update
     * @param predicate Single URI representing the predicate to update
     * @param values    Array with objects representing the rdf-object for the triples.
     *                  Each object must have a 'value' key.
     *                  e.g.: [ {value: 'user 1'}, {value: 'another user'} ]
     * @returns {*}
     */
    update(subject, predicate, values) {
        if (!subject || !predicate || !values) {
            return Promise.reject(Error("No subject, predicate or values given"));
        }

        const request = (values.length === 0)
            // eslint-disable-next-line prefer-template
            ? fetch(Config.get().urls.metadata.statements
                + '?subject=' + encodeURIComponent(subject)
                + '&predicate=' + encodeURIComponent(predicate), {method: 'DELETE', credentials: 'same-origin'})
            : fetch(Config.get().urls.metadata.statements, {
                method: 'PATCH',
                headers: new Headers({'Content-type': 'application/ld+json'}),
                credentials: 'same-origin',
                body: JSON.stringify(this.toJsonLd(subject, predicate, values))
            });

        return request.then(failOnHttpError("Failure when updating metadata"));
    }

    getVocabulary() {
        return this.vocabularyPromise;
    }

    /**
     * Returns all entities in the metadata store for the given type
     *
     * More specifically this method returns all entities x for which a
     * triple exist <x> <@type> <type> exists.
     *
     * @param type  URI of the Class that the entities should be a type of
     * @returns Promise<jsonld> A promise with an expanded version of the JSON-LD structure, describing the entities.
     *                          The entities will have an ID, type and optionally an rdfs:label
     */
    getEntitiesByType(type) {
        return this.getEntitiesByTypes([type]);
    }

    /**
     * Returns all entities in the metadata store for the given list of types
     *
     * More specifically this method returns all entities x for which a
     * triple exist <x> <@type> <t> exists where t is in the given list of types
     *
     * @param type  URI of the Class that the entities should be a type of
     * @returns Promise<jsonld> A promise with an expanded version of the JSON-LD structure, describing the entities.
     *                          The entities will have an ID, type and optionally an rdfs:label
     */
    getEntitiesByTypes(types) {
        const query = GET_ENTITIES_SPARQL.replace('%types', types.map(type => `<${type}>`).join(', '));

        return fetch(Config.get().urls.metadata.query, {
            method: 'POST',
            headers: new Headers({'Accept': 'application/ld+json', 'Content-type': 'application/sparql-query'}),
            credentials: 'same-origin',
            body: query
        })
            .then(failOnHttpError("Failure when retrieving entities"))
            .then(response => response.json())
            .then(jsonld.expand);
    }

    getPropertiesByDomain(type) {
        return this.getVocabulary()
            .then(subjects => subjects.filter(s => (s['@type'] || []).includes(PROPERTY_URI)
                && (s[DOMAIN_URI] || []).includes(type)));
    }

    toJsonLd(subject, predicate, values) {
        return [
            {
                '@id': subject,
                [predicate]: values.map(value => ({'@id': value.id, '@value': value.value}))
            }
        ];
    }

    getSubjectByPath(path) {
        return fetch(Config.get().urls.metadata.pid, {
            method: 'POST',
            body: JSON.stringify({value: path}),
            headers: new Headers({'Content-Type': 'application/json', 'Accept': 'application/json'}),
            credentials: 'same-origin'
        })
            .then(failOnHttpError("Failure when retrieving metadata"))
            .then(response => response.json())
            .then(data => data.id);
    }
}

export default new MetadataAPI();
