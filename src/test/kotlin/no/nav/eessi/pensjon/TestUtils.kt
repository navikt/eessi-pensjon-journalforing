package no.nav.eessi.pensjon

internal class TestUtils {
    companion object {
        fun getResource(resourcePath: String): String =
                TestUtils::class.java.classLoader
                        .getResource(resourcePath)!!
                        .readText()
    }
}
