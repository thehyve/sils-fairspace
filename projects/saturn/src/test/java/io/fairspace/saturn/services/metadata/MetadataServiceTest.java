package io.fairspace.saturn.services.metadata;

import io.fairspace.saturn.config.ConfigLoader;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.AccessDeniedException;
import io.fairspace.saturn.services.metadata.validation.ComposedValidator;
import io.fairspace.saturn.services.metadata.validation.DeletionValidator;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vocabulary.FS;
import io.fairspace.saturn.webdav.BlobStore;
import io.fairspace.saturn.webdav.DavFactory;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.MakeCollectionableResource;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.eclipse.jetty.server.Authentication;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.fairspace.saturn.TestUtils.*;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;
import static io.fairspace.saturn.config.Services.METADATA_SERVICE;
import static io.fairspace.saturn.rdf.ModelUtils.modelOf;
import static io.fairspace.saturn.vocabulary.FS.NS;
import static io.fairspace.saturn.vocabulary.Vocabularies.SYSTEM_VOCABULARY;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static org.apache.jena.query.DatasetFactory.createTxnMem;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.apache.jena.rdf.model.ResourceFactory.*;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.junit.Assert.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MetadataServiceTest {
    private static final String BASE_PATH = "/api/webdav";
    private static final String baseUri = ConfigLoader.CONFIG.publicUrl + BASE_PATH;
    private static final Resource S1 = createResource("http://localhost/iri/S1");
    private static final Resource S2 = createResource("http://localhost/iri/S2");
    private static final Resource S3 = createResource("http://localhost/iri/S3");
    private static final Property P1 = createProperty("https://fairspace.nl/ontology/P1");
    private static final Property P2 = createProperty("https://fairspace.nl/ontology/P2");
    private static final Property class1 = createProperty("https://fairspace.nl/ontology/C1");
    private static final Property class2 = createProperty("https://fairspace.nl/ontology/C2");

    private static final Statement STMT1 = createStatement(S1, P1, S2);
    private static final Statement STMT2 = createStatement(S2, P1, S3);

    private Dataset ds = createTxnMem();

    private Transactions txn = new SimpleTransactions(ds);
    private MetadataService api;

    private DavFactory davFactory;

    private MetadataPermissions permissions;
    Model model;
    @Mock
    BlobStore store;
    @Mock
    UserService userService;
    private org.eclipse.jetty.server.Request request;

    User admin;
    Authentication.User adminAuthentication;
    User user;
    Authentication.User userAuthentication;

    private void selectAdmin() {
        lenient().when(request.getAuthentication()).thenReturn(adminAuthentication);
        lenient().when(userService.currentUser()).thenReturn(admin);
    }
    private void selectRegularUser() {
        lenient().when(request.getAuthentication()).thenReturn(userAuthentication);
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    @Before
    public void setUp() {
        setupRequestContext();
        request = getCurrentRequest();
        permissions = new MetadataPermissions(userService, VOCABULARY.add(class2, FS.adminEditOnly, createTypedLiteral(true)));
        var context = new Context();
        model = ds.getDefaultModel();
        davFactory = new DavFactory(model.createResource(baseUri), store, userService, context);
        api = new MetadataService(txn, SYSTEM_VOCABULARY, new ComposedValidator(new DeletionValidator()), permissions, davFactory);
        context.set(METADATA_SERVICE, api);
        adminAuthentication = mockAuthentication("admin");
        admin = createTestUser("admin", true);
        new DAO(model).write(admin);
        userAuthentication = mockAuthentication("user");
        user = createTestUser("user", false);
        new DAO(model).write(user);
        selectRegularUser();
    }

    @Test
    public void testGetWillNotReturnsDeleted() {
        var statement = createStatement(S2, createProperty("https://institut-curie.org/ontology#sample"), S1);
        txn.executeWrite(m -> m
                .add(statement)
                .add(S1, RDF.type, createResource( "https://institut-curie.org/ontology#BiologicalSample"))
                .add(S1, RDFS.label, "Sample 1")
                .add(S2, RDF.type, FS.File)
                .add(S2, RDFS.label, "File 1")
                .add(S2, FS.dateDeleted, "2021-07-06")
        );
        assertFalse(api.get(S1.getURI(), false).contains(statement));
    }

    @Test
    public void testPutWillAddStatements() {
        var delta = modelOf(STMT1, STMT2).add(S1, RDF.type, class1).add(S2, RDF.type, class1);

        api.put(delta);

        assertTrue(api.get(S1.getURI(), false).contains(STMT1));
        assertTrue(api.get(S2.getURI(), false).contains(STMT2));
    }

    @Test
    public void testPutAdminEditOnly() {
        model.add(S1, FS.adminEditOnly, createTypedLiteral(true)).add(S1, RDF.type, class2);
        var delta = modelOf(STMT1);
        assertThrows(AccessDeniedException.class, () -> api.put(delta));

        selectAdmin();
        api.put(delta);
    }

    @Test
    public void testPutHandlesLifecycleForEntities() {
        var delta = modelOf(STMT1).add(S1, RDF.type, class1);
        api.put(delta);
        assertTrue(ds.getDefaultModel().contains(STMT1.getSubject(), FS.createdBy));
        assertTrue(ds.getDefaultModel().contains(STMT1.getSubject(), FS.dateCreated));
        assertTrue(ds.getDefaultModel().contains(STMT1.getSubject(), FS.modifiedBy));
        assertTrue(ds.getDefaultModel().contains(STMT1.getSubject(), FS.dateModified));
    }


    @Test
    public void testPutWillNotRemoveExistingStatements() {
        // Prepopulate the model
        final Statement EXISTING1 = createStatement(S1, P1, S3);
        final Statement EXISTING2 = createStatement(S2, P2, createPlainLiteral("test"));
        txn.executeWrite(m -> m.add(EXISTING1).add(EXISTING2).add(S1, RDF.type, class1).add(S2, RDF.type, class1));

        // Put new statements
        var delta = modelOf(STMT1, STMT2);
        api.put(delta);

        // Now ensure that the existing triples are still there
        // and the new ones are added
        txn.executeRead(model -> { ;
            assertTrue(model.contains(EXISTING1));
            assertTrue(model.contains(EXISTING2));
            assertTrue(model.contains(STMT1));
            assertTrue(model.contains(STMT2));
        });
    }

    @Test
    public void deleteModel() {
        txn.executeWrite(m -> m.add(STMT1).add(STMT2).add(S1, RDF.type, class1).add(S2, RDF.type, class1));

        api.delete(modelOf(STMT1));

        txn.executeRead(m -> {
            assertFalse(m.contains(STMT1));
            assertTrue(m.contains(STMT2));
        });
    }

    @Test
    public void deleteModelAdminEditOnly() {
        txn.executeWrite(m -> m.add(STMT1).add(STMT2)
                .add(S1, RDF.type, class2)
                .add(S2, RDF.type, class2));

        assertThrows(AccessDeniedException.class, () -> api.delete(modelOf(STMT1)));

        selectAdmin();
        api.delete(modelOf(STMT1));
    }

    @Test
    public void patch() {
        txn.executeWrite(m -> m.add(STMT1).add(STMT2).add(S1, RDF.type, class1).add(S2, RDF.type, class1));

        Statement newStmt1 = createStatement(S1, P1, S3);
        Statement newStmt2 = createStatement(S2, P1, S1);
        Statement newStmt3 = createStatement(S1, P2, S3);

        api.patch(modelOf(newStmt1, newStmt2, newStmt3));

        txn.executeRead(m -> {
            assertTrue(m.contains(newStmt1));
            assertTrue(m.contains(newStmt2));
            assertTrue(m.contains(newStmt3));
            assertFalse(m.contains(STMT1));
            assertFalse(m.contains(STMT2));
        });
    }

    @Test
    public void patchAdminEditOnly() {
        var modelPatch = modelOf(STMT1).add(S1, RDF.type, class2);
        assertThrows(AccessDeniedException.class, () -> api.patch(modelPatch));

        selectAdmin();
        api.patch(modelPatch);
    }

    @Test
    public void patchWithNil() {
        txn.executeWrite(m -> m.add(S1, P1, S2).add(S1, P1, S3).add(S1, RDF.type, class1));

        api.patch(createDefaultModel().add(S1, P1, FS.nil));

        assertFalse(txn.calculateRead(m -> m.contains(S1, P1)));
    }

    @Test
    public void putMultiple() {
        api.put(modelOf(
                createStatement(S1, RDF.type, FS.Directory),
                createStatement(S1, RDFS.label, createStringLiteral("Test 1"))
        ));
        api.put(modelOf(
                createStatement(S2, RDF.type, FS.Directory),
                createStatement(S2, RDFS.label, createStringLiteral("Test 2"))
        ));
    }

    @Test
    public void patchDuplicateLabelDoesNotFail() {
        txn.executeWrite(m -> m
                .add(S1, RDF.type, FS.Directory)
                .add(S1, RDFS.label, "Test 1")
                .add(S2, RDF.type, FS.Directory)
                .add(S2, RDFS.label, "Test 2")
        );

        api.patch(modelOf(createStatement(S1, RDFS.label, createStringLiteral("Test 2 "))));
    }

    @Test
    public void putSameLabelDifferentType() {
        api.put(modelOf(
                createStatement(S1, RDF.type, FS.Directory),
                createStatement(S1, RDFS.label, createStringLiteral("Test"))
        ));
        api.put(modelOf(
                createStatement(S2, RDF.type, createResource(NS + "Sample")),
                createStatement(S2, RDFS.label, createStringLiteral("Test"))
        ));
    }

    @Test
    public void putLabelTrimmed() {
        api.put(modelOf(
                createStatement(S1, RDF.type, FS.Directory),
                createStatement(S1, RDFS.label, createStringLiteral(" Label with whitespace  "))
        ));
        assertNotEquals(" Label with whitespace  ", ds.getDefaultModel().getProperty(S1, RDFS.label).getString());
        assertEquals("Label with whitespace", ds.getDefaultModel().getProperty(S1, RDFS.label).getString());
    }

    @Test
    public void patchLabelTrimmed() {
        txn.executeWrite(m -> m.add(S1, RDFS.label, createStringLiteral("Label 1")).add(S1, RDF.type, class1));

        api.patch(createDefaultModel().add(S1, RDFS.label, createStringLiteral("Label 2 ")));

        assertEquals("Label 2", ds.getDefaultModel().getProperty(S1, RDFS.label).getString());
    }

    @Test
    public void patchLabelChangesLinkedDavResourceLabel()
            throws BadRequestException, NotAuthorizedException, ConflictException {
        var admin = createTestUser("admin", true);
        lenient().when(userService.currentUser()).thenReturn(admin);
        var departmentType = "https://sils.uva.nl/ontology#Department";
        when(request.getHeader("Entity-Type")).thenReturn(departmentType);

        var root = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH);
        root.createCollection("Dep1");

        var departmentDirectories = ds.getDefaultModel()
                .listResourcesWithProperty(RDFS.label, "Dep1")
                .filterKeep(resource -> resource.hasProperty(RDF.type, FS.Directory))
                .toList();
        assertEquals(1, departmentDirectories.size());
        var departmentDirectory = departmentDirectories.get(0);
        var departmentLinkedEntity = departmentDirectory.getPropertyResourceValue(FS.linkedEntity);
        assertTrue(departmentLinkedEntity.hasProperty(RDFS.label, "Dep1"));

        api.patch(createDefaultModel().add(departmentLinkedEntity, RDFS.label, "Label changed"));

        assertEquals("Label changed", ds.getDefaultModel().getProperty(departmentLinkedEntity, RDFS.label).getString());
        assertFalse(ds.getDefaultModel()
                .listResourcesWithProperty(RDFS.label, "Dep1")
                .filterKeep(resource -> resource.hasProperty(RDF.type, FS.Directory))
                .hasNext()
        );
        assertTrue(ds.getDefaultModel()
                .listResourcesWithProperty(RDFS.label, "Label changed")
                .filterKeep(resource -> resource.hasProperty(RDF.type, FS.Directory))
                .hasNext()
        );
    }

    @Test
    public void testPatchHandlesLifecycleForEntities() {
        var delta = modelOf(STMT1).add(S1, RDF.type, class1);
        api.patch(delta);
        assertTrue(ds.getDefaultModel().contains(STMT1.getSubject(), FS.modifiedBy));
        assertTrue(ds.getDefaultModel().contains(STMT1.getSubject(), FS.dateModified));
    }

    @Test
    public void deleteLinkedEntity() {
        txn.executeWrite(m -> m
                .add(STMT1)
                .add(STMT2)
                .add(createStatement(S2, RDF.type, FS.Directory))
                .add(createStatement(S2, FS.linkedEntity, S1))
                .add(S1, RDF.type, class1)
        );

        assertThrows(IllegalArgumentException.class, () -> api.softDelete(S1));
    }
}
