package com.nocklock.securitylens.service;

import com.nocklock.securitylens.dto.ImageInfo;
import com.nocklock.securitylens.dto.MetadataResult;
import com.nocklock.securitylens.dto.OcrResult;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FastAnalysisCache {

    private static final long TTL_MILLIS =
            120_000;

    private final Map<String, CachedAnalysis> cache =
            new ConcurrentHashMap<>();


    public String createKey(
            MultipartFile image
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );


            try (
                    InputStream inputStream =
                            image.getInputStream()
            ) {

                byte[] buffer =
                        new byte[8192];

                int read;


                while (
                        (read = inputStream.read(buffer))
                                != -1
                ) {

                    digest.update(
                            buffer,
                            0,
                            read
                    );
                }
            }


            return HexFormat.of()
                    .formatHex(
                            digest.digest()
                    );


        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to create analysis cache key.",
                    e
            );
        }
    }


    public void put(
            String key,
            ImageInfo imageInfo,
            MetadataResult metadata,
            OcrResult ocrResult
    ) {

        cleanup();


        cache.put(
                key,
                new CachedAnalysis(
                        System.currentTimeMillis(),
                        imageInfo,
                        metadata,
                        ocrResult
                )
        );
    }


    public CachedAnalysis take(
            String key
    ) {

        cleanup();


        CachedAnalysis result =
                cache.remove(
                        key
                );


        if (result == null) {
            return null;
        }


        if (
                System.currentTimeMillis()
                        - result.createdAt()
                        > TTL_MILLIS
        ) {

            return null;
        }


        return result;
    }


    private void cleanup() {

        long now =
                System.currentTimeMillis();


        cache.entrySet()
                .removeIf(
                        entry ->
                                now
                                        - entry.getValue()
                                        .createdAt()
                                        > TTL_MILLIS
                );
    }


    public record CachedAnalysis(
            long createdAt,
            ImageInfo imageInfo,
            MetadataResult metadata,
            OcrResult ocrResult
    ) {
    }
}