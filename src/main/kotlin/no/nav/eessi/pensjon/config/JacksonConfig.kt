package no.nav.eessi.pensjon.config

import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        val constraints = StreamReadConstraints.builder()
            .maxStringLength(50_000_000)
            .build()

        val jsonFactory = JsonFactoryBuilder()
            .streamReadConstraints(constraints)
            .build()

        return JsonMapper.builder(jsonFactory).build()
    }
}