package com.Lia.assistant.forge

object ForgeHtml {
    const val SAFETY_NET_ID = "lia-safety-net"

    /** Removes a leading ```html line and a trailing ``` line. */
    fun stripFences(text: String): String {
        var s = text.trim()
        if (s.startsWith("```")) {
            val newline = s.indexOf('\n')
            s = if (newline >= 0) s.substring(newline + 1) else ""
        }
        s = s.trimEnd()
        if (s.endsWith("```")) s = s.removeSuffix("```")
        return s.trim()
    }

    /** Fences off, chatter before the page off, anything after </html> off. */
    fun clean(text: String): String {
        var s = stripFences(text)
        if (!s.startsWith("<")) {
            val doctype = s.indexOf("<!doctype", ignoreCase = true)
            val html = s.indexOf("<html", ignoreCase = true)
            val start = listOf(doctype, html).filter { it >= 0 }.minOrNull()
            if (start != null) s = s.substring(start)
        }
        val end = s.lastIndexOf("</html>", ignoreCase = true)
        if (end >= 0) s = s.substring(0, end + "</html>".length)
        return s
    }

    fun looksLikeHtml(text: String): Boolean {
        val head = clean(text).take(600).lowercase()
        return head.startsWith("<!doctype html") || head.startsWith("<html")
    }

    fun isComplete(html: String): Boolean = html.trimEnd().endsWith("</html>", ignoreCase = true)

    /**
     * Closes what a cut-off page left open: a half tag is dropped, an unfinished <script> is removed
     * (half a script would only throw errors), an unfinished <style> is cut back to its last whole
     * rule, and </body></html> are added.
     */
    fun repairTruncated(html: String): String {
        var s = html
        val lastLt = s.lastIndexOf('<')
        val lastGt = s.lastIndexOf('>')
        if (lastLt > lastGt) s = s.substring(0, lastLt)

        while (count(s, "<script") > count(s, "</script>")) {
            val at = s.lastIndexOf("<script", ignoreCase = true)
            if (at < 0) break
            s = s.substring(0, at)
        }
        if (count(s, "<style") > count(s, "</style>")) {
            val at = s.lastIndexOf("<style", ignoreCase = true)
            val open = s.indexOf('>', at)
            val brace = s.lastIndexOf('}')
            s = (if (brace > open) s.substring(0, brace + 1) else s.substring(0, open + 1)) + "\n</style>"
        }
        if (!s.contains("</body>", ignoreCase = true)) s = s.trimEnd() + "\n</body>"
        if (!s.contains("</html>", ignoreCase = true)) s = s.trimEnd() + "\n</html>"
        return s
    }

    private fun count(text: String, needle: String): Int {
        var n = 0
        var at = 0
        while (true) {
            at = text.indexOf(needle, at, ignoreCase = true)
            if (at < 0) return n
            n++
            at += needle.length
        }
    }

    private val SAFETY_NET = """<script id="$SAFETY_NET_ID">
(function(){setTimeout(function(){try{
var vh=innerHeight,vw=innerWidth;
document.querySelectorAll('body *').forEach(function(e){
if(e.tagName==='CANVAS'||e.tagName==='SCRIPT'||e.tagName==='STYLE')return;
var s=getComputedStyle(e);
if(s.opacity!=='0')return;
if(s.position==='fixed'||s.pointerEvents==='none')return;
if(e.closest('[aria-hidden="true"]')||e.closest('canvas'))return;
var r=e.getBoundingClientRect();
if(r.width<1||r.height<1)return;
if(r.bottom<0||r.top>vh||r.right<0||r.left>vw)return;
e.style.transition='opacity .6s ease';e.style.opacity='1';
});}catch(x){}},1400);})();
</script>"""

    /**
     * Adds a small script that fades in anything stuck invisible inside the screen after 1.4 s
     * (a page that waits for an animation that never runs). Adding it twice changes nothing.
     */
    fun withSafetyNet(html: String): String {
        if (html.contains("id=\"$SAFETY_NET_ID\"")) return html
        val at = html.lastIndexOf("</body>", ignoreCase = true)
        return if (at >= 0) html.substring(0, at) + SAFETY_NET + "\n" + html.substring(at) else html + "\n" + SAFETY_NET
    }
}
