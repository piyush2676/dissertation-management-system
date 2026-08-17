
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
})();
