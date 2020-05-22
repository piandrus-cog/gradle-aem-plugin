package com.cognifide.gradle.aem.common.instance.satisfy

import com.cognifide.gradle.aem.common.bundle.BundleFile
import com.cognifide.gradle.aem.common.pkg.PackageException
import com.cognifide.gradle.common.file.resolver.FileResolution
import java.io.File

class PackageResolution(group: PackageGroup, id: String, action: (FileResolution) -> File) : FileResolution(group, id, action) {

    private val resolver = group.packageResolver

    private val aem = resolver.aem

    init {
        then { origin ->
            when (origin.extension) {
                "jar" -> wrap(origin)
                "zip" -> origin
                else -> throw PackageException("File $origin must have '*.jar' or '*.zip' extension")
            }
        }
    }

    private fun wrap(jar: File): File {
        val pkgName = jar.nameWithoutExtension
        val pkg = File(dir, "$pkgName.zip")
        if (pkg.exists()) {
            aem.logger.info("CRX package wrapping OSGi bundle already exists: $pkg")
            return pkg
        }

        aem.logger.info("Wrapping OSGi bundle to CRX package: $jar")

        val bundle = BundleFile(jar)
        val bundlePath = "${resolver.bundlePath.get()}/${jar.name}"

        return aem.composePackage {
            this.archivePath.set(pkg)
            this.description.set(bundle.description)
            this.group.set(bundle.group)
            this.name.set(bundle.symbolicName)
            this.version.set(bundle.version)

            filter(bundlePath)
            content { copyJcrFile(jar, bundlePath) }

            resolver.bundleDefinition(this, bundle)
        }
    }
}
