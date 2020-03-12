package org.jetbrains.dokka.base.transformers.documentables

import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.Annotation
import org.jetbrains.dokka.model.Enum
import org.jetbrains.dokka.model.Function
import org.jetbrains.dokka.pages.PlatformData
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.PreMergeDocumentableTransformer

internal object DocumentableVisibilityFilter : PreMergeDocumentableTransformer {
    override fun invoke(modules: List<Module>, context: DokkaContext): List<Module> = modules.map { original ->
        filterPackages(original.packages).let { (modified, packages) ->
            if (!modified) original
            else
                Module(
                    original.name,
                    packages = packages,
                    documentation = original.documentation,
                    platformData = original.platformData,
                    extra = original.extra
                )
        }
    }

    private fun filterPackages(packages: List<Package>): Pair<Boolean, List<Package>> {
        var packagesListChanged = false
        val filteredPackages = packages.mapNotNull {
            var modified = false
            val functions = filterFunctions(it.functions).let { (listModified, list) ->
                modified = modified || listModified
                list
            }
            val properties = filterProperties(it.properties).let { (listModified, list) ->
                modified = modified || listModified
                list
            }
            val classlikes = filterClasslikes(it.classlikes).let { (listModified, list) ->
                modified = modified || listModified
                list
            }
            when {
                !modified -> it
                functions.isEmpty() && properties.isEmpty() && classlikes.isEmpty() -> null
                else -> {
                    packagesListChanged = true
                    Package(
                        it.dri,
                        functions,
                        properties,
                        classlikes,
                        it.documentation,
                        it.platformData,
                        it.extra
                    )
                }
            }
        }
        return Pair(packagesListChanged, filteredPackages)
    }

    private fun <T : WithVisibility> alwaysTrue(a: T, p: PlatformData) = true
    private fun <T : WithVisibility> alwaysFalse(a: T, p: PlatformData) = false

    private fun <T : WithVisibility> T.filterPlatforms(
        additionalCondition: (T, PlatformData) -> Boolean = ::alwaysTrue,
        optionalCondition: (T, PlatformData) -> Boolean = ::alwaysFalse
    ) =
        visibility.mapNotNull { (platformData, visibility) ->
            platformData.takeIf { d ->
                (visibility in allowedVisibilities || optionalCondition(this, d)) && additionalCondition(this, d)
            }
        }

    private fun <T : WithVisibility> List<T>.transform(
        additionalCondition: (T, PlatformData) -> Boolean = ::alwaysTrue,
        optionalCondition: (T, PlatformData) -> Boolean = ::alwaysFalse,
        recreate: (T, List<PlatformData>) -> T
    ): Pair<Boolean, List<T>> {
        var changed = false
        return Pair(changed, mapNotNull { t ->
            val filteredPlatforms = t.filterPlatforms(additionalCondition, optionalCondition)
            when (filteredPlatforms.size) {
                t.visibility.size -> t
                0 -> {
                    changed = true
                    null
                }
                else -> {
                    changed = true
                    recreate(t, filteredPlatforms)
                }
            }
        })
    }

    private fun filterFunctions(
        functions: List<Function>,
        additionalCondition: (Function, PlatformData) -> Boolean = ::alwaysTrue
    ) =
        functions.transform(additionalCondition) { original, filteredPlatforms ->
            with(original) {
                Function(
                    dri,
                    name,
                    isConstructor,
                    parameters,
                    documentation.filtered(filteredPlatforms),
                    sources.filtered(filteredPlatforms),
                    visibility.filtered(filteredPlatforms),
                    type,
                    generics.mapNotNull { it.filter(filteredPlatforms) },
                    receiver,
                    modifier,
                    filteredPlatforms,
                    extra
                )
            }
        }

    private fun hasVisibleAccessorsForPlatform(property: Property, data: PlatformData) =
        property.getter?.visibility?.get(data) in allowedVisibilities ||
                property.setter?.visibility?.get(data) in allowedVisibilities

    private fun filterProperties(
        properties: List<Property>,
        additionalCondition: (Property, PlatformData) -> Boolean = ::alwaysTrue
    ): Pair<Boolean, List<Property>> =
        properties.transform(additionalCondition, ::hasVisibleAccessorsForPlatform) { original, filteredPlatforms ->
            with(original) {
                Property(
                    dri,
                    name,
                    documentation.filtered(filteredPlatforms),
                    sources.filtered(filteredPlatforms),
                    visibility.filtered(filteredPlatforms),
                    type,
                    receiver,
                    setter,
                    getter,
                    modifier,
                    filteredPlatforms,
                    extra
                )
            }
        }

