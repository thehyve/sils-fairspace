package io.fairspace.saturn.webdav;

import io.fairspace.saturn.vocabulary.FS;
import io.milton.http.Auth;
import io.milton.http.FileItem;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.property.PropertySource;
import io.milton.property.PropertySource.PropertyMetaData;
import io.milton.property.PropertySource.PropertySetException;
import io.milton.resource.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import javax.xml.namespace.QName;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;

import static io.fairspace.saturn.audit.Audit.audit;
import static io.fairspace.saturn.auth.RequestContext.getUserURI;
import static io.fairspace.saturn.rdf.ModelUtils.*;
import static io.fairspace.saturn.rdf.SparqlUtils.parseXSDDateTimeLiteral;
import static io.fairspace.saturn.vocabulary.Vocabularies.USER_VOCABULARY;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static io.fairspace.saturn.webdav.DavFactory.childSubject;
import static io.fairspace.saturn.webdav.WebDAVServlet.*;
import static io.milton.http.ResponseStatus.SC_FORBIDDEN;
import static io.milton.property.PropertySource.PropertyAccessibility.READ_ONLY;
import static io.milton.property.PropertySource.PropertyAccessibility.WRITABLE;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.beanutils.PropertyUtils.getPropertyDescriptor;
import static org.apache.commons.beanutils.PropertyUtils.getPropertyDescriptors;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;

abstract class BaseResource implements PropFindableResource, DeletableResource, MoveableResource, CopyableResource, MultiNamespaceCustomPropertyResource, PostableResource {
    protected final DavFactory factory;
    protected final Resource subject;
    protected final Access access;

    BaseResource(DavFactory factory, Resource subject, Access access) {
        this.factory = factory;
        this.subject = subject;
        this.access = access;
    }

    @Property
    public String getLinkedEntityType() {
        return Optional
                .ofNullable(subject.getPropertyResourceValue(FS.linkedEntityType))
                .map(Resource::toString)
                .orElse(null);
    }

    @Property
    public String getLinkedEntityIri() {
        return Optional
                .ofNullable(subject.getPropertyResourceValue(FS.linkedEntity))
                .map(Resource::toString)
                .orElse(null);
    }

    @Override
    public String getUniqueId() {
        return subject.getURI();
    }

    String getRelativePath() {
        return getUniqueId().substring(factory.root.getUniqueId().length());
    }

    @Override
    public String getName() {
        return subject.getRequiredProperty(RDFS.label).getString();
    }

    @Override
    public Object authenticate(String user, String password) {
        return null;
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        // TODO: Authorization not implemented yet, temporary allow everything.
        return true;
    }

    @Override
    public String getRealm() {
        return null;
    }

    @Override
    public String checkRedirect(Request request) throws NotAuthorizedException, BadRequestException {
        return null;
    }

    @Override
    public Date getCreateDate() {
        return parseDate(subject, FS.dateCreated);
    }

    @Override
    public final void delete() throws NotAuthorizedException, ConflictException, BadRequestException {
        var purge = subject.hasProperty(FS.dateDeleted);
        delete(purge);
        updateParents(subject);
        if (purge) {
            audit("FS_DELETE", "path", getRelativePath(), "success", true);
        } else {
            audit("FS_MARK_AS_DELETED", "path", getRelativePath(), "success", true);
        }
    }

    protected void delete(boolean purge) throws NotAuthorizedException, ConflictException, BadRequestException {
        if (purge) {
            if (!factory.userService.currentUser().isAdmin()) {
                var message = "Not authorized to purge the resource.";
                setErrorMessage(message);
                throw new NotAuthorizedException(message, this, SC_FORBIDDEN);
            }
            subject.getModel().removeAll(subject, null, null).removeAll(null, null, subject);
        } else if (!subject.hasProperty(FS.dateDeleted)) {
            subject.addProperty(FS.dateDeleted, timestampLiteral())
                    .addProperty(FS.deletedBy, factory.currentUserResource());
        }
    }

    private void validateTarget(io.milton.resource.CollectionResource parent, String name)
            throws BadRequestException, ConflictException, NotAuthorizedException {
        if (name == null || name.isEmpty()) {
            var message = "The name is empty.";
            setErrorMessage(message);
            throw new BadRequestException(message);
        }
        if (name.contains("\\")) {
            var message = "The name contains an illegal character (\\)";
            setErrorMessage(message);
            throw new BadRequestException(message);
        }
        if (parent != null && parent.child(name) != null) {
            var message = "Resource with the name " + name + " already exist in the specified directory.";
            setErrorMessage(message);
            throw new ConflictException(message);
        }
    }

