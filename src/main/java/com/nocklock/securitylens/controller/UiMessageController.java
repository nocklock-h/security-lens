package com.nocklock.securitylens.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@RestController
@RequestMapping("/api/v1/ui")
public class UiMessageController {

    @GetMapping("/messages")
    public Map<String, Object> messages(
            @RequestParam(defaultValue = "en") String lang
    ) {

        Locale locale =
                lang.toLowerCase().startsWith("ko")
                        ? Locale.KOREAN
                        : Locale.ENGLISH;

        ResourceBundle bundle =
                ResourceBundle.getBundle(
                        "messages",
                        locale
                );

        Map<String, String> messages =
                new LinkedHashMap<>();

        bundle.keySet()
                .stream()
                .filter(key -> key.startsWith("ui."))
                .sorted()
                .forEach(key ->
                        messages.put(
                                key,
                                bundle.getString(key)
                        )
                );

        return Map.of(
                "locale",
                locale.getLanguage(),

                "messages",
                messages
        );
    }
}