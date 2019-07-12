package no.nav.eessi.pensjon.journalforing.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.journalforing.EessiPensjonJournalforingApplication
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test


//TODO expand this test class

class ArchitectureTest {

    companion object {

        @JvmStatic
        private val root = EessiPensjonJournalforingApplication::class.qualifiedName!!
                .replace("." + EessiPensjonJournalforingApplication::class.simpleName, "")

        @JvmStatic
        lateinit var classesToAnalyze: JavaClasses

        @BeforeClass
        @JvmStatic
        fun `extract classes`() {
            classesToAnalyze = ClassFileImporter().importPackages(root)

            assertTrue("Sanity check on no. of classes to analyze", classesToAnalyze.size > 200)
            assertTrue("Sanity check on no. of classes to analyze", classesToAnalyze.size < 800)
        }
    }

    @Test
    fun `Packages should not have cyclic depenedencies`() {
        slices().matching("$root.(*)..").should().beFreeOfCycles().check(classesToAnalyze)
    }


    @Test
    @Ignore("Pending architecture cleanup") //TODO fix architecture and remove this annotation
    fun `Sub-Packages should not have cyclic dependencies`() {
        slices().matching("..$root.(**)").should().notDependOnEachOther().check(classesToAnalyze)
    }

}