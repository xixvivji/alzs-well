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
    private final SyntheticFixtureQualityService qualityService;
    private final SyntheticFixtureProfile profile;
    private final String fixtureVersion;
    private final long seed;
    private final int batchSize;
    private final boolean resume;
    private final boolean verifyDetection;
    private final boolean provisionMembers;
    private final String memberPasswordHash;
    private final SyntheticMemberProvisioningService memberProvisioningService;

    public SyntheticFixtureSeedJob(
            SyntheticFixtureGenerationService service,
            ConfigurableApplicationContext applicationContext,
            SyntheticFixtureQualityService qualityService,
            @Value("${app.synthetic-seed.profile:SMOKE}") SyntheticFixtureProfile profile,
            @Value("${app.synthetic-seed.fixture-version:synthetic-v3.0.0}") String fixtureVersion,
            @Value("${app.synthetic-seed.seed:20260825}") long seed,
            @Value("${app.synthetic-seed.batch-size:10}") int batchSize,
            @Value("${app.synthetic-seed.resume:false}") boolean resume,
            @Value("${app.synthetic-seed.verify-detection:false}") boolean verifyDetection,
            @Value("${app.synthetic-seed.provision-members:false}") boolean provisionMembers,
            @Value("${app.synthetic-seed.member-password-hash:}") String memberPasswordHash,
            SyntheticMemberProvisioningService memberProvisioningService
    ) {
        this.service = service;
        this.applicationContext = applicationContext;
        this.qualityService = qualityService;
        this.profile = profile;
        this.fixtureVersion = fixtureVersion;
        this.seed = seed;
        this.batchSize = batchSize;
        this.resume = resume;
        this.verifyDetection = verifyDetection;
        this.provisionMembers = provisionMembers;
        this.memberPasswordHash = memberPasswordHash;
        this.memberProvisioningService = memberProvisioningService;
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
        if (verifyDetection) {
            SyntheticFixtureQualityService.QualityReport quality = qualityService.evaluate(result.runId());
            LOG.info("synthetic fixture detection quality completed: status=" + quality.status()
                    + ", evaluatedCustomers=" + quality.evaluatedCustomerCount()
                    + ", expectedSignals=" + quality.expectedSignalCount()
                    + ", actualSignals=" + quality.actualSignalCount()
                    + ", falsePositives=" + quality.falsePositiveCount()
                    + ", falseNegatives=" + quality.falseNegativeCount()
                    + ", replayed=" + quality.replayed());
            if (!"PASSED".equals(quality.status())) {
                throw new IllegalStateException("합성 fixture 탐지 품질 검증을 통과하지 못했습니다.");
            }
        }
        if (provisionMembers) {
            SyntheticMemberProvisioningService.ProvisioningResult members =
                    memberProvisioningService.provision(result.runId(), memberPasswordHash);
            LOG.info("public synthetic member provisioning completed: runId=" + members.runId()
                    + ", activeMembers=" + members.activeMembers());
        }
        applicationContext.close();
    }
}