    private void renameLinkedEntity(String name) {
        subject.getPropertyResourceValue(FS.linkedEntity)
                .removeAll(RDFS.label)
                .addProperty(RDFS.label, name);
    }

    private void moveResourcesWithTheSameLinkedEntity(String name) {
        subject.getModel()
                .listStatements(null, FS.linkedEntity, subject.getPropertyResourceValue(FS.linkedEntity))
                .filterDrop(statement -> statement.getSubject().equals(subject))
                .forEachRemaining(statement ->
                        move(statement.getSubject(), statement.getSubject().getPropertyResourceValue(FS.belongsTo), name, true)
                );
    }

    @Override
    public void moveTo(io.milton.resource.CollectionResource parent, String name)
            throws ConflictException, NotAuthorizedException, BadRequestException {
        if (name != null) {
            name = name.trim();
        }
        validateTarget(parent, name);

        var parentSubject = (parent instanceof DirectoryResource) ? ((DirectoryResource) parent).subject : null;
        var parentType = Optional.ofNullable(parentSubject).map(p -> p.getPropertyResourceValue(FS.linkedEntityType)).orElse(null);
        var type = subject.getPropertyResourceValue(FS.linkedEntityType);
        validateIfTypeIsValidForParent(type, parentType);

        renameLinkedEntity(name);
        moveResourcesWithTheSameLinkedEntity(name);

        move(subject, parentSubject, name, true);
    }

    private void move(Resource subject, Resource parent, String name, boolean isTop) {
        var newSubject = childSubject(parent != null ? parent : factory.rootSubject, name);
        newSubject.removeProperties().addProperty(RDFS.label, name);
        if (parent != null) {
            newSubject.addProperty(FS.belongsTo, parent);
        }

        subject.listProperties()
                .filterDrop(stmt -> stmt.getPredicate().equals(RDFS.label))
                .filterDrop(stmt -> stmt.getPredicate().equals(FS.belongsTo))
                .filterDrop(stmt -> stmt.getPredicate().equals(FS.versions))
                .toSet()  // convert to set, to prevent updating a model while iterating over its elements
                .forEach(stmt -> newSubject.addProperty(stmt.getPredicate(), stmt.getObject()));

        var versions = getListProperty(subject, FS.versions);

        if (versions != null) {
            var newVersions = subject.getModel().createList(versions.iterator()
                    .mapWith(RDFNode::asResource)
                    .mapWith(BaseResource::copyVersion));
            newSubject.addProperty(FS.versions, newVersions);
        }

        subject.getModel().listSubjectsWithProperty(FS.belongsTo, subject)
                .toSet()  // convert to set, to prevent updating a model while iterating over its elements
                .forEach(r -> move(r, newSubject, getStringProperty(r, RDFS.label), false));

        subject.getModel().listStatements(null, null, subject)
                .filterDrop(stmt -> stmt.getPredicate().equals(FS.belongsTo))
                .toSet()  // convert to set, to prevent updating a model while iterating over its elements
                .forEach(stmt -> stmt.getSubject().addProperty(stmt.getPredicate(), newSubject));

        subject.getModel().removeAll(subject, null, null).removeAll(null, null, subject);

        subject.addProperty(FS.movedTo, newSubject);

        if (isTop) {
            updateParents(subject);
            updateParents(newSubject);
        }
    }

    private static Resource copyVersion(Resource ver) {
        var newVer = ver.getModel().createResource();
        copyProperties(ver.asResource(), newVer, RDF.type, FS.dateModified, FS.deletedBy, FS.fileSize, FS.blobId, FS.md5);
        return newVer;
    }

