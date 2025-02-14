package com.techsophy.tsf.account.config;

import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Locale;

import static com.techsophy.tsf.account.constants.AccountConstants.ERROR;

/**
 * GlobalMessageSource provides internationalized messages using Spring's MessageSource.
 * It supports different variations of message retrieval based on keys and arguments.
 */
@Component
@RequiredArgsConstructor
public class GlobalMessageSource {

    private final MessageSource messageSource;

    /**
     * Retrieves a localized message based on the provided key.
     *
     * @param key the message key
     * @return the localized message
     */
    public String get(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    /**
     * Retrieves a localized message with a single argument.
     *
     * @param key  the message key
     * @param args the argument to be included in the message
     * @return the formatted localized message
     */
    public String get(String key, String args) {
        return messageSource.getMessage(key, new Object[]{args}, LocaleContextHolder.getLocale());
    }

    /**
     * Retrieves a localized message using an object.
     * The object is expected to be a JSONObject containing an "ERROR" key.
     *
     * @param key       the message key
     * @param anyObject the object containing error details
     * @return the localized message
     */
    public String get(String key, Object anyObject) {
        return messageSource.getMessage(key, new Object[]{((JSONObject) anyObject).get(ERROR)}, LocaleContextHolder.getLocale());
    }

    /**
     * Retrieves a localized message using a key, arguments, and a specific locale.
     *
     * @param errorCode the message key
     * @param args      the arguments for formatting the message
     * @param locale    the locale to be used for message retrieval
     * @return the formatted localized message
     */
    public String get(String errorCode, String[] args, Locale locale) {
        return messageSource.getMessage(errorCode, args, locale);
    }

    /**
     * Retrieves a localized message using a key and a user ID.
     *
     * @param key    the message key
     * @param userId the user ID to be included in the message
     * @return the formatted localized message
     */
    public String get(String key, BigInteger userId) {
        return messageSource.getMessage(key, new Object[]{userId}, LocaleContextHolder.getLocale());
    }
}
