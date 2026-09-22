package com.dms.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The importer reads a real department list, so the part worth pinning is the
 * bit that decides identity: two rows naming the same guide must land on the
 * same account, and nothing derived may leak a real contact address.
 */
class CohortImporterTest {

    @Test
    void aTitleIsStrippedAndTheNameBecomesTheAddress() {
        assertEquals("hitesh.singh@college.edu", CohortImporter.emailFor("Dr. Hitesh Singh"));
        assertEquals("roshni.prasad@college.edu", CohortImporter.emailFor("Ms. Roshni Prasad"));
        assertEquals("pradeep.kumar@college.edu", CohortImporter.emailFor("Mr Pradeep Kumar"));
    }

    @Test
    void theSameGuideWrittenTwoWaysIsTheSamePerson() {
        // The sheet has both "Dr. Megha Gupta" and "Dr.<nbsp>Megha Gupta", and
        // casing wanders between rows. Each must resolve to one account, or a
        // guide ends up supervising their own students twice over.
        String plain = CohortImporter.emailFor("Dr. Megha Gupta");
        assertEquals(plain, CohortImporter.emailFor("Dr. Megha Gupta"));
        assertEquals(plain, CohortImporter.emailFor("DR. MEGHA GUPTA"));
        assertEquals(plain, CohortImporter.emailFor("  Dr.   Megha  Gupta  "));
    }

    @Test
    void aNameWithNoTitleStillResolves() {
        assertEquals("raju@college.edu", CohortImporter.emailFor("Raju"));
    }

    @Test
    void generatedAddressesNeverCarryTheInstituteDomain() {
        // The source sheet holds real mail ids like 0221MCSD006@niet.co.in. None
        // of them are imported, and nothing generated may look like one.
        for (String name : new String[]{"Dr. Hitesh Singh", "Ms Sana Anjum", "Mr. Ibrar Ahmad"}) {
            String email = CohortImporter.emailFor(name);
            assertTrue(email.endsWith(CohortImporter.MAIL_DOMAIN), email);
            assertFalse(email.contains("niet.co.in"), email);
        }
    }
}
