package hu.mpcalc.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/** The language of the messages follows the Accept-Language header; Hungarian when it is missing or not supported. */
@Configuration
public class LocaleConfig {
    public static final Locale HU = Locale.forLanguageTag("hu");

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver r = new AcceptHeaderLocaleResolver();
        r.setSupportedLocales(List.of(HU, Locale.ENGLISH));
        r.setDefaultLocale(HU);
        return r;
    }
}
