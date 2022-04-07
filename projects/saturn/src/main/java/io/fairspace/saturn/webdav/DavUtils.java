package io.fairspace.saturn.webdav;

import io.fairspace.saturn.vocabulary.FS;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.CollectionResource;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;

import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static io.fairspace.saturn.webdav.PathUtils.MAX_ROOT_DIRECTORY_NAME_LENGTH;
import static io.fairspace.saturn.webdav.PathUtils.encodePath;
import static io.fairspace.saturn.webdav.WebDAVServlet.setErrorMessage;

public final class DavUtils {

    public static ResIterator getHierarchyRootClasses() {
        return VOCABULARY.listSubjectsWithProperty(FS.isHierarchyRoot);
    }

    public static ResIterator getHierarchyClasses() {
        return VOCABULARY.listSubjectsWithProperty(FS.isPartOfHierarchy);
    }

    public static org.apache.jena.rdf.model.Resource childSubject(org.apache.jena.rdf.model.Resource subject, String name) {
        return subject.getModel().getResource(subject.getURI() + "/" + encodePath(name));
    }

    public static void validateIfTypeIsValidForParent(org.apache.jena.rdf.model.Resource type, org.apache.jena.rdf.model.Resource parentType)
            throws BadRequestException {
        String errorMessage = "";
        if (parentType == null) {
            if (!VOCABULARY.listSubjectsWithProperty(FS.isHierarchyRoot)
                .mapWith(RDFNode::asResource)
                .filterKeep(resource -> resource.equals(type))
                .hasNext()) {
                errorMessage = "The provided linked entity type: " + type.getURI() + " is not a valid root type.";
            }
        } else {
            if (!VOCABULARY.listSubjectsWithProperty(FS.isPartOfHierarchy)
                    .filterKeep(shape -> shape.getURI().equals(parentType.getURI()))
                    .filterKeep(res -> res.getProperty(FS.hierarchyDescendants)
                            .getList()
                            .mapWith(RDFNode::asResource)
                            .filterKeep(resource -> resource.equals(type))
                            .hasNext())
                    .hasNext()) {
                errorMessage = "The provided linked entity type: " + type.getURI() + " is invalid for parent type " + parentType;
            }
        }
        if (!errorMessage.isEmpty()) {
            setErrorMessage(errorMessage);
            throw new BadRequestException(errorMessage);
        }
    }

    public static void validateLinkedEntityType(org.apache.jena.rdf.model.Resource type) throws BadRequestException {
        var hasValidType = getHierarchyClasses()
                .filterKeep(hierarchyRoot -> hierarchyRoot.getURI().equals(type.getURI()))
                .hasNext();
        if (!hasValidType) {
            var message = "The provided linked entity type is invalid: " + type.getURI();
            setErrorMessage(message);
            throw new BadRequestException(message);
        }
    }

    public static void validateResourceName(String name) throws BadRequestException {
        if (name == null || name.isBlank()) {
            var message = "The directory name is empty.";
            setErrorMessage(message);
            throw new BadRequestException(message);
        }
        if (name.contains("\\")) {
            var message = "The directory name contains an illegal character (\\)";
            setErrorMessage(message);
            throw new BadRequestException(message);
        }
    }

    public static void validateRootDirectoryName(String name) throws BadRequestException {
        validateResourceName(name);
        if (name.length() > MAX_ROOT_DIRECTORY_NAME_LENGTH) {
            var message = "The directory name exceeds maximum length " + MAX_ROOT_DIRECTORY_NAME_LENGTH + ".";
            setErrorMessage(message);
            throw new BadRequestException(message);
        }
    }

    public static void validateResourceDoesNotExist(CollectionResource resource, String name) throws NotAuthorizedException, BadRequestException, ConflictException {
        var existing = resource.child(name);
        if (existing != null) {
            var message = "Target directory with this name already exists.";
            setErrorMessage(message);
            throw new ConflictException(existing, message);
        }
    }
}
