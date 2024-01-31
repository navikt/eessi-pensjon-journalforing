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
    fun `Check architecture`() {
        val root = "journalforing"

        // Root packages
        val eux = "eux"
        val config = "config"
        val health = "health"
        val listeners = "listeners"
        val oppgaveRouting = "journalforing bestemenhet"

        // Sub packages
        val pdf = "journalforing pdf"
        val pesysKlient = "listener pesys"
        val journalforing = "journalforing"
        val fagmodulKlient = "listener fagmodul"
        val journalpostKlient = "journalforing journalpost"
        val personidentifisering = "personidentifisering"
        val personidentifiseringHelpers = "personidentifisering helpers"

        layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage(this.root)
                //Define components
                .layer(root).definedBy(this.root)
                .layer(eux).definedBy("${this.root}.eux")
                .layer(config).definedBy("${this.root}.config")
                .layer(health).definedBy("${this.root}.health")
                .layer(journalforing).definedBy("${this.root}.journalforing")
                .layer(listeners).definedBy("${this.root}.listeners")
                .layer(oppgaveRouting).definedBy("${this.root}.journalforing.bestemenhet")
                .layer(pdf).definedBy("${this.root}.journalforing.pdf")
                .layer(fagmodulKlient).definedBy("${this.root}.listeners.fagmodul")
                .layer(journalpostKlient).definedBy("${this.root}.journalforing.journalpost")
                .layer(pesysKlient).definedBy("${this.root}.listeners.pesys")
                .layer(personidentifisering).definedBy("${this.root}.personidentifisering")
                .layer(personidentifiseringHelpers).definedBy("${this.root}.personidentifisering.helpers")
                //define rules
                .whereLayer(root).mayNotBeAccessedByAnyLayer()
                .whereLayer(eux).mayOnlyBeAccessedByLayers(listeners, journalforing, pdf) // Sed
                .whereLayer(config).mayNotBeAccessedByAnyLayer()
                .whereLayer(health).mayNotBeAccessedByAnyLayer()
                .whereLayer(journalforing).mayOnlyBeAccessedByLayers(listeners)
                .whereLayer(listeners).mayOnlyBeAccessedByLayers(root)
                .whereLayer(oppgaveRouting).mayOnlyBeAccessedByLayers(journalforing)
                .whereLayer(pdf).mayOnlyBeAccessedByLayers(journalforing)
                .whereLayer(fagmodulKlient).mayOnlyBeAccessedByLayers(journalforing, eux, personidentifiseringHelpers)
                .whereLayer(journalpostKlient).mayOnlyBeAccessedByLayers(journalforing)
                .whereLayer(pesysKlient).mayOnlyBeAccessedByLayers(journalforing, listeners, oppgaveRouting)
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
