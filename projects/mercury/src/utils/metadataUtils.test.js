import nodeCrypto from "crypto";

import {
    generateUuid,
    getFirstPredicateId,
    getFirstPredicateValue,
    getLabel, getTypeInfo,
    linkLabel,
    propertiesToShow,
    relativeLink,
    shouldPropertyBeHidden,
    toJsonLd, url2iri,
} from "./metadataUtils";
import * as consts from "../constants";

describe('Metadata Utils', () => {
    describe('linkLabel', () => {
        it('handles IRIs', () => {
            expect(linkLabel('http://localhost/iri/1234')).toEqual('1234');
        });

        it('handles collections', () => {
            expect(linkLabel('http://localhost/collections/coll1')).toEqual('coll1');
        });

        it('can shorten external URLs', () => {
            expect(linkLabel('http://example.com/path', false)).toEqual('http://example.com/path');
            expect(linkLabel('http://example.com/path', true)).toEqual('path');
            expect(linkLabel('http://example.com/path#hash', false)).toEqual('http://example.com/path#hash');
            expect(linkLabel('http://example.com/path#hash', true)).toEqual('hash');
        });
    });

    describe('getLabel', () => {
        it('should return the label if present', () => {
            expect(getLabel({[consts.LABEL_URI]: [{'@value': 'My label'}]})).toEqual('My label');
        });

        it('should return the shacl name if no label is present', () => {
            expect(getLabel({'http://www.w3.org/ns/shacl#name': [{'@value': 'My label'}]})).toEqual('My label');
        });

        it('should not fail if json-ld is not properly expanded', () => {
            expect(getLabel({
                '@id': 'http://test.com/name',
                [consts.LABEL_URI]: 'My label'
            }, true)).toEqual('name');

            expect(getLabel({
                '@id': 'http://test.com/name',
                [consts.LABEL_URI]: {'@value': 'My label'}
            }, true)).toEqual('name');

            expect(getLabel({
                '@id': 'http://test.com/name',
                [consts.LABEL_URI]: ['My label']
            }, true)).toEqual('name');

            expect(getLabel({
                '@id': 'http://test.com/name',
                [consts.LABEL_URI]: []
            }, true)).toEqual('name');
        });

        it('should keep external urls intact if shortenExternalUris is set to false', () => {
            expect(getLabel({'@id': 'http://test.nl/name#lastname'}, false)).toEqual('http://test.nl/name#lastname');
        });
        it('should return part of the url after the pound sign', () => {
            expect(getLabel({'@id': 'http://test.nl/name#lastname'}, true)).toEqual('lastname');
        });
        it('should return part of the url after the last slash if no pound sign present', () => {
            expect(getLabel({'@id': 'http://test.nl/name'}, true)).toEqual('name');
        });
    });

    describe('getFirstPredicateValue', () => {
        it('should return undefined if a property does not exist', () => {
            expect(getFirstPredicateValue({name: 'John'}, 'age')).toEqual(undefined);
        });

        it('should return undefined if a property is empty', () => {
            expect(getFirstPredicateValue({numbers: []}, 'numbers')).toEqual(undefined);
        });

        it('should support literal properties', () => {
            expect(getFirstPredicateValue({numbers: [{'@value': 1}, {'@value': 2}]}, 'numbers')).toEqual(1);
        });
    });

    describe('getFirstPredicateId', () => {
        it('should return undefined if a property does not exist', () => {
            expect(getFirstPredicateId({name: 'John'}, 'age')).toEqual(undefined);
        });

        it('should return undefined if a property is empty', () => {
            expect(getFirstPredicateId({numbers: []}, 'numbers')).toEqual(undefined);
        });

        it('should support reference properties', () => {
            expect(getFirstPredicateId({numbers: [{'@id': 'http://example.com/1'}, {'@id': 'http://example.com/2'}]}, 'numbers'))
                .toEqual('http://example.com/1');
        });
    });

    describe('relativeLink', () => {
        it('should strip the base URL', () => {
            expect(relativeLink('http://example.com:1234/some/path?query=value#bookmark'))
                .toEqual('/some/path?query=value#bookmark');
        });

        it('should also handle simple URLs', () => {
            expect(relativeLink('http://example.com'))
                .toEqual('example.com');
        });
    });

    describe('generateUuid', () => {
        it('should generate valid UUIDS', () => {
            global.crypto = {
                getRandomValues: nodeCrypto.randomFillSync
            };
            const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
            expect(UUID_REGEX.test(generateUuid())).toBe(true);
        });
    });

    describe('shouldPropertyBeHidden', () => {
        it('should never show @type', () => {
            expect(shouldPropertyBeHidden('@type', 'http://example.com')).toBe(true);
            expect(shouldPropertyBeHidden('@type', consts.FILE_URI)).toBe(true);
            expect(shouldPropertyBeHidden('@type', consts.DIRECTORY_URI)).toBe(true);
            expect(shouldPropertyBeHidden('@type', consts.COLLECTION_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.TYPE_URI, 'http://example.com')).toBe(true);
            expect(shouldPropertyBeHidden(consts.TYPE_URI, consts.FILE_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.TYPE_URI, consts.DIRECTORY_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.TYPE_URI, consts.COLLECTION_URI)).toBe(true);
        });

        it('should show comments for everything except to collections', () => {
            expect(shouldPropertyBeHidden(consts.COMMENT_URI, 'http://example.com')).toBe(false);
            expect(shouldPropertyBeHidden(consts.COMMENT_URI, consts.FILE_URI)).toBe(false);
            expect(shouldPropertyBeHidden(consts.COMMENT_URI, consts.DIRECTORY_URI)).toBe(false);
            expect(shouldPropertyBeHidden(consts.COMMENT_URI, consts.COLLECTION_URI)).toBe(true);
        });

        it('should not show labels for managed entities', () => {
            expect(shouldPropertyBeHidden(consts.LABEL_URI, 'http://example.com')).toBe(false);
            expect(shouldPropertyBeHidden(consts.LABEL_URI, consts.FILE_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.LABEL_URI, consts.DIRECTORY_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.LABEL_URI, consts.COLLECTION_URI)).toBe(true);
        });

        it('should never show fs:filePath', () => {
            expect(shouldPropertyBeHidden(consts.FILE_PATH_URI, 'http://example.com')).toBe(true);
            expect(shouldPropertyBeHidden(consts.FILE_PATH_URI, consts.FILE_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.FILE_PATH_URI, consts.DIRECTORY_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.FILE_PATH_URI, consts.COLLECTION_URI)).toBe(true);
        });

        it('should never show fs:dateDeleted', () => {
            expect(shouldPropertyBeHidden(consts.DATE_DELETED_URI, 'http://example.com')).toBe(true);
            expect(shouldPropertyBeHidden(consts.DATE_DELETED_URI, consts.FILE_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.DATE_DELETED_URI, consts.DIRECTORY_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.DATE_DELETED_URI, consts.COLLECTION_URI)).toBe(true);
        });

        it('should never show fs:deletedBy', () => {
            expect(shouldPropertyBeHidden(consts.DELETED_BY_URI, 'http://example.com')).toBe(true);
            expect(shouldPropertyBeHidden(consts.DELETED_BY_URI, consts.FILE_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.DELETED_BY_URI, consts.DIRECTORY_URI)).toBe(true);
            expect(shouldPropertyBeHidden(consts.DELETED_BY_URI, consts.COLLECTION_URI)).toBe(true);
        });

        it('should always show regular properties', () => {
            expect(shouldPropertyBeHidden('http://example.com/property', 'http://example.com')).toBe(false);
            expect(shouldPropertyBeHidden('http://example.com/property', consts.FILE_URI)).toBe(false);
            expect(shouldPropertyBeHidden('http://example.com/property', consts.DIRECTORY_URI)).toBe(false);
            expect(shouldPropertyBeHidden('http://example.com/property', consts.COLLECTION_URI)).toBe(false);
        });
    });


    describe('propertiesToShow', () => {
        it('should hide the type of an entity', () => {
            const properties = [{
                key: "@type",
                values: [{id: "http://fairspace.io/ontology#Collection", comment: "A specific collection in Fairspace."}]
            }, {
                key: "http://fairspace.io/ontology#createdBy",
                values: [{id: "http://fairspace.io/iri/6ae1ef15-ae67-4157-8fe2-79112f5a46fd"}]
            }, {
                key: "http://fairspace.io/ontology#dateCreated",
                values: [{value: "2019-03-18T13:06:22.62Z"}]
            }];

            const expected = [
                {
                    key: "http://fairspace.io/ontology#createdBy",
                    values: [{id: "http://fairspace.io/iri/6ae1ef15-ae67-4157-8fe2-79112f5a46fd"}]
                }, {
                    key: "http://fairspace.io/ontology#dateCreated",
                    values: [{value: "2019-03-18T13:06:22.62Z"}]
                }];

            expect(propertiesToShow(properties)).toEqual(expected);
        });
    });

    describe('toJsonLd', () => {
        it('should creates a valid json-ld (@value)', () => {
            const subject = "some-subject";
            const predicate = "some-predicate";
            const values = [{value: "some-value"}];

            const jsonLd = toJsonLd(subject, predicate, values);

            const expected = {
                "@id": "some-subject",
                "some-predicate": [{"@value": "some-value"}]
            };

            expect(jsonLd).toEqual(expected);
        });

        it('should creates a valid json-ld (@id)', () => {
            const subject = "some-subject";
            const predicate = "some-predicate";
            const values = [{id: "some-id"}];

            const jsonLd = toJsonLd(subject, predicate, values);

            const expected = {
                "@id": "some-subject",
                "some-predicate": [{"@id": "some-id"}]
            };

            expect(jsonLd).toEqual(expected);
        });

        it('null predicate', () => {
            const subject = "some-subject";
            const values = [{id: "some-id"}];
            const jsonLd = toJsonLd(subject, null, values);

            expect(jsonLd).toEqual(null);
        });

        it('null values', () => {
            const subject = "some-subject";
            const predicate = "some-predicate";
            const jsonLd = toJsonLd(subject, predicate, null);

            expect(jsonLd).toEqual(null);
        });

        it('empty values', () => {
            const subject = "some-subject";
            const predicate = "some-predicate";
            const jsonLd = toJsonLd(subject, predicate, []);

            const expected = {
                "@id": "some-subject",
                [predicate]: {'@id': consts.NIL_URI}
            };

            expect(jsonLd).toEqual(expected);
        });

        it('null subject', () => {
            const predicate = "some-predicate";
            const values = [{id: "some-id"}];
            const jsonLd = toJsonLd(null, predicate, values);

            expect(jsonLd).toEqual(null);
        });

        it('all null values', () => {
            const jsonLd = toJsonLd();

            expect(jsonLd).toEqual(null);
        });
    });

    describe('url2iri', () => {
        it('Handles URLS', () => {
            expect(url2iri('scheme://example.com:1234/some/path/?query')).toEqual('http://example.com/some/path/');
        });
    });

    describe('getTypeInfo', () => {
        const generateMetadataWithType = (typeData) => [{
            key: '@type',
            values: [{...typeData}]
        }];

        it('retrieves information on the type of the entity', () => {
            const metadata = generateMetadataWithType({
                label: 'some-label',
                comment: 'some-comment'
            });

            expect(getTypeInfo(metadata)).toEqual('some-label - some-comment');
        });

        it('ignores missing comment', () => {
            const metadata = generateMetadataWithType({
                label: 'some-label'
            });

            expect(getTypeInfo(metadata)).toEqual('some-label');
        });

        it('ignores missing label', () => {
            const metadata = generateMetadataWithType({
                comment: 'some-comment'
            });

            expect(getTypeInfo(metadata)).toEqual('some-comment');
        });

        it('returns undefined if type is not present', () => {
            const metadata = []

            expect(getTypeInfo(metadata)).toBeUndefined();
        });

    })
});
