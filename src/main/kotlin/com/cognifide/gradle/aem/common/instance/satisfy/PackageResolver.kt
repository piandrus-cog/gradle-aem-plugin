package com.cognifide.gradle.aem.common.instance.satisfy

import com.cognifide.gradle.aem.AemExtension
import com.cognifide.gradle.aem.common.bundle.BundleFile
import com.cognifide.gradle.aem.common.pkg.PackageDefinition
import com.cognifide.gradle.common.file.resolver.Resolver
import org.gradle.api.tasks.Internal

class PackageResolver(@Internal val aem: AemExtension) : Resolver<PackageGroup>(aem.common) {

    /**
     * Determines a path in JCR repository in which automatically wrapped bundles will be deployed.
     */
    val bundlePath = aem.obj.string {
        convention("/apps/gap/wrap/install")
        aem.prop.string("package.resolver.bundlePath")?.let { set(it) }
    }

    /**
     * A hook which could be used to override default properties used to generate a CRX package from OSGi bundle.
     */
    fun bundleDefinition(options: PackageDefinition.(BundleFile) -> Unit) {
        this.bundleDefinition = options
    }

    internal var bundleDefinition: PackageDefinition.(BundleFile) -> Unit = {}

    override fun createGroup(name: String): PackageGroup {
        return PackageGroup(this, name)
    }
}
