package com.nocklock.securitylens.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Component
public class ImageOrientationNormalizer {

    public BufferedImage readNormalized(
            MultipartFile image
    ) throws IOException {

        BufferedImage source =
                ImageIO.read(
                        image.getInputStream()
                );


        if (source == null) {
            return null;
        }


        int orientation =
                readExifOrientation(
                        image
                );


        return applyOrientation(
                source,
                orientation
        );
    }


    private int readExifOrientation(
            MultipartFile image
    ) {

        try (
                InputStream inputStream =
                        image.getInputStream()
        ) {

            Metadata metadata =
                    ImageMetadataReader.readMetadata(
                            inputStream
                    );


            ExifIFD0Directory directory =
                    metadata.getFirstDirectoryOfType(
                            ExifIFD0Directory.class
                    );


            if (
                    directory == null
                            ||
                            !directory.containsTag(
                                    ExifIFD0Directory.TAG_ORIENTATION
                            )
            ) {

                return 1;
            }


            return directory.getInt(
                    ExifIFD0Directory.TAG_ORIENTATION
            );


        } catch (Exception e) {

            /*
             * PNG 또는 EXIF가 없는 이미지라면
             * 회전하지 않은 일반 이미지로 취급한다.
             */
            return 1;
        }
    }


    private BufferedImage applyOrientation(
            BufferedImage source,
            int orientation
    ) {

        if (orientation == 1) {
            return source;
        }


        int width =
                source.getWidth();

        int height =
                source.getHeight();


        boolean swapDimensions =
                orientation >= 5
                        &&
                        orientation <= 8;


        int targetWidth =
                swapDimensions
                        ? height
                        : width;

        int targetHeight =
                swapDimensions
                        ? width
                        : height;


        int imageType =
                source.getColorModel()
                        .hasAlpha()
                        ? BufferedImage.TYPE_INT_ARGB
                        : BufferedImage.TYPE_INT_RGB;


        BufferedImage target =
                new BufferedImage(
                        targetWidth,
                        targetHeight,
                        imageType
                );


        AffineTransform transform =
                switch (orientation) {

                    case 2 ->
                            new AffineTransform(
                                    -1, 0,
                                    0, 1,
                                    width, 0
                            );

                    case 3 ->
                            new AffineTransform(
                                    -1, 0,
                                    0, -1,
                                    width, height
                            );

                    case 4 ->
                            new AffineTransform(
                                    1, 0,
                                    0, -1,
                                    0, height
                            );

                    case 5 ->
                            new AffineTransform(
                                    0, 1,
                                    1, 0,
                                    0, 0
                            );

                    case 6 ->
                            new AffineTransform(
                                    0, 1,
                                    -1, 0,
                                    height, 0
                            );

                    case 7 ->
                            new AffineTransform(
                                    0, -1,
                                    -1, 0,
                                    height, width
                            );

                    case 8 ->
                            new AffineTransform(
                                    0, -1,
                                    1, 0,
                                    0, width
                            );

                    default ->
                            new AffineTransform();
                };


        Graphics2D graphics =
                target.createGraphics();


        try {

            graphics.drawImage(
                    source,
                    transform,
                    null
            );

        } finally {

            graphics.dispose();
        }


        return target;
    }
}