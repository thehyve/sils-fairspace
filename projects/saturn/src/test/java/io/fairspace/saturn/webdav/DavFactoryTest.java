package io.fairspace.saturn.webdav;

import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.vocabulary.FS;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.*;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
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

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static io.fairspace.saturn.TestUtils.*;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static io.milton.http.ResponseStatus.SC_FORBIDDEN;
import static java.lang.String.format;
import static org.apache.jena.query.DatasetFactory.createTxnMem;
import static org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/*
    Tests below depend on the current data model and entity hierarchy.
    They may need to be adjusted after a data model change.
 */
@RunWith(MockitoJUnitRunner.class)
public class DavFactoryTest {
    public static final long FILE_SIZE = 3L;
    public static final String BASE_PATH = "/api/webdav";
    public static final QName VERSION = new QName(FS.NS, "version");
    private static final String baseUri = "http://example.com" + BASE_PATH;
    @Mock
    BlobStore store;
    @Mock
    InputStream input;
    @Mock
    UserService userService;

    Context context = new Context();
    User user;
    Authentication.User userAuthentication;
    User admin;
    Authentication.User adminAuthentication;
    private org.eclipse.jetty.server.Request request;

    private ResourceFactory factory;
    private Dataset ds = createTxnMem();
    private Model model = ds.getDefaultModel();

    private void selectRegularUser() {
        lenient().when(request.getAuthentication()).thenReturn(userAuthentication);
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    private void selectAdmin() {
        lenient().when(request.getAuthentication()).thenReturn(adminAuthentication);
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    @Before
    public void before() {
        factory = new DavFactory(model.createResource(baseUri), store, userService, context);

        userAuthentication = mockAuthentication("user");
        user = createTestUser("user", false);
        new DAO(model).write(user);
        adminAuthentication = mockAuthentication("admin");
        admin = createTestUser("admin", true);
        new DAO(model).write(admin);
        VOCABULARY.getResource("https://sils.uva.nl/ontology#Department").addProperty(FS.adminEditOnly, createTypedLiteral(true));

        setupRequestContext();
        request = getCurrentRequest();

        selectRegularUser();
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Department");
    }

    @Test
    public void testRoot() throws NotAuthorizedException, BadRequestException {
        var root = factory.getResource(null, BASE_PATH);

        assertTrue(root instanceof MakeCollectionableResource);
        assertFalse(root instanceof PutableResource);
    }

    @Test
    public void testCreateRootDirectory() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir = root.createCollection("dir0");

        var dirName = "dir0";
        assertTrue(dir instanceof FolderResource);
        assertEquals(dirName, dir.getName());
        assertNotNull(root.child(dirName));
        assertNotNull(factory.getResource(null, format("/api/webdav/%s/", dirName)));
        assertEquals(1, root.getChildren().size());

        assertEquals(Access.Read, ((DavFactory) factory).getAccess(model.getResource(baseUri + "/" + dirName)));
        selectAdmin();
        assertEquals(Access.Write, ((DavFactory) factory).getAccess(model.getResource(baseUri + "/" + dirName)));
    }

    @Test
    public void testCreateRootDirectoryStartingWithDash() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir = root.createCollection("-dir");

        var dirName = "-dir";
        assertTrue(dir instanceof FolderResource);
        assertEquals(dirName, dir.getName());
        assertNotNull(root.child(dirName));
        assertNotNull(factory.getResource(null,format("/api/webdav/%s/", dirName)));
        assertEquals(1, root.getChildren().size());
    }

