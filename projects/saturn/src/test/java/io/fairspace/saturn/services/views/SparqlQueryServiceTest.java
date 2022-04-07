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
import io.fairspace.saturn.webdav.BlobInfo;
import io.fairspace.saturn.webdav.BlobStore;
import io.fairspace.saturn.webdav.DavFactory;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.MakeCollectionableResource;
import io.milton.resource.PutableResource;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.DatasetImpl;
import org.apache.jena.sparql.util.Context;
import org.eclipse.jetty.server.Authentication;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static io.fairspace.saturn.TestUtils.*;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;
import static io.fairspace.saturn.config.Services.FS_ROOT;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static org.apache.jena.query.DatasetFactory.wrap;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@Ignore("Ignored until the basic data model is not defined")
@RunWith(MockitoJUnitRunner.class)
public class SparqlQueryServiceTest {
    static final String BASE_PATH = "/api/webdav";
    static final String baseUri = ConfigLoader.CONFIG.publicUrl + BASE_PATH;
    static final String SAMPLE_NATURE_BLOOD = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C12434";
    static final String ANALYSIS_TYPE_RNA_SEQ = "https://institut-curie.org/osiris#O6-12";
    static final String ANALYSIS_TYPE_IMAGING = "https://institut-curie.org/osiris#C37-2";

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
        user.setCanViewPublicMetadata(true);
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
        var davFactory = new DavFactory(model.createResource(baseUri), store, userService, context);
        ds.getContext().set(FS_ROOT, davFactory.root);
        var metadataPermissions = new MetadataPermissions(davFactory, userService);
        var filteredDatasetGraph = new FilteredDatasetGraph(ds.asDatasetGraph(), metadataPermissions);
        var filteredDataset = DatasetImpl.wrap(filteredDatasetGraph);

        queryService = new SparqlQueryService(ConfigLoader.CONFIG.search, ConfigLoader.VIEWS_CONFIG, filteredDataset);

        when(permissions.canWriteMetadata(any())).thenReturn(true);
        api = new MetadataService(tx, VOCABULARY, new ComposedValidator(new DeletionValidator()), permissions, davFactory);

        setupUsers(model);

        setupRequestContext();
        request = getCurrentRequest();

        selectAdmin();

        var taxonomies = model.read("taxonomies.ttl");
        api.put(taxonomies);

        when(request.getAttribute("BLOB")).thenReturn(new BlobInfo("id", 0, "md5"));

        var root = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH);
        var coll1 = (PutableResource) root.createCollection("coll1");
        coll1.createNew("coffee.jpg", null, 0L, "image/jpeg");

        selectRegularUser();

        var coll2 = (PutableResource) root.createCollection("coll2");
        coll2.createNew("sample-s2-b-rna.fastq", null, 0L, "chemical/seq-na-fastq");
        coll2.createNew("sample-s2-b-rna_copy.fastq", null, 0L, "chemical/seq-na-fastq");

        var testdata = model.read("testdata.ttl");
        api.put(testdata);
    }

    @Test
    public void testRetrieveSamplePage() {
        var viewRequest = new ViewRequest();
        viewRequest.setView("Sample");
        viewRequest.setPage(1);
        viewRequest.setSize(10);
        var page = queryService.retrieveViewPage(viewRequest);
        assertEquals(2, page.getRows().size());
        // The implementation does not sort results. Probably deterministic,
        // but no certain order is guaranteed.
        var row = page.getRows().get(0).get("Sample").iterator().next().getValue().equals("http://example.com/samples#s1-a")
                ? page.getRows().get(0) : page.getRows().get(1);
        assertEquals("Sample A for subject 1", row.get("Sample").iterator().next().getLabel());
        assertEquals(SAMPLE_NATURE_BLOOD, row.get("Sample_nature").iterator().next().getValue());
        assertEquals("Blood", row.get("Sample_nature").iterator().next().getLabel());
        assertEquals("Liver", row.get("Sample_topography").iterator().next().getLabel());
        assertEquals(45.2f, ((Number) row.get("Sample_tumorCellularity").iterator().next().getValue()).floatValue(), 0.01);
    }

    @Test
    public void testCountSamples() {
        selectRegularUser();
        var requestParams = new CountRequest();
        requestParams.setView("Sample");
        var result = queryService.count(requestParams);
        assertEquals(2, result.getCount());
    }

    @Test
    public void testCountSamplesWithoutViewAccess() {
        selectExternalUser();
        var countRequest = new CountRequest();
        countRequest.setView("Sample");
        var result = queryService.count(countRequest);
        assertEquals(0, result.getCount());
    }

    @Test
    public void testRetrieveSamplePageUsingSampleFilter() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(List.of(
                ViewFilter.builder()
                        .field("Sample_nature")
                        .values(List.of(SAMPLE_NATURE_BLOOD))
                        .build()
        ));
        var page = queryService.retrieveViewPage(request);
        assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveSamplePageUsingPrefixFilter() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(List.of(
                ViewFilter.builder()
                        .field("Sample")
                        .prefix("sample b")
                        .build()
        ));
        var page = queryService.retrieveViewPage(request);
        assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveSamplePageForAccessibleCollection() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(Collections.singletonList(
                ViewFilter.builder()
                        .field("Resource_analysisType")
                        .values(Collections.singletonList(ANALYSIS_TYPE_RNA_SEQ))
                        .build()
        ));
        var page = queryService.retrieveViewPage(request);
        assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveUniqueSamplesForLocation() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(Collections.singletonList(
                ViewFilter.builder()
                        .field("location")
                        .values(Collections.singletonList(baseUri + "/coll2"))
                        .build()
        ));
        var page = queryService.retrieveViewPage(request);
        assertEquals(1, page.getRows().size());
    }

    @Test
    public void testRetrieveSamplesForInvalidLocation() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(Collections.singletonList(
                ViewFilter.builder()
                        .field("location")
                        .values(Collections.singletonList(">; INSERT something"))
                        .build()
        ));
        Exception exception = assertThrows(IllegalArgumentException.class, () -> queryService.retrieveViewPage(request));
        String expectedMessage = "Invalid IRI";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    public void testRetrieveFilesForParent() {
        selectAdmin();
        var request = new FileSearchRequest();
        request.setQuery("coffee");
        request.setParentIRI(baseUri + "/coll1");

        var results = queryService.searchFiles(request);
        assertEquals(1, results.size());
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

    @Test
    public void testRetrieveSamplePageForUnaccessibleCollection() {
        var request = new ViewRequest();
        request.setView("Sample");
        request.setPage(1);
        request.setSize(10);
        request.setFilters(List.of(
                ViewFilter.builder()
                        .field("Resource_analysisType")
                        .values(List.of(ANALYSIS_TYPE_IMAGING))
                        .build()
        ));
        var page = queryService.retrieveViewPage(request);
        assertEquals(0, page.getRows().size());
    }
}
