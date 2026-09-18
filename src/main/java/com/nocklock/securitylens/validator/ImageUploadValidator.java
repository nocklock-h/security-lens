package com.nocklock.securitylens.validator;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import com.nocklock.securitylens.exception.InvalidImageException;

@Component
public class ImageUploadValidator {

    private static final long MAX_FILE_SIZE =
            30L * 1024 * 1024;

    private static final int MAX_WIDTH =
            8_000;

    private static final int MAX_HEIGHT =
            8_000;

    private static final long MAX_PIXELS =
            40_000_000L;


    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png"
            );


    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(
                    "jpg",
                    "jpeg",
                    "png"
            );

    public void validate(
            MultipartFile image
    ) {

        validateExists(
                image
        );

        validateFileSize(
                image
        );


        String extension =
                extractExtension(
                        image.getOriginalFilename()
                );


        validateExtension(
                extension
        );


        validateContentType(
                image.getContentType()
        );


        validateMagicBytes(
                image,
                extension
        );


        validateImageStructure(
                image,
                extension
        );
    }


    private void validateExists(
            MultipartFile image
    ) {

        if (
                image == null
                        ||
                        image.isEmpty()
        ) {

            throw new InvalidImageException(
                    "upload.image.empty"
            );
        }
    }


    private void validateFileSize(
            MultipartFile image
    ) {

        if (
                image.getSize() >
                        MAX_FILE_SIZE
        ) {

            throw new InvalidImageException(
                    "upload.image.tooLarge"
            );
        }
    }


    private String extractExtension(
            String filename
    ) {

        if (
                filename == null
                        ||
                        filename.isBlank()
        ) {

            throw new InvalidImageException(
                    "upload.image.filenameMissing"
            );
        }


        int dotIndex =
                filename.lastIndexOf('.');


        if (
                dotIndex < 0
                        ||
                        dotIndex ==
                                filename.length() - 1
        ) {

            throw new InvalidImageException(
                    "upload.image.extensionMissing"
            );
        }


        return filename
                .substring(
                        dotIndex + 1
                )
                .toLowerCase(
                        Locale.ROOT
                );
    }


    private void validateExtension(
            String extension
    ) {

        if (
                !ALLOWED_EXTENSIONS.contains(
                        extension
                )
        ) {

            throw new InvalidImageException(
                    "upload.image.invalidExtension"
            );
        }
    }


    private void validateContentType(
            String contentType
    ) {

        if (
                contentType == null
                        ||
                        !ALLOWED_CONTENT_TYPES.contains(
                                contentType.toLowerCase(
                                        Locale.ROOT
                                )
                        )
        ) {

            throw new InvalidImageException(
                    "upload.image.unsupportedType"
            );
        }
    }


    private void validateMagicBytes(
            MultipartFile image,
            String extension
    ) {

        byte[] header;

        try (
                InputStream input =
                        image.getInputStream()
        ) {

            header =
                    input.readNBytes(
                            8
                    );


            if (header.length < 3) {

                throw new InvalidImageException(
                        "upload.image.corrupted"
                );
            }

        } catch (IOException e) {

            throw new InvalidImageException(
                    "upload.image.readFailed"
            );
        }


        boolean jpeg =
                isJpeg(
                        header
                );


        boolean png =
                isPng(
                        header
                );


        if (
                ("jpg".equals(extension)
                        ||
                        "jpeg".equals(extension))
                        &&
                        !jpeg
        ) {

            throw new InvalidImageException(
                    "upload.image.jpegMismatch"
            );
        }


        if (
                "png".equals(extension)
                        &&
                        !png
        ) {

            throw new InvalidImageException(
                    "upload.image.pngMismatch"
            );
        }
    }


    private boolean isJpeg(
            byte[] header
    ) {

        return header.length >= 3
                &&
                (header[0] & 0xFF) == 0xFF
                &&
                (header[1] & 0xFF) == 0xD8
                &&
                (header[2] & 0xFF) == 0xFF;
    }


    private boolean isPng(
            byte[] header
    ) {

        if (header.length < 8) {
            return false;
        }


        int[] signature = {
                0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
        };


        for (
                int i = 0;
                i < signature.length;
                i++
        ) {

            if (
                    (header[i] & 0xFF)
                            != signature[i]
            ) {

                return false;
            }
        }


        return true;
    }


    private void validateImageStructure(
            MultipartFile image,
            String extension
    ) {

        try (
                InputStream input =
                        image.getInputStream();

                ImageInputStream imageInput =
                        ImageIO.createImageInputStream(
                                input
                        )
        ) {

            if (imageInput == null) {

                throw new InvalidImageException(
                        "upload.image.invalid"
                );
            }


            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(
                            imageInput
                    );


            if (!readers.hasNext()) {

                throw new InvalidImageException(
                        "upload.image.decodeFailed"
                );
            }


            ImageReader reader =
                    readers.next();


            try {

                reader.setInput(
                        imageInput,
                        true,
                        true
                );


                String format =
                        reader
                                .getFormatName()
                                .toLowerCase(
                                        Locale.ROOT
                                );


                validateDecodedFormat(
                        extension,
                        format
                );


                int width =
                        reader.getWidth(0);

                int height =
                        reader.getHeight(0);


                validateDimensions(
                        width,
                        height
                );


            } finally {

                reader.dispose();
            }


        } catch (IOException e) {

            throw new InvalidImageException(
                    "upload.image.unsupportedOrCorrupted"
            );
        }
    }


    private void validateDecodedFormat(
            String extension,
            String format
    ) {

        boolean jpeg =
                format.contains(
                        "jpeg"
                )
                        ||
                        format.contains(
                                "jpg"
                        );


        boolean png =
                format.contains(
                        "png"
                );


        if (
                ("jpg".equals(extension)
                        ||
                        "jpeg".equals(extension))
                        &&
                        !jpeg
        ) {

            throw new InvalidImageException(
                    "upload.image.formatMismatch"
            );
        }


        if (
                "png".equals(extension)
                        &&
                        !png
        ) {

            throw new InvalidImageException(
                    "upload.image.formatMismatch"
            );
        }
    }


    private void validateDimensions(
            int width,
            int height
    ) {

        if (
                width <= 0
                        ||
                        height <= 0
        ) {

            throw new InvalidImageException(
                    "upload.image.invalidDimensions"
            );
        }


        if (
                width > MAX_WIDTH
                        ||
                        height > MAX_HEIGHT
        ) {

            throw new InvalidImageException(
                    "upload.image.dimensionsTooLarge"
            );
        }


        long pixels =
                (long) width
                        * height;


        if (
                pixels > MAX_PIXELS
        ) {

            throw new InvalidImageException(
                    "upload.image.tooManyPixels"
            );
        }
    }
}