package com.cognifide.gradle.aem.common.file

import com.cognifide.gradle.aem.AemException

class ZipException : AemException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