    protected void validateIfTypeIsValidForParent(org.apache.jena.rdf.model.Resource type, org.apache.jena.rdf.model.Resource parentType) throws BadRequestException {
        var validTypeURIs = new ArrayList<String>();
        if (parentType == null) {
            VOCABULARY.listSubjectsWithProperty(FS.isHierarchyRoot)
                    .mapWith(RDFNode::asResource)
                    .mapWith(org.apache.jena.rdf.model.Resource::getURI)
                    .forEach(validTypeURIs::add);
        } else {
            VOCABULARY.listSubjectsWithProperty(FS.isPartOfHierarchy)
                    .filterKeep(shape -> shape.getURI().equals(parentType.getURI()))
                    .forEachRemaining(res -> res.getProperty(FS.hierarchyDescendants)
                            .getList()
                            .mapWith(RDFNode::asResource)
                            .mapWith(org.apache.jena.rdf.model.Resource::getURI)
                            .forEach(validTypeURIs::add));
        }
        if (!validTypeURIs.contains(type.getURI())) {
            var message = "The provided linked entity type is invalid: " + type.getURI();
            setErrorMessage(message);
            throw new BadRequestException(message);
        }
    }

    @Override
    public void copyTo(io.milton.resource.CollectionResource parent, String name)
            throws NotAuthorizedException, BadRequestException, ConflictException {
        if (name != null) {
            name = name.trim();
        }
        validateTarget(parent, name);
        var parentSubject = parent instanceof DirectoryResource ? ((DirectoryResource) parent).subject : factory.rootSubject;

        var parentType = parentSubject.getPropertyResourceValue(FS.linkedEntityType);
        var type = subject.getPropertyResourceValue(FS.linkedEntityType);
        validateIfTypeIsValidForParent(type, parentType);

        copy(subject, parentSubject, name, factory.currentUserResource(), timestampLiteral());
    }

    private void copy(Resource subject, Resource parent, String name, Resource user, Literal date) {
        var newSubject = childSubject(parent, name);
        newSubject.removeProperties();
        newSubject.addProperty(FS.belongsTo, parent);
        newSubject.addProperty(RDFS.label, name)
                .addProperty(FS.dateCreated, date)
                .addProperty(FS.createdBy, user);

        copyProperties(subject, newSubject, RDF.type, FS.contentType, FS.linkedEntity, FS.linkedEntityType);

        if (subject.hasProperty(FS.versions)) {
            var src = getListProperty(subject, FS.versions).getHead().asResource();

            var ver = newSubject.getModel().createResource()
                    .addProperty(RDF.type, FS.FileVersion)
                    .addProperty(FS.modifiedBy, user)
                    .addProperty(FS.dateModified, date);

            copyProperties(src, ver, FS.blobId, FS.fileSize, FS.md5);

            newSubject.addLiteral(FS.currentVersion, 1)
                    .addProperty(FS.versions, newSubject.getModel().createList(ver));
        }
        subject.getModel()
                .listSubjectsWithProperty(FS.belongsTo, subject)
                .toSet()  // convert to set, to prevent updating a model while iterating over its elements
                .forEach(r -> copy(r, newSubject, getStringProperty(r, RDFS.label), user, date));

        updateParents(subject);
        updateParents(newSubject);
    }

    @Override
    public List<QName> getAllPropertyNames() {
        return Stream.of(getPropertyDescriptors(getClass()))
                .filter(p -> p.getReadMethod().isAnnotationPresent(Property.class))
                .map(p -> new QName(FS.NS, p.getName()))
                .collect(toList());
    }

