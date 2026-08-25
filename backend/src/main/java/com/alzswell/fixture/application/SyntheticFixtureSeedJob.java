package com.alzswell.fixture.application;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.synthetic-seed.enabled", havingValue = "true")
public class SyntheticFixtureSeedJob {
    private static final Log LOG = LogFactory.getLog(SyntheticFixtureSeedJob.class);

    private final SyntheticFixtureGenerationService service;
    private final ConfigurableApplicationContext applicationContext;
    private final SyntheticFixtureProfile profile;
    private final String fixtureVersion;
    private final long seed;
    private final int batchSize;
    private final boolean resume;

    public SyntheticFixtureSeedJob(
            SyntheticFixtureGenerationService service,
            ConfigurableApplicationContext applicationContext,
            @Value("${app.synthetic-seed.profile:SMOKE}") SyntheticFixtureProfile profile,
            @Value("${app.synthetic-seed.fixture-version:synthetic-v3.0.0}") String fixtureVersion,
            @Value("${app.synthetic-seed.seed:20260825}") long seed,
            @Value("${app.synthetic-seed.batch-size:10}") int batchSize,
            @Value("${app.synthetic-seed.resume:false}") boolean resume
    ) {
        this.service = service;
        this.applicationContext = applicationContext;
        this.profile = profile;
        this.fixtureVersion = fixtureVersion;
        this.seed = seed;
        this.batchSize = batchSize;
        this.resume = resume;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void generateAndClose() {
        SyntheticFixtureGenerationService.GenerationResult result =
                service.generate(profile, fixtureVersion, seed, batchSize, resume);
        LOG.info("synthetic fixture generation completed: runId=" + result.runId()
                + ", profile=" + result.profile() + ", customers=" + result.actualCustomerCount()
                + ", accounts=" + result.actualAccountCount()
                + ", transactions=" + result.actualTransactionCount()
                + ", replayed=" + result.replayed());
        applicationContext.close();
    }
}
