package com.dms.security;

import com.dms.web.DashboardController;
import com.dms.web.DashboardService;
import com.dms.web.Dashboards;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The URL-level half of the security model: a role reaches its own area and
 * nothing else.
 *
 * <p>Ownership -- a guide reading another guide's student -- is the service's job
 * and is covered in the service tests, because a role check alone can never
 * express it.
 */
@WebMvcTest(controllers = DashboardController.class)
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private DashboardService dashboardService;
    @MockitoBean private CustomUserDetailsService userDetailsService;
    @MockitoBean private AuthzService authzService;

    // ---- anonymous ----------------------------------------------------------

    @Test
    @WithAnonymousUser
    void theLandingPageAndLoginAreOpen() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void anAnonymousUserIsSentToLoginNotShownThePage() throws Exception {
        mvc.perform(get("/student/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ---- each role reaches its own area -------------------------------------

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentReachesTheStudentDashboard() throws Exception {
        when(dashboardService.student(anyString())).thenReturn(studentBoard());
        mvc.perform(get("/student/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void aGuideReachesTheSupervisorDashboard() throws Exception {
        when(dashboardService.supervisor(anyString()))
                .thenReturn(new Dashboards.Supervisor(0, 0, 0, 0, 5));
        mvc.perform(get("/supervisor/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void aCoordinatorReachesTheCoordinatorDashboard() throws Exception {
        when(dashboardService.coordinator())
                .thenReturn(new Dashboards.Coordinator("2026-27", 0, 0, 0, 0, 0, 0));
        mvc.perform(get("/coordinator/dashboard")).andExpect(status().isOk());
    }

    // ---- and no further -----------------------------------------------------

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentIsForbiddenFromTheCoordinatorArea() throws Exception {
        mvc.perform(get("/coordinator/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentIsForbiddenFromTheAdminArea() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void aGuideIsForbiddenFromTheStudentArea() throws Exception {
        mvc.perform(get("/student/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void aCoordinatorIsForbiddenFromTheSupervisorArea() throws Exception {
        mvc.perform(get("/supervisor/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void evenAnAdminDoesNotInheritTheOtherAreas() throws Exception {
        mvc.perform(get("/student/dashboard")).andExpect(status().isForbidden());
        mvc.perform(get("/supervisor/dashboard")).andExpect(status().isForbidden());
    }

    // ---- a user may hold several roles --------------------------------------

    @Test
    @WithMockUser(roles = {"SUPERVISOR", "REVIEWER"})
    void aGuideWhoIsAlsoAReviewerStillReachesTheSupervisorArea() throws Exception {
        when(dashboardService.supervisor(anyString()))
                .thenReturn(new Dashboards.Supervisor(0, 0, 0, 0, 5));
        mvc.perform(get("/supervisor/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN", "COORDINATOR"})
    void theDispatcherRoutesToTheMostPrivilegedRoleHeld() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    @WithMockUser(roles = {"SUPERVISOR", "STUDENT"})
    void theDispatcherPrefersSupervisorOverStudent() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(redirectedUrl("/supervisor/dashboard"));
    }

    // ---- CSRF ---------------------------------------------------------------

    @Test
    @WithMockUser(roles = "STUDENT")
    void aPostWithoutACsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/logout")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aPostWithACsrfTokenIsAccepted() throws Exception {
        mvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private Dashboards.Student studentBoard() {
        return new Dashboards.Student("2026-27", null, null, null, null, 0, 0, 0, 0, 0, null, null);
    }
}