    private fun filterEnumEntries(entries: List<EnumEntry>, filteredPlatforms: List<PlatformData>) = entries.mapNotNull { entry ->
        if (filteredPlatforms.containsAll(entry.platformData)) entry
        else {
            val intersection = filteredPlatforms.intersect(entry.platformData).toList()
            if (intersection.isEmpty()) null
            else EnumEntry(
                entry.dri,
                entry.name,
                entry.documentation.filtered(intersection),
                filterFunctions(entry.functions) { _, data -> data in intersection }.second,
                filterProperties(entry.properties) { _, data -> data in intersection }.second,
                filterClasslikes(entry.classlikes) { _, data -> data in intersection }.second,
                intersection,
                entry.extra
            )
        }
    }

    private fun filterClasslikes(
        classlikeList: List<Classlike>,
        additionalCondition: (Classlike, PlatformData) -> Boolean = ::alwaysTrue
    ): Pair<Boolean, List<Classlike>> {
        var classlikesListChanged = false
        val filteredClasslikes: List<Classlike> = classlikeList.mapNotNull {
            with(it) {
                val filteredPlatforms = filterPlatforms(additionalCondition)
                if (filteredPlatforms.isEmpty()) {
                    classlikesListChanged = true
                    null
                } else {
                    var modified = platformData.size != filteredPlatforms.size
                    val functions =
                        filterFunctions(functions) { _, data -> data in filteredPlatforms }.let { (listModified, list) ->
                            modified = modified || listModified
                            list
                        }
                    val properties =
                        filterProperties(properties) { _, data -> data in filteredPlatforms }.let { (listModified, list) ->
                            modified = modified || listModified
                            list
                        }
                    val classlikes =
                        filterClasslikes(classlikes) { _, data -> data in filteredPlatforms }.let { (listModified, list) ->
                            modified = modified || listModified
                            list
                        }
                    val companion =
                        if (this is WithCompanion) filterClasslikes(listOfNotNull(companion)) { _, data -> data in filteredPlatforms }.let { (listModified, list) ->
                            modified = modified || listModified
                            list.firstOrNull() as Object?
                        } else null
                    val constructors = if (this is WithConstructors)
                        filterFunctions(constructors) { _, data -> data in filteredPlatforms }.let { (listModified, list) ->
                            modified = modified || listModified
                            list
                        } else emptyList()
                    val generics =
                        if (this is WithGenerics) generics.mapNotNull { param -> param.filter(filteredPlatforms) } else emptyList()
                    val enumEntries = if (this is Enum) filterEnumEntries(entries, filteredPlatforms) else emptyList()
                    when {
                        !modified -> this
                        this is Class -> Class(
                            dri,
                            name,
                            constructors,
                            functions,
                            properties,
                            classlikes,
                            sources.filtered(filteredPlatforms),
                            visibility.filtered(filteredPlatforms),
                            companion,
                            generics,
                            supertypes.filtered(filteredPlatforms),
                            documentation.filtered(filteredPlatforms),
                            modifier,
                            filteredPlatforms,
                            extra
                        )
                        this is Annotation -> Annotation(
                            name,
                            dri,
                            documentation.filtered(filteredPlatforms),
                            sources.filtered(filteredPlatforms),
                            functions,
                            properties,
                            classlikes,
                            visibility.filtered(filteredPlatforms),
                            companion,
                            constructors,
                            filteredPlatforms,
                            extra
                        )
                        this is Enum -> Enum(
                            dri,
                            name,
                            enumEntries,
                            documentation.filtered(filteredPlatforms),
                            sources.filtered(filteredPlatforms),
                            functions,
                            properties,
                            classlikes,
                            visibility.filtered(filteredPlatforms),
                            companion,
                            constructors,
                            supertypes.filtered(filteredPlatforms),
                            filteredPlatforms,
                            extra
                        )
                        this is Interface -> Interface(
                            dri,
                            name,
                            documentation.filtered(filteredPlatforms),
                            sources.filtered(filteredPlatforms),
                            functions,
                            properties,
                            classlikes,
                            visibility.filtered(filteredPlatforms),
                            companion,
                            generics,
                            supertypes.filtered(filteredPlatforms),
                            filteredPlatforms,
                            extra
                        )
                        this is Object -> Object(
                            name,
                            dri,
                            documentation.filtered(filteredPlatforms),
                            sources.filtered(filteredPlatforms),
                            functions,
                            properties,
                            classlikes,
                            visibility,
                            supertypes.filtered(filteredPlatforms),
                            filteredPlatforms,
                            extra
                        )
                        else -> null
                    }
                }
            }
        }
        return Pair(classlikesListChanged, filteredClasslikes)
    }

    private val allowedVisibilities = arrayOf(
        JavaVisibility.Public,
        JavaVisibility.Default,
        JavaVisibility.Protected,
        KotlinVisibility.Protected,
        KotlinVisibility.Public
    )
}