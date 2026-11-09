package com.markethub.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

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
        AtomicInteger executedStatements = new AtomicInteger();
        Statement statement = proxy(Statement.class, (method, args) -> {
            if (method.getName().equals("execute")) {
                executedStatements.incrementAndGet();
                return false;
            }
            if (method.getName().equals("getUpdateCount")) return -1;
            return defaultValue(method.getReturnType());
        });
        Connection connection = proxy(Connection.class, (method, args) -> {
            if (method.getName().equals("createStatement")) return statement;
            return defaultValue(method.getReturnType());
        });
        DataSource dataSource = proxy(DataSource.class, (method, args) -> {
            if (method.getName().equals("getConnection")) return connection;
            return defaultValue(method.getReturnType());
        });

        assertTrue(new ClassPathResource("db/demo-seed.sql").exists());
        new DemoDataSeeder(dataSource).run(new DefaultApplicationArguments(new String[0]));
        assertTrue(executedStatements.get() >= 20,
                "Expected the bundled seed script to execute at least 20 SQL statements");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, SqlInvocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(method, args));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\\0';
        if (type == byte.class) return (byte)  0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    @FunctionalInterface
    private interface SqlInvocation {
        Object invoke(Method method, Object[] args) throws Throwable;
    }
}
