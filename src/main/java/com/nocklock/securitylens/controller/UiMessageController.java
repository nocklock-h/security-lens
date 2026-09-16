package com.nocklock.securitylens.controller;

import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ui")
public class UiMessageController {

    private final MessageSource messageSource;

    private static final List<String> UI_MESSAGE_KEYS = List.of(

            "ui.skip",

            "ui.header.subtitle",
            "ui.header.status",

            "ui.hero.badge",
            "ui.hero.title.first",
            "ui.hero.title.second",
            "ui.hero.description",

            "ui.upload.title",
            "ui.upload.description",
            "ui.upload.button",

            "ui.preview.change",
            "ui.preview.analyze",

            "ui.loading.title",
            "ui.loading.prepare",
            "ui.loading.personal",
            "ui.loading.metadata",
            "ui.loading.ai",
            "ui.loading.risk",

            "ui.pii.phone",
            "ui.pii.email",
            "ui.pii.personName",
            "ui.pii.address",
            "ui.pii.rrn",
            "ui.pii.driverLicenseNumber",
            "ui.pii.sensitiveDocument",
            "ui.document.driverLicense",

            "ui.risk.level.critical",
            "ui.risk.level.high",
            "ui.risk.level.medium",
            "ui.risk.level.low",
            "ui.risk.level.unknown",

            "ui.result.complete",
            "ui.result.title",
            "ui.result.description",
            "ui.result.new",

            "ui.result.imageScan",
            "ui.result.scanComplete",

            "ui.result.risk",
            "ui.result.visible",
            "ui.result.visibleDescription",
            "ui.result.hidden",
            "ui.result.hiddenDescription",

            "ui.result.ai",
            "ui.result.aiDescription",

            "ui.result.protect",
            "ui.result.comingSoon",

            "ui.error.title",
            "ui.error.description",
            "ui.error.retry",

            "ui.risk.critical",
            "ui.risk.high",
            "ui.risk.medium",
            "ui.risk.low",

            "ui.empty.visible",
            "ui.empty.metadata",

            "ui.error.imageOnly",
            "ui.error.analysis"
    );


    public UiMessageController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }


    @GetMapping("/messages")
    public Map<String, Object> messages(
            @RequestParam(defaultValue = "en") String lang
    ) {

        Locale locale =
                lang.toLowerCase().startsWith("ko")
                        ? Locale.KOREAN
                        : Locale.ENGLISH;


        Map<String, String> messages =
                new LinkedHashMap<>();


        for (String key : UI_MESSAGE_KEYS) {

            messages.put(
                    key,
                    messageSource.getMessage(
                            key,
                            null,
                            locale
                    )
            );
        }


        return Map.of(
                "locale", locale.getLanguage(),
                "messages", messages
        );
    }
}