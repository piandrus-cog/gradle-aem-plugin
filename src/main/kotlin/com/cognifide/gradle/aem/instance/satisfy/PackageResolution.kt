package com.cognifide.gradle.aem.instance.satisfy

import aQute.bnd.osgi.Jar
import com.cognifide.gradle.aem.api.AemExtension
import com.cognifide.gradle.aem.base.vlt.VltFilter
import com.cognifide.gradle.aem.internal.file.FileOperations
import com.cognifide.gradle.aem.internal.file.resolver.FileResolution
import com.cognifide.gradle.aem.pkg.PackagePlugin
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.gradle.util.GFileUtils
import org.zeroturnaround.zip.ZipUtil
import java.io.File

class PackageResolution(group: PackageGroup, id: String, action: (FileResolution) -> File) : FileResolution(group, id, action) {

    private val resolver = group.resolver

    private val aem = AemExtension.of(resolver.project)

    override fun process(file: File): File {
        val origin = super.process(file)

        return when (FilenameUtils.getExtension(file.name)) {
            "jar" -> wrap(origin)
            "zip" -> origin
            else -> throw PackageException("File $origin must have *.jar or *.zip extension")
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

        val pkgRoot = File(dir, pkgName)
        val pkgPath = "${resolver.bundlePath}/${jar.name}"
        val vaultDir = File(pkgRoot, PackagePlugin.VLT_PATH)

        // Copy package template files
        GFileUtils.mkdirs(vaultDir)
        FileOperations.copyResources(PackagePlugin.VLT_PATH, vaultDir)

        // Expand package properties
        val bundle = Jar(jar)
        val description = bundle.manifest.mainAttributes.getValue("Bundle-Description") ?: ""
        val symbolicName = bundle.manifest.mainAttributes.getValue("Bundle-SymbolicName")
        val group = symbolicName.substringBeforeLast(".")
        val version = bundle.manifest.mainAttributes.getValue("Bundle-Version")
        val filters = listOf(VltFilter.rootElementForPath(pkgPath))
        val bundleProps = mapOf<String, Any>(
                "project.group" to group,
                "project.name" to symbolicName,
                "project.version" to version,
                "project.description" to description,
                "filters" to filters,
                "compose.vaultProperties" to mapOf<String, String>(),
                "nodeTypesLibs" to listOf<String>(),
                "nodeTypesLines" to listOf<String>()

        )
        val overrideProps = resolver.bundleProperties(bundle)
        val effectiveProps = bundleProps + overrideProps

        FileOperations.amendFiles(vaultDir, listOf("**/${PackagePlugin.VLT_PATH}/*.xml")) { file, content ->
            aem.props.expand(content, effectiveProps, file.absolutePath)
        }

        // Copy bundle to install path
        val pkgJar = File(pkgRoot, "jcr_root$pkgPath")

        GFileUtils.mkdirs(pkgJar.parentFile)
        FileUtils.copyFile(jar, pkgJar)

        // ZIP all to CRX package
        ZipUtil.pack(pkgRoot, pkg)
        pkgRoot.deleteRecursively()

        return pkg
    }
}