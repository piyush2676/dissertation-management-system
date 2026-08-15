/* ==========================================================================
   Dissertation Management System — client script

   Deliberately tiny and dependency-free. Everything here is decoration on top
   of markup that already works: with JavaScript disabled the pages render and
   every form still posts. No framework, no CDN, nothing to load over a network
   that may not exist in a lab.
   ========================================================================== */

(function () {
    "use strict";

    /* ---- Ambient clock ---------------------------------------------------
       Rendered client-side on purpose. The server's clock is the server's
       timezone; the strip is meant to read as the room the user is sitting in,
       so it comes from the browser. Format matches the rest of the chrome:
       lowercase, spaced, no colon -- "10 37 pm".
       -------------------------------------------------------------------- */

    function clockText(now) {
        var hours = now.getHours();
        var suffix = hours >= 12 ? "pm" : "am";
        var display = hours % 12;
        if (display === 0) { display = 12; }
        return display + " " + String(now.getMinutes()).padStart(2, "0") + " " + suffix;
    }

    /* ---- Time of day -----------------------------------------------------
       The illustration answers to the clock. Four parts of the day, written to
       the root element as a data attribute; app.css does the rest, so the only
       thing this file decides is which word applies.

       This is why the ambient clock earns its place: it is not decoration, it
       drives what the page looks like. A student opening the site at 11pm sees
       the shop lit and the sky gone; at 6am it is washed pink.
       -------------------------------------------------------------------- */

    function dayPart(hour) {
        if (hour < 6)  { return "night"; }
        if (hour < 9)  { return "dawn"; }
        if (hour < 17) { return "day"; }
        if (hour < 20) { return "dusk"; }
        return "night";
    }

    /* ?daypart=night forces one, for demoing at 3pm what the site looks like
       at midnight. Anything unrecognised falls back to the real clock, so a
       stray query string can never leave the page in a broken state. */
    var FORCED = (function () {
        var allowed = ["dawn", "day", "dusk", "night"];
        var value = new URLSearchParams(window.location.search).get("daypart");
        return allowed.indexOf(value) === -1 ? null : value;
    })();

    function paint() {
        var now = new Date();

        var nodes = document.querySelectorAll("[data-clock]");
        var text = clockText(now);
        for (var i = 0; i < nodes.length; i++) {
            nodes[i].textContent = text;
        }

        document.documentElement.setAttribute("data-daypart", FORCED || dayPart(now.getHours()));
    }

    paint();
    window.setInterval(paint, 20000);

    /* ---- Scene parallax --------------------------------------------------
       Writes the pointer position onto the hero as two custom properties in
       the range -1..1. CSS decides what to do with them: the far plane drifts,
       the near plane moves further, the headline moves against both.

       Everything is funnelled through requestAnimationFrame, so a mousemove
       storm still costs one style write per frame. Nothing here reads layout,
       so nothing here can force a reflow.

       Skipped entirely for touch pointers and for anyone who has asked for
       reduced motion -- in both cases the CSS already pins the planes still,
       and this simply never writes to them.
       -------------------------------------------------------------------- */

    function initParallax() {
        var hero = document.querySelector(".hero-wash");
        if (!hero) { return; }

        var fine = window.matchMedia("(hover: hover)").matches;
        var calm = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        if (!fine || calm) { return; }

        var pending = false;
        var px = 0;
        var py = 0;

        function apply() {
            pending = false;
            hero.style.setProperty("--px", px.toFixed(3));
            hero.style.setProperty("--py", py.toFixed(3));
        }

        window.addEventListener("mousemove", function (event) {
            px = (event.clientX / window.innerWidth) * 2 - 1;
            py = (event.clientY / window.innerHeight) * 2 - 1;
            if (!pending) {
                pending = true;
                window.requestAnimationFrame(apply);
            }
        });

        /* Pointer leaving the window returns the scene to rest rather than
           freezing it mid-drift. */
        document.addEventListener("mouseleave", function () {
            px = 0;
            py = 0;
            if (!pending) {
                pending = true;
                window.requestAnimationFrame(apply);
            }
        });
    }

    initParallax();

    /* ---- Print a record --------------------------------------------------
       Any element carrying data-print asks the browser to print the page. The
       print stylesheet turns the topic page into a filed memo, so this is the
       whole implementation -- no PDF library, no server round trip.
       -------------------------------------------------------------------- */

    document.addEventListener("click", function (event) {
        var trigger = event.target.closest ? event.target.closest("[data-print]") : null;
        if (!trigger) { return; }
        event.preventDefault();
        window.print();
    });
})();
