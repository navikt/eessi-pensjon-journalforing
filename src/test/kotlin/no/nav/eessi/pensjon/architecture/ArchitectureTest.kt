package no.nav.eessi.pensjon.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.EessiPensjonJournalforingApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ArchitectureTest {

    private val root = EessiPensjonJournalforingApplication::class.qualifiedName!!
            .replace("." + EessiPensjonJournalforingApplication::class.simpleName, "")

    // Only include main module. Ignore test module and external deps
    private val classesToAnalyze = ClassFileImporter()
        .withImportOptions(listOf(
            ImportOption.DoNotIncludeJars(),
            ImportOption.DoNotIncludeArchives(),
            ImportOption.DoNotIncludeTests())
        ).importPackages(root)

    @BeforeAll
    fun beforeAll() {
        // Validate number of classes to analyze
        assertTrue(classesToAnalyze.size in 100..250, "Sanity check on no. of classes to analyze (is ${classesToAnalyze.size})")
    }

    @Test
    fun `Packages should not have cyclic depenedencies`() {
        slices().matching("$root.(*)..").should().beFreeOfCycles().check(classesToAnalyze)
    }

    @Test
    fun `Klienter should not depend on eachother`() {
        slices().matching("..$root.klienter.(**)").should().notDependOnEachOther().check(classesToAnalyze)
    }

    @Test
    fun `Check architecture`() {
        val ROOT = "journalforing"

        // Root packages
        val Buc = "journalforing.buc"
        val Config = "journalforing.config"
        val Handler = "journalforing.handler"
        val Health = "journalforing.health"
        val Journalforing = "journalforing.journalforing"
        val Listeners = "journalforing.listeners"
        val OppgaveRouting = "journalforing.oppgaverouting"
        val PDF = "journalforing.pdf"
        val Personidentifisering = "journalforing.personidentifisering"

        // Sub packages
        val FagmodulKlient = "journalforing.klienter.fagmodul"
        val JournalpostKlient = "journalforing.klienter.journalpost"
        val PesysKlient = "journalforing.klienter.pesys"
        val PersonidentifiseringHelpers = "journalforing.personidentifisering.helpers"

        layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage(root)
                //Define components
                .layer(ROOT).definedBy(root)
                .layer(Buc).definedBy("$root.buc")
                .layer(Config).definedBy("$root.config")
                .layer(Handler).definedBy("$root.handler")
                .layer(Health).definedBy("$root.health")
                .layer(Journalforing).definedBy("$root.journalforing")
                .layer(Listeners).definedBy("$root.listeners")
                .layer(OppgaveRouting).definedBy("$root.oppgaverouting")
                .layer(PDF).definedBy("$root.pdf")
                .layer(FagmodulKlient).definedBy("$root.klienter.fagmodul")
                .layer(JournalpostKlient).definedBy("$root.klienter.journalpost")
                .layer(PesysKlient).definedBy("$root.klienter.pesys")
                .layer(Personidentifisering).definedBy("$root.personidentifisering")
                .layer(PersonidentifiseringHelpers).definedBy("$root.personidentifisering.helpers")
                //define rules
                .whereLayer(ROOT).mayNotBeAccessedByAnyLayer()
                .whereLayer(Buc).mayOnlyBeAccessedByLayers(Listeners, Journalforing, PDF) // Sed
                .whereLayer(Config).mayNotBeAccessedByAnyLayer()
                .whereLayer(Handler).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(Health).mayNotBeAccessedByAnyLayer()
                .whereLayer(Journalforing).mayOnlyBeAccessedByLayers(Listeners)
                .whereLayer(Listeners).mayOnlyBeAccessedByLayers(ROOT)
                .whereLayer(OppgaveRouting).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(PDF).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(FagmodulKlient).mayOnlyBeAccessedByLayers(Journalforing, Buc, PersonidentifiseringHelpers)
                .whereLayer(JournalpostKlient).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(PesysKlient).mayOnlyBeAccessedByLayers(Journalforing, Listeners, OppgaveRouting)
                //Verify rules
                .check(classesToAnalyze)
    }

    @Test
    fun `avoid JUnit4-classes`() {
        val junitReason = "We use JUnit5 (but had to include JUnit4 because spring-kafka-test needs it to compile)"

        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.junit",
                        "org.junit.runners",
                        "org.junit.experimental..",
                        "org.junit.function",
                        "org.junit.matchers",
                        "org.junit.rules",
                        "org.junit.runner..",
                        "org.junit.validator",
                        "junit.framework.."
                ).because(junitReason)
                .check(classesToAnalyze)

        noClasses()
                .should()
                .beAnnotatedWith("org.junit.runner.RunWith")
                .because(junitReason)
                .check(classesToAnalyze)

        noMethods()
                .should()
                .beAnnotatedWith("org.junit.Test")
                .orShould().beAnnotatedWith("org.junit.Ignore")
                .because(junitReason)
                .check(classesToAnalyze)
    }
}
