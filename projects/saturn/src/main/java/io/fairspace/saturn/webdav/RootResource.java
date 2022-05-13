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
import org.apache.jena.vocabulary.RDF;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import static io.fairspace.saturn.webdav.DavUtils.*;
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

    @Override
    public List<? extends Resource> getChildren() {
        var hierarchyRootClasses = getHierarchyRootClasses().toSet();
        return factory.rootSubject.getModel().listSubjectsWithProperty(RDF.type, FS.Directory)
                .filterKeep(root -> hierarchyRootClasses.contains(root.getPropertyResourceValue(FS.linkedEntityType)))
                .mapWith(factory::getResource)
                .filterDrop(Objects::isNull)
                .toList();
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
     * @throws BadRequestException if the name is invalid (@see {@link DavUtils#validateRootDirectoryName(String)}).
     */
    @Override
    public io.milton.resource.CollectionResource createCollection(String name) throws ConflictException, BadRequestException, NotAuthorizedException {
        validatePermission();
        validateRootDirectoryName(name);
        name = name.trim();
        factory.validateChildNameUniqueness(factory.rootSubject, name);

        var subj = factory.createDavResource(name, factory.rootSubject)
                .addProperty(RDF.type, FS.Directory)
                .addProperty(FS.dateModified, timestampLiteral())
                .addProperty(FS.modifiedBy, factory.currentUserResource());

        factory.linkEntityToSubject(subj);

        return (CollectionResource) factory.getResource(subj, Access.Manage);
    }

    private void validatePermission() throws NotAuthorizedException {
        var iter = DavUtils.getHierarchyRootClasses();
        while(iter.hasNext()) {
            var resource = iter.nextResource();
                factory.validateAuthorization(resource.getURI());
        }
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
