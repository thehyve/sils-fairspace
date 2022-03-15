package io.fairspace.saturn.webdav;

import io.fairspace.saturn.vocabulary.FS;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.CollectionResource;
import io.milton.resource.MakeCollectionableResource;
import io.milton.resource.PropFindableResource;
import io.milton.resource.Resource;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.util.*;

import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static io.fairspace.saturn.webdav.DavFactory.childSubject;
import static io.fairspace.saturn.webdav.PathUtils.validateRootDirectoryName;
import static io.fairspace.saturn.webdav.WebDAVServlet.setErrorMessage;
import static io.fairspace.saturn.webdav.WebDAVServlet.timestampLiteral;

@Log4j2
class RootResource implements io.milton.resource.CollectionResource, MakeCollectionableResource, PropFindableResource {

    private final DavFactory factory;

    public RootResource(DavFactory factory) {
        this.factory = factory;
    }

    @Override
    public Resource child(String childName) throws NotAuthorizedException {
        return factory.getResource(childSubject(factory.rootSubject, childName));
    }

    private ResIterator getHierarchyRootClasses() {
        return VOCABULARY.listSubjectsWithProperty(FS.isHierarchyRoot);
    }

    @Override
    public List<? extends Resource> getChildren() {
        var hierarchyRootClasses = getHierarchyRootClasses().toSet();
        return factory.rootSubject.getModel().listSubjectsWithProperty(RDF.type, FS.Directory)
                .filterKeep(root -> hierarchyRootClasses.contains(root.getPropertyResourceValue(FS.linkedEntityType)))
                .mapWith(factory::getResource)
                .filterDrop(Objects::isNull)
                .toList();
    }

    public Optional<Resource> findRootDirectoryWithName(String name) {
        return factory.rootSubject.getModel().listSubjectsWithProperty(RDF.type, FS.Directory)
                .mapWith(child -> factory.getResourceByType(child, Access.List))
                .filterDrop(Objects::isNull)
                .filterKeep(collection -> collection.getName().equals(name))
                .nextOptional();
    }

    protected void validateTargetDirectoryName(String name) throws ConflictException, BadRequestException {
        validateRootDirectoryName(name);
        var existing = findRootDirectoryWithName(name);
        if (existing.isPresent()) {
            var message = "Target root directory with this name already exists.";
            setErrorMessage(message);
            throw new ConflictException(existing.get(), message);
        }
    }

    private void validateLinkedEntityType(org.apache.jena.rdf.model.Resource type) throws BadRequestException {
        var hasValidType = getHierarchyRootClasses()
                .filterKeep(root -> root.getURI().equals(type.getURI()))
                .hasNext();
        if (!hasValidType) {
            var message = "The provided linked entity type is invalid: " + type.getURI();
            setErrorMessage(message);
            throw new BadRequestException(message);
        }
    }

    /**
     * Creates a new root directory.
     * Returns null if a root directory already exists with the same name (modulo case),
     * which is interpreted as a failure by {@link io.milton.http.webdav.MkColHandler},
     * resulting in a 405 (Method Not Allowed) response.
     *
     * @param name the root directory name, which needs to be unique.
     *
     * @return the directory resource if it was successfully created; null if
     *         a directory with the label already exists (ignoring case);
     * @throws ConflictException if the IRI is already is use by a resource that is not deleted.
     * @throws BadRequestException if the name is invalid (@see {@link #validateTargetDirectoryName(String)}).
     */
    @Override
    public io.milton.resource.CollectionResource createCollection(String name) throws ConflictException, BadRequestException {
        if (name != null) {
            name = name.trim();
        }
        validateTargetDirectoryName(name);
        var type = factory.getLinkedEntityType();
        validateLinkedEntityType(type);

        var subj = childSubject(factory.rootSubject, name);
        if (subj.hasProperty(RDF.type) && !subj.hasProperty(FS.dateDeleted)) {
            var message = "Cannot create a root directory with this name.";
            setErrorMessage(message);
            throw new ConflictException(message);
        }

        subj.getModel().removeAll(subj, null, null).removeAll(null, null, subj);
        var user = factory.currentUserResource();

        subj.addProperty(RDF.type, FS.Directory)
                .addProperty(RDFS.label, name)
                .addProperty(RDFS.comment, "")
                .addProperty(FS.createdBy, user)
                .addProperty(FS.dateCreated, timestampLiteral())
                .addProperty(FS.dateModified, timestampLiteral())
                .addProperty(FS.modifiedBy, user);

        factory.addLinkedEntity(name, subj, type);

        return (CollectionResource) factory.getResource(subj, Access.Manage);
    }

    @Override
    public String getUniqueId() {
        return factory.rootSubject.getURI();
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public Object authenticate(String user, String password) {
        return null;
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return true;
    }

    @Override
    public String getRealm() {
        return null;
    }

    @Override
    public Date getModifiedDate() {
        return null;
    }

    @Override
    public String checkRedirect(Request request) {
        return null;
    }

    @Override
    public Date getCreateDate() {
        return null;
    }
}
