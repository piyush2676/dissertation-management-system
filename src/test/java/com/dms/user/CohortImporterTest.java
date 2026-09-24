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
        assertEquals("hitesh.singh@niet.co.in", CohortImporter.emailFor("Dr. Hitesh Singh"));
        assertEquals("roshni.prasad@niet.co.in", CohortImporter.emailFor("Ms. Roshni Prasad"));
        assertEquals("pradeep.kumar@niet.co.in", CohortImporter.emailFor("Mr Pradeep Kumar"));
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
        assertEquals("raju@niet.co.in", CohortImporter.emailFor("Raju"));
    }

    @Test
    void generatedAddressesAreDerivedOnTheInstituteErpDomain() {
        // Since 2026-09-24 sign-in is by ERP address on @niet.co.in. The addresses are
        // still derived from the name, never read from the sheet's mail-id column.
        for (String name : new String[]{"Dr. Hitesh Singh", "Ms Sana Anjum", "Mr. Ibrar Ahmad"}) {
            String email = CohortImporter.emailFor(name);
            assertTrue(email.endsWith("@niet.co.in"), email);
            assertFalse(email.contains("@gmail.com"), email);
        }
    }
}
