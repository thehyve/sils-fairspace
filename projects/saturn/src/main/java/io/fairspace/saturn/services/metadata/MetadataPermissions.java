package io.fairspace.saturn.services.metadata;

import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.webdav.DavFactory;
import org.apache.jena.rdf.model.Resource;

public class MetadataPermissions {
    private final DavFactory davFactory;
    private final UserService userService;

    public MetadataPermissions(DavFactory davFactory, UserService userService) {
        this.davFactory = davFactory;
        this.userService = userService;
    }

    public boolean canReadMetadata(Resource resource) {
        if (userService.currentUser().isAdmin()) {
            return true;
        }
        if (davFactory.isFileSystemResource(resource)) {
            return davFactory.getAccess(resource).canList();
        }
        return userService.currentUser().isCanViewPublicMetadata();
    }

    public boolean canWriteMetadata(Resource resource) {
        if (userService.currentUser().isAdmin()) {
            return true;
        }
        if (davFactory.isFileSystemResource(resource)) {
            return davFactory.getAccess(resource).canWrite();
        }
        return userService.currentUser().isCanAddSharedMetadata();
    }
}
