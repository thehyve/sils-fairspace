package io.fairspace.saturn.services.users;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fairspace.saturn.rdf.dao.DAO;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Node;
import org.eclipse.jetty.client.HttpClient;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static io.fairspace.saturn.auth.SecurityUtil.authorizationHeader;
import static io.fairspace.saturn.rdf.SparqlUtils.generateMetadataIri;
import static io.fairspace.saturn.rdf.TransactionUtils.commit;
import static java.util.stream.Collectors.toList;
import static org.apache.http.HttpStatus.SC_OK;
import static org.eclipse.jetty.http.HttpHeader.AUTHORIZATION;

/**
 * Loads users from the database on start and then regularly synchronizes the cache and the database with Keycloak.
 * Loading from the database is needed to prevent unnecessary writes.
 *
 */
@Slf4j
public class UserService {
    private static final long REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(1);
    private static final ObjectMapper mapper = new ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final String usersEndpoint;
    private final DAO dao;
    private final Map<Node, User> usersByIri = new ConcurrentHashMap<>();
    private final HttpClient httpClient = new HttpClient();
    private final Timer worker = new Timer();
    private final boolean authorizationRequired;
    private volatile String authorization;

    public UserService(String usersEndpoint, DAO dao, boolean authorizationRequired) {
        this.usersEndpoint = usersEndpoint;
        this.dao = dao;
        this.authorizationRequired = authorizationRequired;

        loadUsers();

        worker.schedule(new TimerTask() {
            @Override
            public void run() {
                refreshCache();
            }
        }, 0, REFRESH_INTERVAL);
    }

    public User getUser(Node iri) {
        authorization = authorizationHeader();
        if (!usersByIri.containsKey(iri)) {
            refreshCache();
        }

        return usersByIri.get(iri);
    }


    private void refreshCache() {
        if (authorizationRequired && authorization == null) {
            return;
        }
        var users = fetchUsers();
        var updated = users
                .stream()
                .filter(user -> !user.equals(usersByIri.replace(user.getIri(), user)))
                .collect(toList());
        if (!updated.isEmpty()) {
            commit("Update user information", dao, () -> updated.forEach(dao::write));
        }

    }

    private void loadUsers() {
        dao.list(User.class).forEach(user -> usersByIri.put(user.getIri(), user));
    }

    private List<User> fetchUsers() {
        try {
            if (!httpClient.isStarted()) {
                httpClient.start();
            }
            var request = httpClient.newRequest(usersEndpoint);
            if(authorization != null) {
                request.header(AUTHORIZATION, authorization);
            }
            var response = request.send();
            if (response.getStatus() == SC_OK) {
                List<KeycloakUser> keycloakUsers = mapper.readValue(response.getContent(), new TypeReference<List<KeycloakUser>>() {});
                return keycloakUsers
                        .stream()
                        .map(keycloakUser -> {
                            var user = new User();
                            user.setIri(generateMetadataIri(keycloakUser.getId()));
                            user.setName(keycloakUser.getFullName());
                            user.setEmail(keycloakUser.getEmail());
                            return user;
                        })
                        .collect(toList());
            } else {
                log.error("Error retrieving users from {}: {} {}", usersEndpoint, response.getStatus(), response.getReason());
            }
        } catch (Exception e) {
            log.error("Error retrieving users from {}", usersEndpoint, e);
        }
        return List.of();
    }

    public Node getUserIri(String userId) {
        return generateMetadataIri(userId);
    }
}
