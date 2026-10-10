package com.Lia.assistant.forge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeStageDetectorTest {
    private val head = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
        "<script src=\"https://cdn.tailwindcss.com\"></script>" +
        "<style>@keyframes spin{to{transform:rotate(1turn)}} .a{color:red}</style></head>"

    private fun detect(s: String) = ForgeStageDetector.detect(s)

    // ---- stages ----
    @Test fun nothingYetIsPlanning() = assertEquals(ForgeStage.PLANNING, detect(""))

    @Test fun aHeadOnlyPageStaysAtTheStart() {
        // Even a head with a style block and keyframes is still planning: the body has not begun.
        assertEquals(ForgeStage.PLANNING, detect(head))
        assertEquals(ForgeStage.PLANNING, detect("<!DOCTYPE html><html><head><title>x</title>"))
    }

    @Test fun theBodyStartsTheStructure() =
        assertEquals(ForgeStage.STRUCTURE, detect("$head<body class=\"bg-black\"><header><nav>"))

    @Test fun manyClassesMeanStyling() {
        val body = (1..30).joinToString("") { "<div class=\"p-$it\"></div>" }
        assertEquals(ForgeStage.STYLING, detect("$head<body>$body"))
        assertEquals(ForgeStage.STRUCTURE, detect("$head<body><div class=\"a\"></div>"))
    }

    @Test fun aCanvasCountsAsVisuals() =
        assertEquals(ForgeStage.VISUALS, detect("$head<body><canvas id=\"hero\"></canvas>"))

    @Test fun imagesAndSvgCountAsVisualsToo() {
        assertEquals(ForgeStage.VISUALS, detect("$head<body><img src=\"https://picsum.photos/seed/a/400/300\">"))
        assertEquals(ForgeStage.VISUALS, detect("$head<body><svg viewBox=\"0 0 1 1\"></svg>"))
        assertEquals(ForgeStage.VISUALS, detect("$head<body><script type=\"module\">import * as THREE from 'three';"))
    }

    @Test fun scriptsForAnimationMeanMotion() {
        assertEquals(ForgeStage.MOTION, detect("$head<body><img src=\"a.jpg\"><script>gsap.from('.a',{y:20})"))
        assertEquals(ForgeStage.MOTION, detect("$head<body><script>new IntersectionObserver(()=>{})"))
        assertEquals(ForgeStage.MOTION, detect("$head<body><style>@keyframes pulse{}</style>"))
    }

    @Test fun theClosingTagsMeanFinishing() {
        assertEquals(ForgeStage.FINISHING, detect("$head<body><p>x</p></body>"))
        assertEquals(ForgeStage.FINISHING, detect("$head<body><p>x</p></body></html>"))
    }

    @Test fun theStageNeverGoesBackwardsWhileThePageGrows() {
        val page = head + "<body class=\"bg-black\"><header class=\"a\"><nav class=\"b\">Home</nav></header>" +
            (1..6).joinToString("") { "<section id=\"s$it\" class=\"py-20 px-6\"><h2 class=\"text-4xl\">Part $it</h2><div class=\"grid gap-4\"></div></section>" } +
            "<img src=\"https://picsum.photos/seed/a/400/300\" alt=\"x\">" +
            "<script src=\"https://cdnjs.cloudflare.com/gsap.min.js\"></script><script>gsap.from('.a',{y:10});</script>" +
            "</body></html>"
        var previous = ForgeStage.PLANNING
        val seen = linkedSetOf<ForgeStage>()
        var at = 0
        while (at <= page.length) {
            val stage = detect(page.substring(0, at))
            assertTrue("went back at $at: $previous -> $stage", stage.ordinal >= previous.ordinal)
            previous = stage
            seen += stage
            at += 40
        }
        assertEquals(ForgeStage.FINISHING, detect(page))
        assertTrue(ForgeStage.PLANNING in seen)
        assertTrue(ForgeStage.STRUCTURE in seen)
        assertTrue(ForgeStage.MOTION in seen)
    }

    @Test fun thereAreSixStagesWithLabels() {
        assertEquals(6, ForgeStage.entries.size)
        assertEquals(
            listOf("Planning", "Structure", "Styling", "Visuals", "Motion", "Finishing"),
            ForgeStage.entries.map { it.label },
        )
    }

    // ---- section names ----
    @Test fun sectionsAreNamedFromIdsLabelsAndHeadings() {
        val html = "<body><header class=\"x\">logo</header>" +
            "<section id=\"hero\" class=\"a\"><h1>Welcome</h1></section>" +
            "<section class=\"b\" aria-label=\"our-story\"></section>" +
            "<section class=\"c\"><h2 class=\"t\">Pricing <span>plans</span></h2></section>" +
            "<section class=\"d\"><p>no name here</p></section>" +
            "<footer>bye</footer>"
        assertEquals(
            listOf("Header", "Hero", "Our Story", "Pricing plans", "Section 4", "Footer"),
            ForgeStageDetector.sections(html),
        )
    }

    @Test fun aHeadingOfTheNextSectionIsNotBorrowed() {
        val html = "<section class=\"a\"><p>x</p></section><section class=\"b\"><h2>Real name</h2></section>"
        assertEquals(listOf("Section 1", "Real name"), ForgeStageDetector.sections(html))
    }

    @Test fun aTagThatIsStillBeingWrittenIsNotCountedYet() {
        assertEquals(emptyList<String>(), ForgeStageDetector.sections("<body><section id=\"ab"))
        assertEquals(listOf("About"), ForgeStageDetector.sections("<body><section id=\"about\""+">"))
    }

    @Test fun tagCaseDoesNotMatter() =
        assertEquals(listOf("Header", "Gallery"), ForgeStageDetector.sections("<HEADER></HEADER><SECTION ID=\"gallery\">"))

    // ---- progress ----
    @Test fun progressStartsLowAndNeverReachesOneBeforeTheEnd() {
        assertEquals(0.03f, ForgeStageDetector.progress(ForgeStage.PLANNING, 0), 0.0001f)
        assertEquals(0.97f, ForgeStageDetector.progress(ForgeStage.FINISHING, 1_000_000), 0.0001f)
    }

    @Test fun progressOnlyGrowsWithStageAndLength() {
        var last = -1f
        for (stage in ForgeStage.entries) {
            for (chars in listOf(0, 5_000, 20_000, 50_000)) {
                val p = ForgeStageDetector.progress(stage, chars)
                assertTrue("$stage $chars", p >= 0.03f && p <= 0.97f)
                if (chars == 0) {
                    assertTrue(p >= last)
                    last = p
                }
            }
        }
        assertTrue(ForgeStageDetector.progress(ForgeStage.STYLING, 10_000) < ForgeStageDetector.progress(ForgeStage.STYLING, 20_000))
    }
}
