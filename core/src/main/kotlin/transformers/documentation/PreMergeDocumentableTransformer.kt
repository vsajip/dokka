package org.jetbrains.dokka.transformers.documentation

import org.jetbrains.dokka.model.Module
import org.jetbrains.dokka.plugability.DokkaContext

interface PreMergeDocumentableTransformer {
    operator fun invoke(modules: List<Module>, context: DokkaContext): List<Module>
}