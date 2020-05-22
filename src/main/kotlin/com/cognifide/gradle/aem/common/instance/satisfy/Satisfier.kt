package com.cognifide.gradle.aem.common.instance.satisfy

import com.cognifide.gradle.aem.common.instance.Instance
import com.cognifide.gradle.aem.common.instance.InstanceManager
import com.cognifide.gradle.aem.common.instance.InstanceSync
import com.cognifide.gradle.aem.common.instance.service.pkg.PackageState
import com.cognifide.gradle.aem.common.pkg.PackageValidator
import com.cognifide.gradle.common.utils.Patterns
import com.cognifide.gradle.common.utils.using
import java.io.File

class Satisfier(private val manager: InstanceManager) {

    private val aem = manager.aem

    private val common = aem.common

    private val logger = aem.logger

    /**
     * Allows to disable service at all.
     */
    val enabled = aem.obj.boolean {
        convention(true)
        aem.prop.boolean("instance.satisfy.enabled")?.let { set(it) }
    }

    /**
     * Enables deployment via package activation from author to publishers when e.g they are not accessible.
     */
    val distributed = aem.obj.boolean {
        convention(false)
        aem.prop.boolean("instance.satisfy.distributed")?.let { set(it) }
    }

    /**
     * Allows to disable package validation before satisfying.
     */
    val validated = aem.obj.boolean {
        convention(true)
        aem.prop.boolean("instance.satisfy.validated")?.let { set(it) }
    }

    /**
     * Forces to upload and install again all packages regardless their state on instances (already uploaded / installed).
     */
    val greedy = aem.obj.boolean {
        convention(false)
        aem.prop.boolean("instance.satisfy.greedy")?.let { set(it) }
    }

    /**
     * Determines which packages should be installed by default when satisfy task is being executed.
     */
    val groupFilter = aem.obj.string {
        convention("*")
        aem.prop.string("instance.satisfy.group")?.let { set(it) }
    }

    /**
     * Determines which filtered group of packages will be validated before satisfying.
     */
    val groupValidated = aem.obj.string {
        convention("*,!tool.*,!cfp.*,!fp.*,!sp.*")
        aem.prop.string("instance.satisfy.group.validated")?.let { set(it) }
    }

    /**
     * Allows to customize CRX package validator.
     */
    fun validator(options: PackageValidator.() -> Unit) {
        this.validatorOptions = options
    }

    private var validatorOptions: PackageValidator.() -> Unit = {}

    /**
     * Provides a packages from local and remote sources.
     * Handles automatic wrapping OSGi bundles to CRX packages.
     */
    fun packages(options: PackageResolver.() -> Unit) = packageProvider.using(options)

    private val packageProvider = PackageResolver(aem).apply {
        downloadDir.convention(manager.buildDir.dir("satisfy/packages"))
        aem.prop.file("instance.satisfy.downloadDir")?.let { downloadDir.set(it) }
        aem.prop.list("instance.satisfy.urls")?.forEachIndexed { index, url ->
            val no = index + 1
            val fileName = url.substringAfterLast("/").substringBeforeLast(".")
            group("$GROUP_CMD.$no.$fileName") { get(url) }
        }
    }

    private val cmdGroups: Boolean get() = aem.prop.list("instance.satisfy.urls") != null

    fun satisfy(instance: Instance) = satisfy(listOf(instance))

    fun satisfy(instances: Collection<Instance>): List<PackageAction> {
        if (!enabled.get()) {
            logger.lifecycle("No actions performed / instance satisfier is disabled.")
            return listOf()
        }

        val groupsSatisfiable = groupsSatisfiable()

        if (validated.get()) {
            val filesSatisfiable = filesSatisfied(instances, groupsSatisfiable)
            val filesValidatable = filesValidatable(groupsSatisfiable)
            val filesValidated = filesSatisfiable.intersect(filesValidatable)

            aem.validatePackage(filesValidated, validatorOptions)
        }

        val packageActionsPerformed = satisfy(instances, groupsSatisfiable)
        if (packageActionsPerformed.isEmpty()) {
            logger.lifecycle("No actions to perform / all packages satisfied.")
        }

        return packageActionsPerformed
    }

