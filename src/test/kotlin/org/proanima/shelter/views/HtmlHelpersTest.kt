package org.proanima.shelter.views

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlHelpersTest {

    @Test
    fun `escapes html special characters`() {
        val result = escapeHtml("""<script>alert("x")</script> & 'test'""")

        assertEquals(
            "&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; &#39;test&#39;",
            result
        )
    }
}