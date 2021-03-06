package io.fairspace.saturn.services.health;

import com.zaxxer.hikari.*;
import io.fairspace.saturn.config.Config;
import io.fairspace.saturn.config.ConfigLoader;
import io.fairspace.saturn.config.ViewsConfig;
import io.fairspace.saturn.services.views.ViewStoreClientFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.sql.SQLException;

@RunWith(MockitoJUnitRunner.class)
public class HealthServiceTest {
    HealthService healthService;
    ViewStoreClientFactory viewStoreClientFactory;

    @Before
    public void before() throws SQLException, NotAuthorizedException, BadRequestException, ConflictException, IOException {
        var viewDatabase = new Config.ViewDatabase();
        viewDatabase.url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        viewDatabase.username = "sa";
        viewDatabase.password = "";
        ViewsConfig config = ConfigLoader.VIEWS_CONFIG;
        ViewStoreClientFactory.H2_DATABASE = true;
        viewStoreClientFactory = new ViewStoreClientFactory(config, viewDatabase);

        healthService = new HealthService(viewStoreClientFactory.dataSource);
    }

    @Test
    public void testRetrieveStatus_UP() {
        healthService = new HealthService(null);
        var health = healthService.getHealth();

        Assert.assertEquals(health.getStatus(), HealthStatus.UP);
        Assert.assertTrue(health.getComponents().isEmpty());
    }

    @Test
    public void testRetrieveStatusWithViewDatabase_UP() {
        var health = healthService.getHealth();

        Assert.assertEquals(health.getStatus(), HealthStatus.UP);
        Assert.assertEquals(health.getComponents().size(), 1);
        Assert.assertEquals(health.getComponents().get("viewDatabase"), HealthStatus.UP);
    }

    @SneakyThrows
    @Test
    public void testRetrieveStatusWithViewDatabase_DOWN() {
        ((HikariDataSource)viewStoreClientFactory.dataSource).close();
        var health = healthService.getHealth();

        Assert.assertEquals(health.getStatus(), HealthStatus.DOWN);
        Assert.assertEquals(health.getComponents().size(), 1);
        Assert.assertEquals(health.getComponents().get("viewDatabase"), HealthStatus.DOWN);
    }
}
