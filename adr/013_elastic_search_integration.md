# Integration with ElasticSearch

* **Status**: proposed

* **Context**: From the business requirements it follows that we need to be able 
to perform full-text search on metadata. Apache Jena already has an API allowing for
integration with ES and automatic indexing of all metadata.
Indexing is configured by specifying mapping between RDF predicate URIs and ES fields. 
Properties not included in the mapping are not indexed, but new mappings can be in principle added in run-time.
Jena creates one index for everything and doesn't make any distinction between entities belonging to different RDF classes. 
Of course, rdf:type can be indexed as well.
The index doesn't support nested documents, but one property can have multiple values.


* **Decision**: 
  * Add a Helm chart for ElasticSearch to the workspace and make it exposed for read-only access
  * Use `jena-text` and `jena-text-es` libraries for integration
  * Use dynamic mapping between property URIs and field names, based on the vocabulary. 
  * Use [ElasticSearch JavaScript API](https://www.elastic.co/guide/en/elasticsearch/client/javascript-api/current/index.html) in the front-end
  * Access to the ElasticSearch will be guarded by Pluto and can be restricted to read-only mode by allowing only GET HTTP requests. According to the business requiremnets, we don't need fine-grained permissions model for search.

* **Consequences**: 
  * The front-end will not depend on SPARQL
  * Reference properties will be indexed using URIs, not labels. The user will have to first search for a referenced entity.
  * Changes in the vocabulary can make previously indexed fields useless. 
  * Triple updates are sent to ES synchronously. This means the client has to wait for it, and client calls may fail when data is stored in Jena but not in ES.
  * The business logic drives which triples should be indexed or not. This business logic will be integrated with the database layer with this implementation.
