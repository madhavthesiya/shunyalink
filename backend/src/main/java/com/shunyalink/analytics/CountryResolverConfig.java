package com.shunyalink.analytics;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CountryResolverConfig {
    @Bean(destroyMethod = "close")
    LocalCountryResolver localCountryResolver(@Value("${app.geoip.database:/app/dbip-country.mmdb}") String database) {
        return new LocalCountryResolver(Path.of(database));
    }
}
