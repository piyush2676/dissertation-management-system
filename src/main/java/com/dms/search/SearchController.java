package com.dms.search;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Mounted outside the role prefixes: everyone searches, and what each role may
 * find is decided by the query scoping in SearchService rather than by the URL.
 */
@Controller
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("")
    public String search(@RequestParam(name = "q", required = false) String q,
                         Authentication authentication,
                         Model model) {

        model.addAttribute("results", searchService.search(
                q,
                authentication.getName(),
                AuthorityUtils.authorityListToSet(authentication.getAuthorities())));

        return "search";
    }
}
