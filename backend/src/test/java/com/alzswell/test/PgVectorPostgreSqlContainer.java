package com.alzswell.test;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PgVectorPostgreSqlContainer extends PostgreSQLContainer<PgVectorPostgreSqlContainer> {
    private static final DockerImageName IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.1-pg17")
            .asCompatibleSubstituteFor("postgres");

    public PgVectorPostgreSqlContainer() {
        super(IMAGE);
    }
}
