package io.fairspace.saturn.webdav;

import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.metadata.validation.ComposedValidator;
import io.fairspace.saturn.services.metadata.validation.DeletionValidator;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vocabulary.FS;
import io.milton.http.FileItem;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.MakeCollectionableResource;
import io.milton.resource.PutableResource;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.eclipse.jetty.server.Authentication;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static io.fairspace.saturn.TestUtils.*;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;
import static io.fairspace.saturn.config.Services.METADATA_PERMISSIONS;
import static io.fairspace.saturn.config.Services.METADATA_SERVICE;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static org.apache.jena.query.DatasetFactory.wrap;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.junit.Assert.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DirectoryResourceTest {
    static final String BASE_PATH = "/api/webdav";
    static final String baseUri = "http://example.com" + BASE_PATH;
    private static final int FILE_SIZE = 10;
    private Model model;

    @Mock
    BlobStore store;
    @Mock
    UserService userService;
    @Mock
    MetadataService metadataService;
    @Mock
    FileItem file;
    @Mock
    BlobFileItem blobFileItem;

    private DavFactory davFactory;
    DirectoryResource dir;
    User admin;
    Authentication.User adminAuthentication;
    User user;
    Authentication.User userAuthentication;
    private org.eclipse.jetty.server.Request request;

    private void selectAdmin() {
        lenient().when(request.getAuthentication()).thenReturn(adminAuthentication);
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    private void selectRegularUser() {
        lenient().when(request.getAuthentication()).thenReturn(userAuthentication);
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    @Before
    public void before() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        Context context = new Context();
        var dsg = DatasetGraphFactory.createTxnMem();
        Dataset ds = wrap(dsg);
        Transactions tx = new SimpleTransactions(ds);
        model = ds.getDefaultModel();
        MetadataPermissions permissions = new MetadataPermissions(userService, VOCABULARY);
        context.set(METADATA_PERMISSIONS, permissions);
        davFactory = new DavFactory(model.createResource(baseUri), store, userService, context);
        metadataService = new MetadataService(tx, VOCABULARY, new ComposedValidator(new DeletionValidator()), permissions, davFactory);
        context.set(METADATA_SERVICE, metadataService);

        adminAuthentication = mockAuthentication("admin");
        admin = createTestUser("admin", true);
        new DAO(model).write(admin);
        userAuthentication = mockAuthentication("user");
        user = createTestUser("user", false);
        new DAO(model).write(user);

        setupRequestContext();
        request = getCurrentRequest();
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Department");

        selectAdmin();

        var taxonomies = model.read("taxonomies.ttl");
        metadataService.put(taxonomies);
        var testdata = model.read("testdata.ttl");
        metadataService.put(testdata);

        var blob = new BlobInfo("id", FILE_SIZE, "md5");
        when(request.getAttribute("BLOB")).thenReturn(blob);
        when(file.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(new byte[FILE_SIZE]));

        var root = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH);
        var coll1 = (PutableResource) root.createCollection("coll1");
        coll1.createNew("coffee.jpg", null, 0L, "image/jpeg");
        root.createCollection("coll2");
    }

    @Ignore("File upload functionality currently not supported.")
    @Test
    public void testFileUploadSuccess() throws NotAuthorizedException, ConflictException, BadRequestException {
        dir = new DirectoryResource(davFactory, model.getResource(baseUri + "/dir"), Access.Manage);
        dir.subject.addProperty(RDF.type, FS.Directory);

        dir.processForm(Map.of("action", "upload_files"), Map.of("/subdir/file.ext", blobFileItem));

        assertTrue(dir.child("subdir") instanceof DirectoryResource);

        var subdir = (DirectoryResource) dir.child("subdir");

        assertTrue(subdir.child("file.ext") instanceof FileResource);

        var file = (FileResource) subdir.child("file.ext");

        assertEquals(FILE_SIZE, (long) file.getContentLength());
    }

    @Ignore("File upload functionality currently not supported.")
    @Test
    public void testFileUploadExistingDir() throws NotAuthorizedException, ConflictException, BadRequestException {
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        dir.processForm(Map.of("action", "upload_files"), Map.of("/coll1/file.ext", blobFileItem));

        assertTrue(dir.child("coll1") instanceof DirectoryResource);

        var subdir = (DirectoryResource) dir.child("coll1");
        assertTrue(subdir.child("file.ext") instanceof FileResource);

        var file = (FileResource) subdir.child("file.ext");
        assertEquals(FILE_SIZE, (long) file.getContentLength());
    }

    @Test
    public void testTypedLiteralMetadataUploadSuccess() throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv =
                "DirectoryName,Description\n" +
                        "coll1,\"Blah\"\n" +
                        "coll2,\"Blah blah\"\n";
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        DirectoryResource dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        DirectoryResource dir2 = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll2");
        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));

        assertTrue(model.getResource(dir.getLinkedEntityIri()).hasProperty(RDFS.comment, model.createTypedLiteral("Blah")));
        assertTrue(model.getResource(dir2.getLinkedEntityIri()).hasProperty(RDFS.comment, model.createTypedLiteral("Blah blah")));
    }

    @Test(expected = BadRequestException.class)
    public void testMetadataUploadUnknownProperty() throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv =
                "DirectoryName,Unknown\n" +
                        "./coll1,\"Blah blah\"\n";
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");

        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }

    @Test(expected = BadRequestException.class)
    public void testMetadataUploadEmptyHeader() throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv =
                ",\n" +
                        "./coll1,\"Blah blah\"\n";
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");

        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }

    @Test(expected = BadRequestException.class)
    public void testMetadataUploadUnknownFile() throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv =
                "DirectoryName,Description\n" +
                        "./subdir,\"Blah blah\"\n";
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");

        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }

    @Test(expected = BadRequestException.class)
    public void testMetadataUploadDeletedFile() throws NotAuthorizedException, ConflictException, BadRequestException {
        String csv =
                "DirectoryName,Label,Description\n" +
                        "coll1,\"subdir label\",\"Blah blah\"\n";
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        dir.delete();

        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }

    @Ignore("Ignored until the basic data model is not defined")
    @Test
    public void testLinkedMetadataUploadByIRISuccess() throws NotAuthorizedException, ConflictException, BadRequestException {
        Property sampleProp = createProperty("https://institut-curie.org/ontology#sample");
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        assert !dir.subject.hasProperty(sampleProp);

        String csv =
                "DirectoryName,Is about biological sample\n" +
                        ".,\"http://example.com/samples#s2-b\"\n";
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));

        assertEquals(dir.subject.getProperty(sampleProp).getResource().getURI(), "http://example.com/samples#s2-b");
    }

    @Ignore("No entity linked to the root level entity.")
    @Test
    public void testLinkedMetadataUploadByLabelSuccess() throws NotAuthorizedException, ConflictException, BadRequestException {
        Property departmentContactPersonName = createProperty("http://example.com/samples#s1-a");
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        assert !dir.subject.hasProperty(departmentContactPersonName);

        String csv =
                "DirectoryName,Contact person name\n" +
                        baseUri + "/coll1,\"John Smith\"\n";
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));

        assertEquals(model.getResource(dir.getLinkedEntityIri()).getProperty(departmentContactPersonName).getResource().getURI(), "http://example.com/samples#s1-a");
    }

    @Test(expected = BadRequestException.class)
    public void testLinkedMetadataUploadByUnknownIRI() throws NotAuthorizedException, ConflictException, BadRequestException {
        Property sampleProp = createProperty("https://institut-curie.org/ontology#sample");
        dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");
        assert !dir.subject.hasProperty(sampleProp);

        String csv =
                "DirectoryName,Is about biological sample\n" +
                        ".,\"http://example.com/samples#unknown-sample\"\n";
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file));
    }

    @Test
    public void testMetadataUploadAccessDenied() throws NotAuthorizedException, ConflictException, BadRequestException {
        selectRegularUser();
        String csv =
                "DirectoryName,Description\n" +
                        "coll1,\"Blah\"\n";
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes()));
        DirectoryResource dir = (DirectoryResource) davFactory.getResource(null, BASE_PATH + "/coll1");

        Exception exception = assertThrows(BadRequestException.class, () -> dir.processForm(Map.of("action", "upload_metadata"), Map.of("file", file)));
        String expectedMessage = "Error applying metadata. Access denied.";
        String actualMessage = ((BadRequestException) exception).getReason();

        assertTrue(actualMessage.contains(expectedMessage));
    }
}
