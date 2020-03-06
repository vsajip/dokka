package utils

import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.plugability.UnresolvedTypeHandler
import org.jetbrains.dokka.testApi.testRunner.AbstractCoreTest
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.model.DModule

abstract class AbstractModelTest(val path: String? = null, val pkg: String) : ModelDSL(), AssertDSL {

    fun inlineModelTest(
        query: String,
        platform: String = "jvm",
        targetList: List<String> = listOf("jvm"),
        prependPackage: Boolean = true,
        cleanupOutput: Boolean = true,
        pluginsOverrides: List<DokkaPlugin> = emptyList(),
        typeHandler: UnresolvedTypeHandler = UnresolvedTypeHandler.Exception,
        block: DModule.() -> Unit
    ) {
        val configuration = dokkaConfiguration {
            unresolvedTypeHandler = typeHandler
            passes {
                pass {
                    sourceRoots = listOf("src/")
                    analysisPlatform = platform
                    targets = targetList
                }
            }
        }
        val prepend = path.let { p -> p?.let { "|$it\n" } ?: "" } + if (prependPackage) "|package $pkg" else ""

        testInline(
            query = ("$prepend\n$query").trim().trimIndent(),
            configuration = configuration,
            cleanupOutput = cleanupOutput,
            pluginOverrides = pluginsOverrides
        ) {
            documentablesTransformationStage = block
        }
    }


}
