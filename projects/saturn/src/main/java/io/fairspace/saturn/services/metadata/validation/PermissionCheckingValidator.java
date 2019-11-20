package io.fairspace.saturn.services.metadata.validation;

import io.fairspace.saturn.services.permissions.Access;
import io.fairspace.saturn.services.permissions.MetadataAccessDeniedException;
import io.fairspace.saturn.services.permissions.PermissionsService;
import lombok.AllArgsConstructor;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;

@AllArgsConstructor
public class PermissionCheckingValidator implements MetadataRequestValidator {
    private final PermissionsService permissions;

    @Override
    public void validate(Model before, Model after, Model removed, Model added, Model vocabulary, ViolationHandler violationHandler) {
        try {
            permissions.ensureAccess(removed
                            .listSubjects()
                            .andThen(added.listSubjects())
                            .filterKeep(Resource::isURIResource)
                            .mapWith(FrontsNode::asNode)
                            .toSet(),
                    Access.Write
            );
        } catch (MetadataAccessDeniedException e) {
            violationHandler.onViolation("Cannot modify read-only resource", createResource(e.getSubject().getURI()), null, null);
        }
    }
}
