package org.jetbrains.dokka.base.renderers.jekyll

import org.jetbrains.dokka.base.renderers.gfm.GfmRenderer
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.plugability.DokkaContext
import java.lang.StringBuilder


class JekyllRenderer(context: DokkaContext) : GfmRenderer(context) {

    override fun buildPage(page: ContentPage, content: (StringBuilder, ContentPage) -> Unit): String {
        val builder = StringBuilder()
        builder.append("---\n")
        builder.append("title: ${page.name} -\n")
        builder.append("---\n")
        content(builder, page)
        return builder.toString()
    }
}