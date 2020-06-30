package no.nav.eessi.pensjon.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.EessiPensjonJournalforingApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ArchitectureTest {

    companion object {

        @JvmStatic
        private val root = EessiPensjonJournalforingApplication::class.qualifiedName!!
                .replace("." + EessiPensjonJournalforingApplication::class.simpleName, "")

        @JvmStatic
        lateinit var classesToAnalyze: JavaClasses

        @BeforeAll
        @JvmStatic
        fun `extract classes`() {
            classesToAnalyze = ClassFileImporter().importPackages(root)

            assertTrue(classesToAnalyze.size > 150, "Sanity check on no. of classes to analyze")
            assertTrue(classesToAnalyze.size < 800, "Sanity check on no. of classes to analyze")
        }
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
        val Config = "journalforing.Config"
        val BUC = "journalforing.buc"
        val Health = "journalforing.Health"
        val Journalforing = "journalforing.journalforing"
        val JSON = "journalforing.json"
        val Listeners = "journalforing.listeners"
        val Logging = "journalforing.logging"
        val Metrics = "journalforing.metrics"
        val OppgaveRouting = "journalforing.oppgaverouting"
        val PDF = "journalforing.pdf"
        val STS = "journalforing.security.sts"
        val EuxKlient = "journalforing.klienter.eux"
        val FagmodulKlient = "journalforing.klienter.fagmodul"
        val JournalpostKlient = "journalforing.klienter.journalpost"
        val PesysKlient = "journalforing.klienter.pesys"
        val Personidentifisering = "journalforing.personidentifisering"
        val Personoppslag = "journalforing.personoppslag"
        val PersonidentifiseringHelpers = "journalforing.personidentifisering.helpers"
        val Integrasjonstest = "journalforing.integrasjonstest"

        val packages: Map<String, String> = mapOf(
                ROOT to root,
                Config to "$root.config",
                Health to "$root.health",
                Journalforing to "$root.journalforing",
                JSON to "$root.json",
                BUC to "$root.buc",
                Listeners to "$root.listeners",
                Logging to "$root.logging",
                Metrics to "$root.metrics",
                OppgaveRouting to "$root.oppgaverouting",
                PDF to "$root.pdf",
                STS to "$root.security.sts",
                EuxKlient to "$root.klienter.eux",
                FagmodulKlient to "$root.klienter.fagmodul",
                JournalpostKlient to "$root.klienter.journalpost",
                PesysKlient to "$root.klienter.pesys",
                Personidentifisering to "$root.personidentifisering",
                Personoppslag to "$root.personoppslag..",
                PersonidentifiseringHelpers to "$root.personidentifisering.helpers",
                Integrasjonstest to "$root.integrasjonstest"
        )

        /*
        TODO do something about the dependencies surrounding STS, but there is a bit too much black magic there for me ...
        TODO look at/refactor the relationship between journalforing.JournalpostModel and klienter.journalpost.JournalpostKlient ...
         */
        layeredArchitecture()
                //Define components
                .layer(ROOT).definedBy(packages[ROOT])
                .layer(Config).definedBy(packages[Config])
                .layer(Health).definedBy(packages[Health])
                .layer(Journalforing).definedBy(packages[Journalforing])
                .layer(JSON).definedBy(packages[JSON])
                .layer(BUC).definedBy(packages[BUC])
                .layer(Listeners).definedBy(packages[Listeners])
                .layer(Logging).definedBy(packages[Logging])
                .layer(Metrics).definedBy(packages[Metrics])
                .layer(OppgaveRouting).definedBy(packages[OppgaveRouting])
                .layer(PDF).definedBy(packages[PDF])
                .layer(STS).definedBy(packages[STS])
                .layer(EuxKlient).definedBy(packages[EuxKlient])
                .layer(FagmodulKlient).definedBy(packages[FagmodulKlient])
                .layer(JournalpostKlient).definedBy(packages[JournalpostKlient])
                .layer(PesysKlient).definedBy(packages[PesysKlient])
                .layer(Personidentifisering).definedBy(packages[Personidentifisering])
                .layer(Personoppslag).definedBy(packages[Personoppslag])
                .layer(PersonidentifiseringHelpers).definedBy(packages[PersonidentifiseringHelpers])
                .layer(Integrasjonstest).definedBy(packages[Integrasjonstest])
                //define rules
                .whereLayer(ROOT).mayOnlyBeAccessedByLayers(Integrasjonstest)
                .whereLayer(Config).mayNotBeAccessedByAnyLayer()
                .whereLayer(Health).mayNotBeAccessedByAnyLayer()
                .whereLayer(BUC).mayOnlyBeAccessedByLayers(Listeners, Journalforing, Integrasjonstest)
                .whereLayer(Journalforing).mayOnlyBeAccessedByLayers(Listeners)
                .whereLayer(Listeners).mayOnlyBeAccessedByLayers(ROOT, Integrasjonstest)
                .whereLayer(Logging).mayOnlyBeAccessedByLayers(Config, STS, Personoppslag)
                .whereLayer(OppgaveRouting).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(PDF).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(STS).mayOnlyBeAccessedByLayers(Config, Personoppslag, Integrasjonstest)
                .whereLayer(EuxKlient).mayOnlyBeAccessedByLayers(Journalforing, BUC, Integrasjonstest)
                .whereLayer(FagmodulKlient).mayOnlyBeAccessedByLayers(Journalforing, BUC, PersonidentifiseringHelpers) // TODO PersonidentifiseringHelpers må vekk
                .whereLayer(JournalpostKlient).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(PesysKlient).mayOnlyBeAccessedByLayers(Journalforing)
                //.whereLayer(PersonidentifiseringKlienter).mayOnlyBeAccessedByLayers(Personidentifisering, Integrasjonstest) // TODO Denne må skrus på når TODOene over er fikset
                //.whereLayer(PersonidentifiseringHelpers).mayOnlyBeAccessedByLayers(Personidentifisering, Integrasjonstest) // TODO Denne må skrus på når TODOene over er fikset
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
