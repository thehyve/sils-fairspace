package io.fairspace.saturn.services.metadata;

import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vocabulary.FS;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import static io.fairspace.saturn.rdf.ModelUtils.getBooleanProperty;

public class MetadataPermissions {
    private final UserService userService;
    private final Model vocabulary;

    public MetadataPermissions(UserService userService, Model vocabulary) {
        this.userService = userService;
        this.vocabulary = vocabulary;
    }

    private boolean isAdminEditOnlyResource(org.apache.jena.rdf.model.Resource type) {
        var resourceClass = vocabulary.getResource(type.getURI());
        return getBooleanProperty(resourceClass, FS.adminEditOnly);
    }

    public boolean canReadMetadata() {
        return true;
    }

    public boolean canWriteMetadata(Resource resourceType) {
        return userService.currentUser().isAdmin() || (resourceType != null && !isAdminEditOnlyResource(resourceType));
    }
}
