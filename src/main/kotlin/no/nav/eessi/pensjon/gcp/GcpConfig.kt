package no.nav.eessi.pensjon.gcp

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("test", "prod")
@Configuration
class GcpConfig {

    @Bean
    fun gcpStorage() : Storage{
        return StorageOptions.getDefaultInstance().service
    }
}