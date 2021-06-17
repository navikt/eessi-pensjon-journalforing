package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SivilstandItem
import no.nav.eessi.pensjon.eux.model.sed.StatsborgerskapItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class KravInitialiseringsServiceTest {

    @Test
    fun `Gitt en SED som kan automatisk kravinit så returner true`() {

        val dato = LocalDate.of(2020, 10, 11)
        val p2000 = P2000(nav = Nav(bruker = Bruker(
            person = Person(
                sivilstand = listOf(
                    SivilstandItem(
                        dato.toString(),
                        "gift"
                    )
                ),
                statsborgerskap = listOf(
                    StatsborgerskapItem("SE")
                )
            )
        )
        ), pensjon = null)

        assertEquals(true, p2000.  validerForKravinit() )
    }

    @Test
    fun `Gitt en SED som ikke kan automatisk kravinit så returner false`() {
        val p2000 = P2000(nav = Nav(bruker = Bruker(
            person = Person(
                statsborgerskap = listOf(
                    StatsborgerskapItem("SE")
                )
            )
        )), pensjon = null)
        assertEquals(false, p2000.validerForKravinit() )
    }

    @Test
    fun `Gitt en SED som ikke kan automatisk kravinit på grunn av manglende fradato så returner false`() {
        val p2000 = P2000(nav = Nav(bruker = Bruker(
            person = Person(
                sivilstand = listOf(
                    SivilstandItem(null,
                        "enslig"
                    )
                ),
                statsborgerskap = listOf(
                    StatsborgerskapItem("SE")
                )
            )
        )), pensjon = null)
        assertEquals(false, p2000.validerForKravinit() )
    }
}