    @Test
    public void testCreateRootDirectoryWithTooLongName() throws NotAuthorizedException, ConflictException, BadRequestException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        try {
            root.createCollection("");
            fail("Empty directory label; should be rejected.");
        } catch (BadRequestException e) {
            assertEquals("The directory name is empty.", e.getReason());
        }
        var tooLongName = "test123_56".repeat(20);
        try {
            root.createCollection(tooLongName);
            fail("Directory name should be rejected as too long.");
        } catch (BadRequestException e) {
            assertEquals("The directory name exceeds maximum length 127.", e.getReason());
        }
    }

    @Test
    public void testNonExistingResource() throws NotAuthorizedException, BadRequestException {
        assertNull(factory.getResource(null, BASE_PATH + "dir0/dir/file"));
    }

    @Test
    public void testAdminAccess() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var collName = "coll";
        root.createCollection(collName);

        selectAdmin();
        assertEquals(1, root.getChildren().size());
        assertEquals(Access.Write, ((DavFactory) factory).getAccess(model.getResource(baseUri + "/" + collName)));
        selectRegularUser();
        assertEquals(Access.Read, ((DavFactory) factory).getAccess(model.getResource(baseUri + "/" + collName)));
    }

    @Test(expected = ConflictException.class)
    public void testCreateRootDirectoryTwiceFails() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        assertNotNull(root.createCollection("dir0"));
        root.createCollection("dir0");
    }

    @Test
    public void testCreateRootDirectoryWithSameNameButDifferentCaseDoesNotFail() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        assertNotNull(root.createCollection("dir1"));
        assertNotNull(root.createCollection("DIR1"));
        assertEquals(2, root.getChildren().size());
    }

    @Test
    public void testCreateChildDirectory() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var rootDir = (FolderResource) root.createCollection("dir0");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var dir = rootDir.createCollection("dir01");
        assertNotNull(dir);
        assertEquals("dir01", dir.getName());
        assertEquals(1, rootDir.getChildren().size());
    }

    @Test
    public void testCreateChildDirectoryWithExistingNameFails() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var rootDir = (FolderResource) root.createCollection("dir0");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        rootDir.createCollection("dir01");
        try {
            rootDir.createCollection("dir01");
            fail("Children name should be unique.");
        } catch (ConflictException e) {
            assertEquals("Target directory with name: dir01 already exists.", e.getMessage());
        }
        assertEquals(1, rootDir.getChildren().size());
    }

    @Test
    public void testCreateChildDirectoryWithExistingNameAsDeletedFails() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var rootDir = (FolderResource) root.createCollection("dir0");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var dir1 = rootDir.createCollection("dir01");
        ((FolderResource) dir1).delete();

        assertEquals(0, rootDir.getChildren().size());

        try {
            rootDir.createCollection("dir01");
            fail("Children name should be unique.");
        } catch (ConflictException e) {
            assertEquals("Target directory with name: dir01 already exists.", e.getMessage());
        }
        assertEquals(0, rootDir.getChildren().size());
    }


    @Test(expected = BadRequestException.class)
    public void testCreateRootDirectoryWithNonRootEntityType() throws NotAuthorizedException, BadRequestException, ConflictException {
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Project"); // NON-root type
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        root.createCollection("dir0");
    }

    @Test(expected = BadRequestException.class)
    public void testCreateRootDirectoryWithInvalidChildEntityType() throws NotAuthorizedException, BadRequestException, ConflictException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var rootDir = (FolderResource) root.createCollection("dir0");
        // No change in returned Entity-Type header
        rootDir.createCollection("dir01");
    }

    @Ignore("To be removed after removing File resource")
    @Test
    public void testCreateFile() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");

        verifyNoInteractions(input, store);

        assertNotNull(factory.getResource(null, BASE_PATH + format("/%s/file", "coll")));

        assertTrue(file instanceof GetableResource);
        assertEquals("file", file.getName());
        assertEquals(FILE_SIZE, ((GetableResource)file).getContentLength().longValue());
        assertEquals("text/abc", ((GetableResource)file).getContentType(BASE_PATH));

        assertTrue(file instanceof MultiNamespaceCustomPropertyResource);

        assertEquals(1, ((MultiNamespaceCustomPropertyResource) file).getProperty(VERSION));
    }

    @Ignore("To be removed after removing File resource")
    @Test
    public void testOverwriteFile() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");

        assertTrue(file instanceof ReplaceableResource);

        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", FILE_SIZE + 1, "md5"));
        ((ReplaceableResource) file).replaceContent(input, FILE_SIZE + 1);

        verifyNoInteractions(input, store);

        var file2 = coll.child("file");
        assertEquals(2, ((MultiNamespaceCustomPropertyResource) file2).getProperty(VERSION));
        assertEquals(FILE_SIZE + 1, ((GetableResource)file2).getContentLength().longValue());
    }


    @Ignore("To be removed after removing File resource")
    @Test
    public void testGetVersion() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");

        assertTrue(file instanceof ReplaceableResource);

        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", FILE_SIZE + 1, "md5"));
        ((ReplaceableResource) file).replaceContent(input, FILE_SIZE + 1);

        when(request.getHeader("Version")).thenReturn("1");
        var ver1 = coll.child("file");
        assertEquals(1, ((MultiNamespaceCustomPropertyResource) ver1).getProperty(VERSION));
        assertEquals(FILE_SIZE, ((GetableResource)ver1).getContentLength().longValue());

        when(request.getHeader("Version")).thenReturn("2");
        var ver2 = coll.child("file");
        assertEquals(2, ((MultiNamespaceCustomPropertyResource) ver2).getProperty(VERSION));
        assertEquals(FILE_SIZE + 1, ((GetableResource)ver2).getContentLength().longValue());
    }

    @Ignore("To be removed after removing File resource")
    @Test
    public void testRevert() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("file", input, FILE_SIZE, "text/abc");

        assertTrue(file instanceof ReplaceableResource);

        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", FILE_SIZE + 1, "md5"));
        ((ReplaceableResource) file).replaceContent(input, FILE_SIZE + 1);

        ((PostableResource)coll.child("file")).processForm(Map.of("action", "revert", "version", "1"), Map.of());

        var ver3 = coll.child("file");
        assertEquals(3, ((MultiNamespaceCustomPropertyResource) ver3).getProperty(VERSION));
        assertEquals(FILE_SIZE, ((GetableResource)ver3).getContentLength().longValue());
    }

    @Test
    public void testDeleteDirectory() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir = (FolderResource) root.createCollection("dir");

        assertEquals(1, root.getChildren().size());

        dir.delete();

        verifyNoInteractions(input, store);
        assertEquals(0, root.getChildren().size());
    }

    @Test
    public void testShowDeletedDirectories() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir = (FolderResource) root.createCollection("dir0");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var subDir = dir.createCollection("dir1");

        assertTrue(subDir instanceof DeletableResource);
        ((DeletableResource)subDir).delete();

        when(request.getHeader("Show-Deleted")).thenReturn("on");

        assertEquals(1, dir.getChildren().size());
        assertNotNull(dir.child("dir1"));

        var deleted = (MultiNamespaceCustomPropertyResource) dir.child("dir1");

        assertNotNull(deleted.getProperty(new QName(FS.NS, "dateDeleted")));

        selectRegularUser();
        assertEquals(((DirectoryResource) deleted).getAccess(), Access.Read.name());

        selectAdmin();
        assertEquals(((DirectoryResource) deleted).getAccess(), Access.Write.name());
    }

    @Test
    public void testRestoreDeletedDirectories() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir = (FolderResource) root.createCollection("dir0");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var subDir = dir.createCollection("dir1");

        assertTrue(subDir instanceof DeletableResource);
        ((DeletableResource)subDir).delete();

        when(request.getHeader("Show-Deleted")).thenReturn("on");
        var deleted = (PostableResource) dir.child("dir1");

        deleted.processForm(Map.of("action", "undelete"), Map.of());

        var restored = (MultiNamespaceCustomPropertyResource) dir.child("dir1");
        assertNull(restored.getProperty(new QName(FS.NS, "dateDeleted")));
    }

    @Test
    public void testPurgeDirectory() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir = (FolderResource) root.createCollection("dir0");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var subDir = dir.createCollection("dir1");

        assertTrue(subDir instanceof DeletableResource);
        ((DeletableResource)subDir).delete();

        when(request.getHeader("Show-Deleted")).thenReturn("on");

        assertEquals(1, dir.getChildren().size());
        assertNotNull(dir.child("dir1"));

        var deleted = (DeletableResource) dir.child("dir1");

        try {
            deleted.delete();
            fail("Only admin can purge a directory.");
        } catch (NotAuthorizedException e) {
            assertNotNull(e);
            assertEquals(SC_FORBIDDEN, e.getRequiredStatusCode());
            assertEquals("Not authorized to purge the resource.", e.getMessage());
        }

        userService.currentUser().setAdmin(true);
        deleted.delete();

        assertNull(dir.child("dir1"));
    }

    @Ignore("To be removed after removing File resource")
    @Test
    public void testRenameFile() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var coll = (FolderResource) root.createCollection("coll");

        var file = coll.createNew("old", input, FILE_SIZE, "text/abc");

        ((MoveableResource) file).moveTo(coll, "new");

        assertEquals(1, coll.getChildren().size());
        assertNull(coll.child("old"));
        assertNotNull(coll.child("new"));
    }

    @Test
    public void testRenameDirectory() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir0 = (FolderResource) root.createCollection("dir0");

        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var subDir = dir0.createCollection("old");
        var departmentDirectories = ds.getDefaultModel()
                .listResourcesWithProperty(RDFS.label, "old")
                .filterKeep(resource -> resource.hasProperty(RDF.type, FS.Directory))
                .toList();
        assertEquals(1, departmentDirectories.size());
        var departmentDirectory = departmentDirectories.get(0);
        var departmentLinkedEntity = departmentDirectory.getPropertyResourceValue(FS.linkedEntity);
        assertTrue(departmentLinkedEntity.hasProperty(RDFS.label, "old"));

        ((MoveableResource) subDir).moveTo(dir0, "new");

        assertEquals(1, dir0.getChildren().size());
        assertNull(dir0.child("old"));
        assertNotNull(dir0.child("new"));
        assertEquals("new", ds.getDefaultModel().getProperty(departmentLinkedEntity, RDFS.label).getString());

        assertNull(factory.getResource(null, BASE_PATH + "/dir0/old"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/dir0/new"));
    }

    @Test
    public void testMoveDirectory() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir1 = (FolderResource) root.createCollection("d1");
        var dir2 = (FolderResource) root.createCollection("d2");

        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var dir11 = (MakeCollectionableResource) dir1.createCollection("dir1");
        var dir22 = dir2.createCollection("dir2");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Project");
        var subdir = dir11.createCollection("old");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Study");
        ((FolderResource) subdir).createCollection("dir_x");

        ((MoveableResource) subdir).moveTo(dir22, "new");

        assertNull(factory.getResource(null, BASE_PATH + "/d1/dir1/old"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/d2/dir2/new"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/d2/dir2/new/dir_x"));
    }

    @Test(expected = BadRequestException.class)
    public void testMoveDirectoryOnDifferentHierarchyLevelFails() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir0 = root.createCollection("dir0");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var dir01 = ((FolderResource) dir0).createCollection("dir01");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Project");
        var dir011 = ((FolderResource) dir01).createCollection("dir011");

        ((MoveableResource) dir011).moveTo(dir0, "dir0");
    }

    @Test(expected = ConflictException.class)
    public void testMoveDirectoryToExistingFails() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        root.createCollection("dir1");
        var dir2 = (FolderResource) root.createCollection("dir2");

        dir2.moveTo(root, "dir1");
    }

    @Test
    public void testCopyDirectory() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir1 = (FolderResource) root.createCollection("d1");
        var dir2 = (FolderResource) root.createCollection("d2");

        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var dir11 = (MakeCollectionableResource) dir1.createCollection("dir1");
        var dir22 = dir2.createCollection("dir2");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Project");
        var subdir = dir11.createCollection("old");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Study");
        ((FolderResource) subdir).createCollection("dir_x");

        ((CopyableResource) subdir).copyTo(dir22, "new");

        assertNotNull(factory.getResource(null, BASE_PATH + "/d1/dir1/old"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/d1/dir1/old/dir_x"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/d2/dir2/new"));
        assertNotNull(factory.getResource(null, BASE_PATH + "/d2/dir2/new/dir_x"));
    }

    @Test(expected = BadRequestException.class)
    public void testCopyDirectoryOnDifferentHierarchyLevelFails() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir0 = root.createCollection("dir0");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var dir01 = ((FolderResource) dir0).createCollection("dir01");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Project");
        var dir011 = ((FolderResource) dir01).createCollection("dir011");

        ((CopyableResource) dir011).copyTo(dir0, "dir0");
    }

    @Test(expected = ConflictException.class)
    public void testCopyDirectoryToExistingFails() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var root = (MakeCollectionableResource) factory.getResource(null, BASE_PATH);
        var dir01 = root.createCollection("dir01");
        var dir02 = root.createCollection("dir02");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var dir011 = ((FolderResource) dir01).createCollection("my_dir");
        var dir021 = ((FolderResource) dir02).createCollection("my_dir");

        ((CopyableResource) dir021).copyTo(dir01, "my_dir");
    }

}
