package com.dms.user;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An unordered runner sorts last, so without an explicit order the cohort import filled the
 * users table first and the seeder, which needs an empty one, skipped the demo accounts.
 */
class StartupOrderTest {

    @Test
    void seederRunsBeforeTheCohortImporter() {
        int seeder = OrderUtils.getOrder(DataSeeder.class, Ordered.LOWEST_PRECEDENCE);
        int importer = OrderUtils.getOrder(CohortImporter.class, Ordered.LOWEST_PRECEDENCE);
        assertThat(seeder).isLessThan(importer);
    }
}
