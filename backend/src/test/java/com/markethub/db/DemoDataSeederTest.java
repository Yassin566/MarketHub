package com.markethub.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

class DemoDataSeederTest {

    @Test
    void seederIsRestrictedToTheExplicitDevelopmentProfile() {
        Profile profile = DemoDataSeeder.class.getAnnotation(Profile.class);

        assertTrue(profile != null, "Seeder must be guarded by a Spring profile");
        assertArrayEquals(new String[]{"dev"}, profile.value());
    }

    @Test
    void executesTheBundledDemoSeedScript() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        assertTrue(new ClassPathResource("db/demo-seed.sql").exists());

        new DemoDataSeeder(dataSource).run(new DefaultApplicationArguments(new String[0]));

        verify(statement, atLeast(20)).execute(anyString());
    }
}
