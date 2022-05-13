package io.fairspace.saturn.services.views;

import io.fairspace.saturn.config.ConfigLoader;
import io.fairspace.saturn.rdf.dao.DAO;
import io.fairspace.saturn.rdf.search.FilteredDatasetGraph;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.metadata.validation.ComposedValidator;
import io.fairspace.saturn.services.metadata.validation.DeletionValidator;
import io.fairspace.saturn.services.search.FileSearchRequest;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.webdav.BlobStore;
import io.fairspace.saturn.webdav.DavFactory;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.MakeCollectionableResource;
import lombok.SneakyThrows;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.DatasetImpl;
import org.apache.jena.sparql.util.Context;
import org.eclipse.jetty.server.Authentication;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.fairspace.saturn.TestUtils.*;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;
import static io.fairspace.saturn.config.Services.FS_ROOT;
import static io.fairspace.saturn.config.Services.METADATA_PERMISSIONS;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static org.apache.jena.query.DatasetFactory.wrap;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SparqlQueryServiceTest {
    static final String BASE_PATH = "/api/webdav";
    static final String baseUri = ConfigLoader.CONFIG.publicUrl + BASE_PATH;

    @Mock
    BlobStore store;
    @Mock
    UserService userService;
    @Mock
    private MetadataPermissions permissions;
    MetadataService api;
    QueryService queryService;

    User user;
    Authentication.User userAuthentication;
    User user2;
    Authentication.User user2Authentication;
    User admin;
    Authentication.User adminAuthentication;
    private org.eclipse.jetty.server.Request request;

    private DavFactory davFactory;


    private void selectRegularUser() {
        lenient().when(request.getAuthentication()).thenReturn(userAuthentication);
        lenient().when(userService.currentUser()).thenReturn(user);
    }

    private void selectExternalUser() {
        lenient().when(request.getAuthentication()).thenReturn(user2Authentication);
        lenient().when(userService.currentUser()).thenReturn(user2);
    }

    private void selectAdmin() {
        lenient().when(request.getAuthentication()).thenReturn(adminAuthentication);
        lenient().when(userService.currentUser()).thenReturn(admin);
    }

    private void setupUsers(Model model) {
        userAuthentication = mockAuthentication("user");
        user = createTestUser("user", false);
        new DAO(model).write(user);
        user2Authentication = mockAuthentication("user2");
        user2 = createTestUser("user2", false);
        new DAO(model).write(user2);
        adminAuthentication = mockAuthentication("admin");
        admin = createTestUser("admin", true);
        new DAO(model).write(admin);
    }

    @Before
    public void before() throws NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var dsg = DatasetGraphFactory.createTxnMem();
        Dataset ds = wrap(dsg);
        Transactions tx = new SimpleTransactions(ds);
        Model model = ds.getDefaultModel();

        var context = new Context();
        davFactory = new DavFactory(model.createResource(baseUri), store, userService, context);
        ds.getContext().set(FS_ROOT, davFactory.root);
        var permissions = new MetadataPermissions(userService, VOCABULARY);
        context.set(METADATA_PERMISSIONS, permissions);
        var filteredDatasetGraph = new FilteredDatasetGraph(ds.asDatasetGraph(), permissions);
        var filteredDataset = DatasetImpl.wrap(filteredDatasetGraph);

        queryService = new SparqlQueryService(ConfigLoader.CONFIG.search, ConfigLoader.VIEWS_CONFIG, filteredDataset);

        when(permissions.canWriteMetadata(any())).thenReturn(true);
        api = new MetadataService(tx, VOCABULARY, new ComposedValidator(new DeletionValidator()), this.permissions, davFactory);

        setupUsers(model);

        setupRequestContext();
        request = getCurrentRequest();

        selectAdmin();

        var taxonomies = model.read("taxonomies.ttl");
        api.put(taxonomies);
        var testdata = model.read("testdata.ttl");
        api.put(testdata);

        var root = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH);

        // existing dept
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("http://example.com/department#d1");
        var dept01 = (MakeCollectionableResource) root.createCollection("Dept001");

        // new dept
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Department");
        root.createCollection("Dept02");

        // new PI
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#PrincipalInvestigator");
        var pi001 = (MakeCollectionableResource) dept01.createCollection("PI001");

        // new Project
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Project");
        var project001 = (MakeCollectionableResource) pi001.createCollection("Project001");

        // new Study
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Study");
        var study001 = (MakeCollectionableResource) project001.createCollection("Study001");

        // existing Object
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("http://example.com/object#object1");
        when(request.getHeader("Entity-Type")).thenReturn("");
        var object001 = (MakeCollectionableResource) study001.createCollection("Object001");

        // new Sample
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Sample");
        var sample001 = (MakeCollectionableResource) object001.createCollection("Sample001");

        // new Assay
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Assay");
        sample001.createCollection("Assay001");
    }

    @Test
    public void testRetrieveResourcePage_NoFilters_NoFiles() {
        var vr = new ViewRequest();
        vr.setView("Resource");
        vr.setPage(1);
        vr.setSize(10);
        vr.setFilters(new ArrayList<>());
        var page = queryService.retrieveViewPage(vr);
        assertEquals(0, page.getRows().size());
    }

    @SneakyThrows
    @Test
    public void testRetrieveResourcePage_NoFilters_2Files() {
        var assay = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH + "/Dept001/PI001/Project001/Study001/Object001/Sample001/Assay001");

        // new File
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#ExternalFile");
        assay.createCollection("File001");

        // new File
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#ExternalFile");
        assay.createCollection("File001-2");

        var vr = new ViewRequest();
        vr.setView("Resource");
        vr.setPage(1);
        vr.setSize(10);
        vr.setFilters(new ArrayList<>());
        var page = queryService.retrieveViewPage(vr);
        assertEquals(2, page.getRows().size());

        // altitude, as defined in testdata.ttl
        var objectAltitude = page.getRows().get(0).get("Object_objectAltitude").iterator().next().getValue();

        assertEquals(94, objectAltitude);
    }

    @SneakyThrows
    @Test
    public void testCountResources() {
        var assay = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH + "/Dept001/PI001/Project001/Study001/Object001/Sample001/Assay001");

        var requestParams = new CountRequest();
        requestParams.setView("Resource");
        requestParams.setFilters(new ArrayList<>());
        var result = queryService.count(requestParams);
        assertEquals(0, result.getCount());

        // new File
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#ExternalFile");
        assay.createCollection("File001");

        // new File
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#ExternalFile");
        assay.createCollection("File001-2");

        result = queryService.count(requestParams);
        assertEquals(2, result.getCount());
    }

    @SneakyThrows
    @Test
    public void testRetrieveViewPageUsingObjectFilter() {
        var assay = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH + "/Dept001/PI001/Project001/Study001/Object001/Sample001/Assay001");

        // new File
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#ExternalFile");
        assay.createCollection("File001");

        var vr = new ViewRequest();
        vr.setView("Resource");
        vr.setPage(1);
        vr.setSize(10);
        vr.setFilters(List.of(
                ViewFilter.builder()
                        .field("Object_objectAltitude")
                        .min(92)
                        .build()
        ));
        var page = queryService.retrieveViewPage(vr);
        assertEquals(1, page.getRows().size());
    }

    @SneakyThrows
    @Test
    public void testRetrieveUniqueFilesForLocation() {
        var vr_filter = new ViewRequest();
        vr_filter.setView("Resource");
        vr_filter.setPage(1);
        vr_filter.setSize(10);
        vr_filter.setFilters(Collections.singletonList(
                ViewFilter.builder()
                        .field("location")
                        .values(Collections.singletonList(baseUri + "/Dept001/PI001/Project001/Study001/Object001/Sample001"))
                        .build()
        ));

        // no files yet
        var page = queryService.retrieveViewPage(vr_filter);
        assertEquals(0, page.getRows().size());

        var object001 = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH + "/Dept001/PI001/Project001/Study001/Object001");

        // new file002
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Sample");
        var sample002 = (MakeCollectionableResource) object001.createCollection("Sample002");
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Assay");
        var assay002 = (MakeCollectionableResource) sample002.createCollection("Assay002");
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#ExternalFile");
        assay002.createCollection("File002");

        // file002 should not be found given the search path
        page = queryService.retrieveViewPage(vr_filter);
        assertEquals(0, page.getRows().size());

        var assay001 = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH + "/Dept001/PI001/Project001/Study001/Object001/Sample001/Assay001");
        when(request.getHeader("Linked-Entity-IRI")).thenReturn("");
        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#ExternalFile");
        assay001.createCollection("File001");

        // file001 and file002 are expected
        var vr_no_filter = new ViewRequest();
        vr_no_filter.setView("Resource");
        vr_no_filter.setPage(1);
        vr_no_filter.setSize(10);
        vr_no_filter.setFilters(new ArrayList<>());
        page = queryService.retrieveViewPage(vr_no_filter);
        assertEquals(2, page.getRows().size());

        // file001 is expected
        page = queryService.retrieveViewPage(vr_filter);
        assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveResourcesForInvalidLocation() {
        var vr = new ViewRequest();
        vr.setView("Resource");
        vr.setPage(1);
        vr.setSize(10);
        vr.setFilters(Collections.singletonList(
                ViewFilter.builder()
                        .field("location")
                        .values(Collections.singletonList(">; INSERT something"))
                        .build()
        ));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> queryService.retrieveViewPage(vr));
        String expectedMessage = "Invalid IRI";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

     @Test
    public void testRetrieveFilesForInvalidParent() {
        selectAdmin();
        var request = new FileSearchRequest();
        request.setQuery("coffee");
        request.setParentIRI(">; INSERT something");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> queryService.searchFiles(request));
        String expectedMessage = "Invalid IRI";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }
}
