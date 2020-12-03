package no.nav.eessi.pensjon.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.ImportOptions
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

    private val rootDir = EessiPensjonJournalforingApplication::class.qualifiedName!!
            .replace("." + EessiPensjonJournalforingApplication::class.simpleName, "")

    // Only include main module. Ignore test module and external deps
    private val classesToAnalyze = ClassFileImporter()
            .importClasspath(
                    ImportOptions()
                            .with(ImportOption.DoNotIncludeJars())
                            .with(ImportOption.DoNotIncludeArchives())
                            .with(ImportOption.DoNotIncludeTests())
            )

    @BeforeAll
    fun beforeAll() {
        // Validate number of classes to analyze
        assertTrue(classesToAnalyze.size > 150, "Sanity check on no. of classes to analyze")
        assertTrue(classesToAnalyze.size < 300, "Sanity check on no. of classes to analyze")
    }

    @Test
    fun `Packages should not have cyclic depenedencies`() {
        slices().matching("$rootDir.(*)..").should().beFreeOfCycles().check(classesToAnalyze)
    }

    @Test
    fun `Klienter should not depend on eachother`() {
        slices().matching("..$rootDir.klienter.(**)").should().notDependOnEachOther().check(classesToAnalyze)
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
        val Sed = "journalforing.sed"

        // Sub packages
        val EuxKlient = "journalforing.klienter.eux"
        val FagmodulKlient = "journalforing.klienter.fagmodul"
        val JournalpostKlient = "journalforing.klienter.journalpost"
        val PesysKlient = "journalforing.klienter.pesys"
        val PersonidentifiseringHelpers = "journalforing.personidentifisering.helpers"

        layeredArchitecture()
                //Define components
                .layer(ROOT).definedBy(rootDir)
                .layer(Buc).definedBy("$rootDir.buc")
                .layer(Config).definedBy("$rootDir.config")
                .layer(Handler).definedBy("$rootDir.handler")
                .layer(Health).definedBy("$rootDir.health")
                .layer(Journalforing).definedBy("$rootDir.journalforing")
                .layer(Listeners).definedBy("$rootDir.listeners")
                .layer(OppgaveRouting).definedBy("$rootDir.oppgaverouting")
                .layer(PDF).definedBy("$rootDir.pdf")
                .layer(EuxKlient).definedBy("$rootDir.klienter.eux")
                .layer(FagmodulKlient).definedBy("$rootDir.klienter.fagmodul")
                .layer(JournalpostKlient).definedBy("$rootDir.klienter.journalpost")
                .layer(PesysKlient).definedBy("$rootDir.klienter.pesys")
                .layer(Personidentifisering).definedBy("$rootDir.personidentifisering")
                .layer(PersonidentifiseringHelpers).definedBy("$rootDir.personidentifisering.helpers")
                .layer(Sed).definedBy("$rootDir.sed")
                //define rules
                .whereLayer(ROOT).mayNotBeAccessedByAnyLayer()
                .whereLayer(Buc).mayOnlyBeAccessedByLayers(Listeners, Journalforing) // Sed
                .whereLayer(Config).mayNotBeAccessedByAnyLayer()
                .whereLayer(Handler).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(Health).mayNotBeAccessedByAnyLayer()
                .whereLayer(Journalforing).mayOnlyBeAccessedByLayers(Listeners)
                .whereLayer(Listeners).mayOnlyBeAccessedByLayers(ROOT)
                .whereLayer(OppgaveRouting).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(PDF).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(Sed).mayOnlyBeAccessedByLayers(Listeners, Journalforing, Buc, OppgaveRouting)
                .whereLayer(EuxKlient).mayOnlyBeAccessedByLayers(Journalforing, Buc)
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
