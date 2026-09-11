
(function () {
    "use strict";

    function clockText(now) {
        var hours = now.getHours();
        var suffix = hours >= 12 ? "pm" : "am";
        var display = hours % 12;
        if (display === 0) { display = 12; }
        return display + " " + String(now.getMinutes()).padStart(2, "0") + " " + suffix;
    }

    function dayPart(hour) {
        if (hour < 6)  { return "night"; }
        if (hour < 9)  { return "dawn"; }
        if (hour < 17) { return "day"; }
        if (hour < 20) { return "dusk"; }
        return "night";
    }

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

    document.addEventListener("click", function (event) {
        var trigger = event.target.closest ? event.target.closest("[data-print]") : null;
        if (!trigger) { return; }
        event.preventDefault();
        window.print();
    });
    /* Text-size controls, as the institute site offers. Scales the root font
       size, which every rem-based rule follows, and remembers the choice. */
    (function initTextScale() {
        var KEY = "dms-text-scale";
        var MIN = 87.5;
        var MAX = 125;
        var STEP = 6.25;

        function apply(percent) {
            document.documentElement.style.fontSize = percent + "%";
            try { window.localStorage.setItem(KEY, String(percent)); } catch (ignored) {}
        }

        function current() {
            var stored = null;
            try { stored = window.localStorage.getItem(KEY); } catch (ignored) {}
            var value = parseFloat(stored);
            return isNaN(value) ? 100 : value;
        }

        if (current() !== 100) { apply(current()); }

        document.addEventListener("click", function (event) {
            var trigger = event.target.closest ? event.target.closest("[data-text-scale]") : null;
            if (!trigger) { return; }
            var mode = trigger.getAttribute("data-text-scale");
            if (mode === "reset") { apply(100); return; }
            var next = current() + (mode === "up" ? STEP : -STEP);
            apply(Math.min(MAX, Math.max(MIN, next)));
        });
    })();

    /* The rail button reveals the workflow links on a narrow screen, where the
       second header row is hidden. */
    document.addEventListener("click", function (event) {
        var trigger = event.target.closest ? event.target.closest("[data-rail-menu]") : null;
        if (!trigger) { return; }
        var links = document.querySelector(".nav-links");
        if (links) { links.classList.toggle("open"); }
    });
})();
