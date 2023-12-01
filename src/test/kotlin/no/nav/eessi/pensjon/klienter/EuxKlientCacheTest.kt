package no.nav.eessi.pensjon.klienter

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.buc.EuxCacheableKlient
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.config.EuxCacheConfig
import no.nav.eessi.pensjon.config.SED_CACHE
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.web.client.RestTemplate

@SpringJUnitConfig(classes = [euxServiceCacheTest.Config::class, EuxCacheConfig::class])
class euxServiceCacheTest {

    @Autowired
    lateinit var euxService: EuxService

    @Autowired
    lateinit var euxRestTemplate: RestTemplate

    @Autowired
    lateinit var cacheManager: CacheManager

    private val RINASAK_ID = "123"
    private val sedIds = listOf("11111", "11111", "11111", "22222")

    @BeforeEach
    fun setup() {
        sedIds.forEach {
            every {
                euxRestTemplate.getForObject("/buc/${RINASAK_ID}/sed/$it", String::class.java)
            } returns javaClass.getResource("/sed/P2000-NAV.json")!!.readText()
        }
    }

    @Test
    fun `hentSed skal cache henting av SED og kun kalle ekstern API én gang pr sed`() {

        sedIds.forEach {
            euxService.hentSed(RINASAK_ID, it)
        }
        sedIds.distinct().forEach {
            verify(exactly = 1) {
                euxRestTemplate.getForObject("/buc/${RINASAK_ID}/sed/$it", String::class.java)
            }
        }
        println(cacheManager.getCache(SED_CACHE)?.nativeCache?.toJson())
    }

    @Test
    fun `hentSedMedGyldigStatus skal cache henting av SED og kun kalle ekstern API én gang pr sed`() {
        val sedDataList = sedIds.map { sedId ->
            ForenkletSED(id = sedId, status = SedStatus.SENT, type = SedType.P6000)
        }
        euxService.hentSedMedGyldigStatus(RINASAK_ID, sedDataList)
        sedIds.distinct().forEach {
            verify(exactly = 1) {
                euxRestTemplate.getForObject("/buc/${RINASAK_ID}/sed/$it", String::class.java)
            }
        }
        println(cacheManager.getCache(SED_CACHE)?.nativeCache?.toJson())
    }

    @TestConfiguration
    class Config {
        @Bean
        fun euxKlientLib() = EuxKlientLib(euxRestTemplate())
        @Bean
        fun euxCacheableKlient() = EuxCacheableKlient(euxKlientLib())
        @Bean
        fun EuxService() = EuxService(euxCacheableKlient())
        @Bean
        fun euxRestTemplate(): RestTemplate = mockk(relaxed = true)
    }
}