package io.fairspace.saturn.vocabulary;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;

public class FS {
    public static final String NS = "https://fairspace.nl/ontology#";

    public static final String DIRECTORY_URI = NS + "Directory";
    public static final Resource Directory = createResource(DIRECTORY_URI);

    public static final String FILE_URI = NS + "File";
    public static final Resource File = createResource(FILE_URI);

    public static final String CURRENT_VERSION_URI = NS + "currentVersion";
    public static final Property currentVersion = createProperty(CURRENT_VERSION_URI);

    public static final String VERSIONS_URI = NS + "versions";
    public static final Property versions = createProperty(VERSIONS_URI);

    public static final String FILE_VERSION_URI = NS + "FileVersion";
    public static final Resource FileVersion = createResource(FILE_VERSION_URI);

    public static final String USER_URI = NS + "User";
    public static final Resource User = createResource(USER_URI);

    public static final String BELONGS_TO_URI = NS + "belongsTo";
    public static final Property belongsTo = createProperty(BELONGS_TO_URI);

    public static final String CREATED_BY_URI = NS + "createdBy";
    public static final Property createdBy = createProperty(CREATED_BY_URI);

    public static final String DATE_CREATED_URI = NS + "dateCreated";
    public static final Property dateCreated = createProperty(DATE_CREATED_URI);

    public static final String MODIFIED_BY_LOCAL_PART = "modifiedBy";
    public static final String MODIFIED_BY_URI = NS + MODIFIED_BY_LOCAL_PART;
    public static final Property modifiedBy = createProperty(MODIFIED_BY_URI);

    public static final String DATE_MODIFIED_URI = NS + "dateModified";
    public static final Property dateModified = createProperty(DATE_MODIFIED_URI);

    public static final String DELETED_BY_URI = NS + "deletedBy";
    public static final Property deletedBy = createProperty(DELETED_BY_URI);

    public static final String DATE_DELETED_URI = NS + "dateDeleted";
    public static final Property dateDeleted = createProperty(DATE_DELETED_URI);

    public static final String STATUS_URI = NS + "status";
    public static final Property status = createProperty(STATUS_URI);

    public static final String MOVED_TO_URI = NS + "movedTo";
    public static final Property movedTo = createProperty(MOVED_TO_URI);

    public static final String ID_URI = NS + "id";
    public static final Property id = createProperty(ID_URI);

    public static final String EMAIL_URI = NS + "email";
    public static final Property email = createProperty(EMAIL_URI);

    public static final String USERNAME_URI = NS + "username";
    public static final Property username = createProperty(USERNAME_URI);

    public static final String HAS_ROLE_URI = NS + "hasRole";
    public static final Property hasRole = createProperty(HAS_ROLE_URI);

    public static final String FILE_SIZE_URI = NS + "fileSize";
    public static final Property fileSize = createProperty(FILE_SIZE_URI);

    public static final String BLOB_ID_URI = NS + "blobId";
    public static final Property blobId = createProperty(BLOB_ID_URI);

    public static final String CONTENT_TYPE_URI = NS + "contentType";
    public static final Property contentType = createProperty(CONTENT_TYPE_URI);

    public static final String MD5_URI = NS + "md5";
    public static final Property md5 = createProperty(MD5_URI);


    public static final String CONNECTION_STRING_URI = NS + "connectionString";
    public static final Property connectionString = createProperty(CONNECTION_STRING_URI);

    public static final String MACHINE_ONLY_URI = NS + "machineOnly";
    public static final Property machineOnly = createProperty(MACHINE_ONLY_URI);

    public static final String ADMIN_EDIT_ONLY_URI = NS + "adminEditOnly";
    public static final Property adminEditOnly = createProperty(ADMIN_EDIT_ONLY_URI);

    public static final String LINKED_ENTITY_TYPE_URI = NS + "linkedEntityType";
    public static final Property linkedEntityType = createProperty(LINKED_ENTITY_TYPE_URI);
    public static final String LINKED_ENTITY_URI = NS + "linkedEntity";
    public static final Property linkedEntity = createProperty(LINKED_ENTITY_URI);
    public static final String REPRESENTS_EXTERNAL_FILE = NS + "representsExternalFile";
    public static final Property representsExternalFile = createProperty(REPRESENTS_EXTERNAL_FILE);

    public static final String IS_HIERARCHY_ROOT_URI = NS + "hierarchyRoot";
    public static final Property isHierarchyRoot = createProperty(IS_HIERARCHY_ROOT_URI);
    public static final String HIERARCHY_DESCENDANTS_URI = NS + "hierarchyDescendants";
    public static final Property hierarchyDescendants = createProperty(HIERARCHY_DESCENDANTS_URI);
    public static final String IS_PART_OF_HIERARCHY_URI = NS + "partOfHierarchy";
    public static final Property isPartOfHierarchy = createProperty(IS_PART_OF_HIERARCHY_URI);

    public static final String IMPORTANT_PROPERTY_URI = NS + "importantProperty";
    public static final Property importantProperty = createProperty(IMPORTANT_PROPERTY_URI);

    public static final String ERROR_URI = NS + "error";
    public static final String ERROR_STATUS_URI = NS + "errorStatus";
    public static final String ERROR_MESSAGE_URI = NS + "errorMessage";
    public static final String ERROR_DETAILS_URI = NS + "errorDetails";
    public static final String NODE_URL_URI = NS + "nodeUrl";
    public static final Property nodeUrl = createProperty(NODE_URL_URI);

    public static final String MARKDOWN_URI = NS + "markdown";
    public static final Property markdown = createProperty(MARKDOWN_URI);

    public static final String NIL_URI = NS + "nil";
    public static final Property nil = createProperty(NIL_URI);

    public static final String IS_ADMIN_URI = NS + "isAdmin";
    public static final String IS_SUPERADMIN_URI = NS + "isSuperadmin";
}

