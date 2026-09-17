package com.dms.session;

import com.dms.user.Programme;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DissertationPhaseTest {

    @Test
    void thirdSemesterMTechIsPreDissertation() {
        assertEquals(Optional.of(DissertationPhase.PRE),
                DissertationPhase.forSemester(Programme.MTECH, 3));
    }

    @Test
    void fourthSemesterMTechIsFinalDissertation() {
        assertEquals(Optional.of(DissertationPhase.FINAL),
                DissertationPhase.forSemester(Programme.MTECH, 4));
    }

    @Test
    void ninthAndTenthSemestersCarryTheIntegratedProgramme() {
        assertEquals(Optional.of(DissertationPhase.PRE),
                DissertationPhase.forSemester(Programme.BTECH_MTECH_INTEGRATED, 9));
        assertEquals(Optional.of(DissertationPhase.FINAL),
                DissertationPhase.forSemester(Programme.BTECH_MTECH_INTEGRATED, 10));
    }

    @Test
    void aSemesterOutsideTheDissertationYearsHasNoPhase() {
        assertTrue(DissertationPhase.forSemester(Programme.MTECH, 2).isEmpty());
        assertTrue(DissertationPhase.forSemester(Programme.BTECH_MTECH_INTEGRATED, 4).isEmpty(),
                "semester 4 is a dissertation semester for M.Tech only, not the integrated programme");
    }

    @Test
    void aMissingSemesterOrProgrammeHasNoPhaseRatherThanThrowing() {
        assertTrue(DissertationPhase.forSemester(Programme.MTECH, null).isEmpty());
        assertTrue(DissertationPhase.forSemester(null, 3).isEmpty());
    }
}
