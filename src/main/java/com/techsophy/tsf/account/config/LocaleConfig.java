package com.techsophy.tsf.account.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

@Configuration
public class LocaleConfig extends AcceptHeaderLocaleResolver implements WebMvcConfigurer {

    /**
     * Resolves the locale from the "Accept-Language" header in the HTTP request.
     * If the header is absent or empty, it defaults to US locale.
     *
     * @param request HTTP request
     * @return resolved locale
     */
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String languageHeader = request.getHeader(ACCEPT_LANGUAGE);
        return StringUtils.isNotEmpty(languageHeader) ? Locale.forLanguageTag(languageHeader) : Locale.US;
    }

    /**
     * Configures the MessageSource to load localization messages.
     *
     * @return configured MessageSource
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(BASENAME_ERROR_MESSAGES, BASENAME_MESSAGES);
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setCacheMillis(CACHEMILLIS); // Refresh cache once per hour
        messageSource.setUseCodeAsDefaultMessage(USEDEFAULTCODEMESSAGE);
        return messageSource;
    }
}
