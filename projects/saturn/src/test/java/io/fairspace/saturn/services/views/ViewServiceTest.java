package io.fairspace.saturn.services.views;

import io.fairspace.saturn.config.Config;
import io.fairspace.saturn.config.ConfigLoader;
import io.fairspace.saturn.config.ViewsConfig;
import io.fairspace.saturn.rdf.transactions.SimpleTransactions;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.rdf.transactions.TxnIndexDatasetGraph;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.metadata.MetadataService;
import io.fairspace.saturn.services.metadata.validation.ComposedValidator;
import io.fairspace.saturn.services.metadata.validation.DeletionValidator;
import io.fairspace.saturn.services.users.User;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.webdav.BlobStore;
import io.fairspace.saturn.webdav.DavFactory;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.MakeCollectionableResource;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.util.Context;
import org.eclipse.jetty.server.Authentication;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.sql.SQLException;
import java.util.stream.Collectors;

import static io.fairspace.saturn.TestUtils.*;
import static io.fairspace.saturn.auth.RequestContext.getCurrentRequest;
import static io.fairspace.saturn.config.Services.FS_ROOT;
import static io.fairspace.saturn.rdf.ModelUtils.EMPTY_MODEL;
import static io.fairspace.saturn.vocabulary.Vocabularies.VOCABULARY;
import static org.apache.jena.query.DatasetFactory.wrap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ViewServiceTest {
    private static final String BASE_PATH = "/api/webdav";
    private static final String baseUri = ConfigLoader.CONFIG.publicUrl + BASE_PATH;

    @Mock
    BlobStore store;
    @Mock
    UserService userService;
    @Mock
    private MetadataPermissions permissions;
    MetadataService api;
    ViewService viewService;

    @Before
    public void before() throws SQLException, BadRequestException, ConflictException, NotAuthorizedException, IOException {
        var dsg = DatasetGraphFactory.createTxnMem();
        Dataset ds = wrap(dsg);
        Transactions tx = new SimpleTransactions(ds);
        Model model = ds.getDefaultModel();

        var context = new Context();

        var davFactory = new DavFactory(model.createResource(baseUri), store, userService, context);
        ds.getContext().set(FS_ROOT, davFactory.root);

        viewService = new ViewService(ConfigLoader.CONFIG.search, ConfigLoader.VIEWS_CONFIG, ds, null);

        when(permissions.canWriteMetadata(any())).thenReturn(true);
        api = new MetadataService(tx, VOCABULARY, new ComposedValidator(new DeletionValidator()), permissions, davFactory);

        setupRequestContext();
        var request = getCurrentRequest();

        var taxonomies = model.read("taxonomies.ttl");
        api.put(taxonomies);

        User user = createTestUser("user", true);
        Authentication.User userAuthentication = mockAuthentication("admin");
        lenient().when(request.getAuthentication()).thenReturn(userAuthentication);
        lenient().when(userService.currentUser()).thenReturn(user);

        when(request.getHeader("Entity-Type")).thenReturn("https://sils.uva.nl/ontology#Department");

        var root = (MakeCollectionableResource) ((ResourceFactory) davFactory).getResource(null, BASE_PATH);
        root.createCollection("Dep1");

        var testdata = model.read("testdata.ttl");
        api.put(testdata);
    }

    @Test
    public void testFetchViewConfig() {
        var facets = viewService.getFacets();
        var selection = facets.stream()
                .filter(facet -> facet.getType() == ViewsConfig.ColumnType.Number)
                .collect(Collectors.toList());
        Assert.assertEquals(1, selection.size());
        viewService.getViews();
    }
}
