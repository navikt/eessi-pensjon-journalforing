package no.nav.eessi.pensjon.journalforing.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.journalforing.EessiPensjonJournalforingApplication
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

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

            assertTrue("Sanity check on no. of classes to analyze", classesToAnalyze.size > 150)
            assertTrue("Sanity check on no. of classes to analyze", classesToAnalyze.size < 800)
        }
    }

    @Test
    fun `Packages should not have cyclic depenedencies`() {
        slices().matching("$root.(*)..").should().beFreeOfCycles().check(classesToAnalyze)
    }


    @Test
    fun `Services should not depend on eachother`() {
        slices().matching("..$root.services.(**)").should().notDependOnEachOther().check(classesToAnalyze)
    }

    @Test
    fun `Check architecture`() {
        val ROOT = "journalforing"
        val Config = "journalforing.Config"
        val Health = "journalforing.Health"
        val Journalforing = "journalforing.journalforing"
        val JSON = "journalforing.json"
        val Listeners = "journalforing.listeners"
        val Logging = "journalforing.logging"
        val Metrics = "journalforing.metrics"
        val OppgaveRouting = "journalforing.oppgaverouting"
        val PDF = "journalforing.pdf"
        val STS = "journalforing.security.sts"
        val AktoerregisterService = "journalforing.services.aktoerregister"
        val EuxService = "journalforing.services.eux"
        val FagmodulService = "journalforing.services.fagmodul"
        val JournalPostService = "journalforing.services.journalpost"
        val OppgaveService = "journalforing.services.oppgave"
        val PersonV3Service = "journalforing.services.personv3"

        val packages: Map<String, String> = mapOf(
                ROOT to root,
                Config to "$root.config",
                Health to "$root.health",
                Journalforing to "$root.journalforing",
                JSON to "$root.json",
                Listeners to "$root.listeners",
                Logging to "$root.logging",
                Metrics to "$root.metrics",
                OppgaveRouting to "$root.oppgaverouting",
                PDF to "$root.pdf",
                STS to "$root.security.sts",
                AktoerregisterService to "$root.services.aktoerregister",
                EuxService to "$root.services.eux",
                FagmodulService to "$root.services.fagmodul",
                JournalPostService to "$root.services.journalpost",
                OppgaveService to "$root.services.oppgave",
                PersonV3Service to "$root.services.personv3"
        )

        /*
        TODO do something about the dependencies surrounding STS, but there is a bit too much black magic there for me ...
        TODO refactor SedListenerTest, it has way too many dependencies
        TODO look at/refactor the relationship between journalforing.JournalpostModel and services.journalpost.JournalpostService ...
         */
        layeredArchitecture()
                //Define components
                .layer(ROOT).definedBy(packages[ROOT])
                .layer(Config).definedBy(packages[Config])
                .layer(Health).definedBy(packages[Health])
                .layer(Journalforing).definedBy(packages[Journalforing])
                .layer(JSON).definedBy(packages[JSON])
                .layer(Listeners).definedBy(packages[Listeners])
                .layer(Logging).definedBy(packages[Logging])
                .layer(Metrics).definedBy(packages[Metrics])
                .layer(OppgaveRouting).definedBy(packages[OppgaveRouting])
                .layer(PDF).definedBy(packages[PDF])
                .layer(STS).definedBy(packages[STS])
                .layer(AktoerregisterService).definedBy(packages[AktoerregisterService])
                .layer(EuxService).definedBy(packages[EuxService])
                .layer(FagmodulService).definedBy(packages[FagmodulService])
                .layer(JournalPostService).definedBy(packages[JournalPostService])
                .layer(OppgaveService).definedBy(packages[OppgaveService])
                .layer(PersonV3Service).definedBy(packages[PersonV3Service])
                //define rules
                .whereLayer(ROOT).mayNotBeAccessedByAnyLayer()
                .whereLayer(Config).mayNotBeAccessedByAnyLayer()
                .whereLayer(Health).mayNotBeAccessedByAnyLayer()
                .whereLayer(Journalforing).mayOnlyBeAccessedByLayers(Listeners)
                .whereLayer(Listeners).mayOnlyBeAccessedByLayers(ROOT)
                .whereLayer(Logging).mayOnlyBeAccessedByLayers(Config, STS)
                .whereLayer(OppgaveRouting).mayOnlyBeAccessedByLayers(Journalforing, Listeners)
                .whereLayer(PDF).mayOnlyBeAccessedByLayers(Journalforing)
                .whereLayer(STS).mayOnlyBeAccessedByLayers(Config, PersonV3Service)
                .whereLayer(AktoerregisterService).mayOnlyBeAccessedByLayers(Journalforing, Listeners)
                .whereLayer(EuxService).mayOnlyBeAccessedByLayers(Journalforing, Listeners)
                .whereLayer(FagmodulService).mayOnlyBeAccessedByLayers(Journalforing, Listeners)
                .whereLayer(JournalPostService).mayOnlyBeAccessedByLayers(Journalforing, Listeners)
                .whereLayer(OppgaveService).mayOnlyBeAccessedByLayers(Journalforing, Listeners)
                .whereLayer(PersonV3Service).mayOnlyBeAccessedByLayers(ROOT, Journalforing, Listeners)
                //Verify rules
                .check(classesToAnalyze)
    }

}