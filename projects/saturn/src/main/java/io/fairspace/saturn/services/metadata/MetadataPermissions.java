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

    private boolean isAdminEditOnlyResource(String type) {
        var resourceClass = vocabulary.getResource(type);
        return getBooleanProperty(resourceClass, FS.adminEditOnly);
    }

    public boolean canReadMetadata() {
        return true;
    }

    public boolean canWriteMetadata(Resource resourceType) {
        return canWriteMetadataByUri(resourceType.getURI());
    }

    public boolean canWriteMetadataByUri(String typeUri) {
        return userService.currentUser().isAdmin() || (typeUri != null && !isAdminEditOnlyResource(typeUri));
    }
}