    private fun filesSatisfied(instances: Collection<Instance>, packageGroups: List<PackageGroup>): List<File> {
        val result = mutableListOf<File>()

        common.progress(packageGroups.sumBy { it.files.size * groupInstances(instances, it).size }) {
            packageGroups.forEach { group ->
                step = "Analyzing group '${group.name}'"

                val groupInstances = groupInstances(instances, group)
                aem.sync(groupInstances) {
                    val packageStates = group.files.map {
                        PackageState(it, packageManager.find(it))
                    }
                    val packageSatisfiable = packageStates.filter {
                        (group.greedy.orNull ?: greedy.get()) || packageManager.isSnapshot(it.file) || !it.uploaded || !it.installed
                    }.map { it.file }

                    result.addAll(packageSatisfiable)
                }
            }
        }

        return result
    }

    @Suppress("ComplexMethod")
    private fun satisfy(instances: Collection<Instance>, packageGroups: List<PackageGroup>): List<PackageAction> {
        val allActions = mutableListOf<PackageAction>()

        common.progress(packageGroups.sumBy { it.files.size * groupInstances(instances, it).size }) {
            packageGroups.forEach { group ->
                step = "Satisfying group '${group.name}'"

                val groupInstances = groupInstances(instances, group)
                val groupActions = mutableListOf<PackageAction>()

                aem.sync(groupInstances) {
                    val packageStates = group.files.map {
                        PackageState(it, packageManager.find(it))
                    }
                    val packageSatisfiable = packageStates.filter {
                        (group.greedy.orNull ?: greedy.get()) || packageManager.isSnapshot(it.file) || !it.uploaded || !it.installed
                    }
                    val packageSatisfiableAny = packageSatisfiable.isNotEmpty()

                    if (packageSatisfiableAny) {
                        apply(group.initializer)
                    }

                    packageStates.forEach { pkg ->
                        increment("${pkg.file.name} -> ${instance.name}") {
                            when {
                                group.greedy.orNull ?: greedy.get() -> {
                                    logger.info("Satisfying package '${pkg.name}' on '${instance.name}' (greedy).")
                                    groupActions.add(satisfyPackage(group, pkg))
                                }
                                packageManager.isSnapshot(pkg.file) -> {
                                    logger.info("Satisfying package '${pkg.name}' on '${instance.name}' (snapshot).")
                                    groupActions.add(satisfyPackage(group, pkg))
                                }
                                !pkg.uploaded -> {
                                    logger.info("Satisfying package '${pkg.name}' on '${instance.name}' (not uploaded).")
                                    groupActions.add(satisfyPackage(group, pkg))
                                }
                                !pkg.installed -> {
                                    logger.info("Satisfying package '${pkg.name}' on '${instance.name}' (not installed).")
                                    groupActions.add(satisfyPackage(group, pkg))
                                }
                                else -> {
                                    logger.info("Not satisfying package '${pkg.name}' on '${instance.name}' (already installed).")
                                }
                            }
                        }
                    }

                    if (packageSatisfiableAny) {
                        apply(group.finalizer)
                    }
                }

                if (groupActions.isNotEmpty()) {
                    groupInstances.apply(group.completer)
                }

                allActions.addAll(groupActions)
            }
        }

        return allActions
    }

    private fun groupsSatisfiable(): List<PackageGroup> {
        val result = if (cmdGroups) {
            logger.info("Providing packages defined via command line.")
            packageProvider.resolveGroups("$GROUP_CMD.*")
        } else {
            logger.info("Providing packages defined in build script.")
            packageProvider.resolveGroups(groupFilter.get())
        }

        val files = result.flatMap { it.files }

        logger.info("Packages provided (${files.size}):\n${files.joinToString("\n")}")

        return result
    }

    private fun filesValidatable(packageGroups: List<PackageGroup>) = packageGroups
            .filter { Patterns.wildcard(it.name, groupValidated.get()) }
            .flatMap { it.files }

    private fun groupInstances(instances: Collection<Instance>, group: PackageGroup) = instances.filter { group.condition(it) }

    private fun InstanceSync.satisfyPackage(group: PackageGroup, state: PackageState): PackageAction {
        packageManager.deploy(state.file, group.distributed.orNull ?: distributed.get())
        return PackageAction(state.file, instance)
    }

    fun resolve(): List<File> {
        logger.info("Resolving CRX packages for satisfying instances")
        val files = packageProvider.files
        logger.info("Resolved CRX packages:\n${files.joinToString("\n")}")
        return files
    }

    companion object {
        const val NAME = "instanceSatisfy"

        const val GROUP_CMD = "cmd"
    }
}