    @Override
    public Object getProperty(QName name) {
        try {
            return getPropertyDescriptor(this, name.getLocalPart())
                    .getReadMethod()
                    .invoke(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setProperty(QName name, Object value) throws PropertySetException, NotAuthorizedException {
        try {
            getPropertyDescriptor(this, name.getLocalPart())
                    .getWriteMethod()
                    .invoke(this, value);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof PropertySetException) {
                throw (PropertySetException) e.getTargetException();
            }
            if (e.getTargetException() instanceof NotAuthorizedException) {
                throw (NotAuthorizedException) e.getTargetException();
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public PropertySource.PropertyMetaData getPropertyMetaData(QName name) {
        try {
            var pd = getPropertyDescriptor(this, name.getLocalPart());
            if (pd != null) {
                return new PropertyMetaData(pd.getWriteMethod() != null ? WRITABLE : READ_ONLY, pd.getPropertyType());
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    @Property
    public String getIri() {
        return subject.getURI();
    }

    @Property
    public Date getDateDeleted() {
        return parseDate(subject, FS.dateDeleted);
    }

    @Property
    public String getMetadataLinks() {
        if(includeMetadataLinks()) {
            return String.join(",", metadataLinks());
        }
        return null;
    }

    public Set<String> metadataLinks() {
        var userVocabularyPaths = USER_VOCABULARY.listStatements()
                .filterKeep(stmt -> stmt.getObject().isResource() && stmt.getPredicate().getURI().equals(SHACL.path.getURI()))
                .mapWith(stmt -> stmt.getObject().asResource().getURI())
                .toSet();
        return subject.listProperties()
                .filterKeep(stmt -> stmt.getObject().isResource() && userVocabularyPaths.contains(stmt.getPredicate().getURI()))
                .mapWith(Statement::getResource)
                .mapWith(Resource::getURI)
                .toSet();
    }

    @Property
    public String getDeletedBy() {
        var deletedBy = subject.getPropertyResourceValue(FS.deletedBy);
        if (deletedBy != null) {
            return deletedBy.getURI();
        }
        return null;
    }

    @Override
    public String toString() {
        return subject.getURI().substring(factory.rootSubject.getURI().length());
    }

    protected Resource newVersion(BlobInfo blob) {
        updateParents(subject);
        return subject.getModel()
                .createResource()
                .addProperty(RDF.type, FS.FileVersion)
                .addProperty(FS.blobId, blob.id)
                .addLiteral(FS.fileSize, blob.size)
                .addProperty(FS.md5, blob.md5)
                .addProperty(FS.dateModified, timestampLiteral())
                .addProperty(FS.modifiedBy, factory.currentUserResource());
    }

    protected static void updateParents(Resource subject) {
        var now = timestampLiteral();
        for (var s = subject.getPropertyResourceValue(FS.belongsTo);
             s != null && !s.hasProperty(RDF.type, FS.Workspace);
             s = s.getPropertyResourceValue(FS.belongsTo)) {
            s.removeAll(FS.dateModified)
                    .removeAll(FS.modifiedBy)
                    .addProperty(FS.dateModified, now)
                    .addProperty(FS.modifiedBy, createResource(getUserURI().getURI()));
        }
    }

    protected static Date parseDate(Resource s, org.apache.jena.rdf.model.Property p) {
        if (!s.hasProperty(p)) {
            return null;
        }
        return Date.from(parseXSDDateTimeLiteral(s.getProperty(p).getLiteral()));
    }

    @Override
    public String processForm(Map<String, String> parameters, Map<String, FileItem> files) throws BadRequestException, NotAuthorizedException, ConflictException {
        var action = parameters.get("action");
        if (action == null) {
            var message = "No action specified";
            setErrorMessage(message);
            throw new BadRequestException(this, message);
        }
        performAction(action, parameters, files);
        return null;
    }

    protected void performAction(String action, Map<String, String> parameters, Map<String, FileItem> files) throws BadRequestException, NotAuthorizedException, ConflictException {
        switch (action) {
            case "undelete" -> undelete();
            default -> {
                var message = "Unrecognized action " + action;
                setErrorMessage(message);
                throw new BadRequestException(this, message);
            }
        }
    }

    protected boolean canUndelete() {
        return access.canWrite();
    }

    protected void undelete() throws BadRequestException, NotAuthorizedException, ConflictException {
        if (!canUndelete()) {
            var message = "Not authorized to undelete this resource.";
            setErrorMessage(message);
            throw new NotAuthorizedException(message, this, SC_FORBIDDEN);
        }
        if (!subject.hasProperty(FS.dateDeleted)) {
            var message = "Cannot restore resource that is not marked as deleted.";
            setErrorMessage(message);
            throw new ConflictException(this, message);
        }
        var date = subject.getProperty(FS.dateDeleted).getLiteral();
        var user = subject.getProperty(FS.deletedBy).getResource();
        undelete(subject, date, user);
        updateParents(subject);
    }

    private void undelete(Resource resource, Literal date, Resource user) {
        if (resource.hasProperty(FS.deletedBy, user) && resource.hasProperty(FS.dateDeleted, date)) {
            resource.removeAll(FS.dateDeleted).removeAll(FS.deletedBy);

            resource.getModel()
                    .listSubjectsWithProperty(FS.belongsTo, resource)
                    .toSet()  // convert to set, to prevent updating a model while iterating over its elements
                    .forEach(r -> undelete(r, date, user));
        }
    }
}
