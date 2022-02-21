package no.nav.eessi.pensjon.config

import no.nav.eessi.pensjon.personoppslag.pdl.PdlToken
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenCallBack
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenImp
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.*

@Primary
@Profile("prod", "test")
@Component("PdlTokenComponent")
@Order(Ordered.HIGHEST_PRECEDENCE)
class PDLConfiguration(private val clientConfigurationProperties: ClientConfigurationProperties, private val oAuth2AccessTokenService: OAuth2AccessTokenService): PdlTokenCallBack {

    override fun callBack(): PdlToken {
        val clientProperties =  Optional.ofNullable(clientConfigurationProperties.registration["pdl-credentials"]).orElseThrow { RuntimeException("could not find oauth2 client config for pdl-credentials") }
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        val token = response.accessToken
        return PdlTokenImp(systemToken = token, userToken = token, isUserToken = false)
    }

}

