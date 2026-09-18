package com.nocklock.securitylens.service;

import com.nocklock.securitylens.config.OcrProperties;
import com.nocklock.securitylens.exception.OcrErrorCode;
import com.nocklock.securitylens.exception.OcrException;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;

import com.nocklock.securitylens.dto.OcrResult;
import com.nocklock.securitylens.dto.OcrWord;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Word;

import java.awt.Rectangle;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OcrService {

    private final OcrProperties ocrProperties;

    private static final Logger log =
            LoggerFactory.getLogger(OcrService.class);

    private static final int MIN_OCR_DIMENSION = 1600;
    private static final int MAX_OCR_DIMENSION = 3000;

    public OcrService(OcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
    }

    public String extractText(MultipartFile image) {
        try {
            BufferedImage originalImage = ImageIO.read(image.getInputStream());

            if (originalImage == null) {
                throw new OcrException(OcrErrorCode.UNSUPPORTED_IMAGE);
            }

            double scaleRatio =
                    resolveScaleRatio(originalImage);

            BufferedImage preprocessedImage =
                    preprocess(
                            originalImage,
                            scaleRatio
                    );

            ITesseract tesseract = createTesseract();

            return tesseract.doOCR(preprocessedImage);

        } catch (IOException | TesseractException e) {
            throw new OcrException(
                    OcrErrorCode.ANALYSIS_FAILED,
                    e
            );
        }
    }

    public OcrResult analyze(MultipartFile image) {

        try {

            long ocrStartTime =
                    System.nanoTime();

            log.info("OCR STEP 1 - read image started");

            BufferedImage originalImage =
                    ImageIO.read(
                            image.getInputStream()
                    );

            if (originalImage == null) {
                throw new OcrException(
                        OcrErrorCode.UNSUPPORTED_IMAGE
                );
            }

            log.info(
                    "OCR STEP 2 - image read completed. width={}, height={}",
                    originalImage.getWidth(),
                    originalImage.getHeight()
            );


            double scaleRatio =
                    resolveScaleRatio(
                            originalImage
                    );

            log.info(
                    "OCR STEP 3 - preprocessing started. scaleRatio={}, maxDimension={}",
                    scaleRatio,
                    Math.max(
                            originalImage.getWidth(),
                            originalImage.getHeight()
                    )
            );


            BufferedImage preprocessedImage =
                    preprocess(
                            originalImage,
                            scaleRatio
                    );

            log.info(
                    "OCR STEP 4 - preprocessing completed. width={}, height={}",
                    preprocessedImage.getWidth(),
                    preprocessedImage.getHeight()
            );


            ITesseract tesseract =
                    createTesseract();


            /*
             * 1단계
             * 전체 OCR 텍스트를 생성한다.
             *
             * 이 텍스트는
             * 주민번호 / 운전면허번호 / 전화번호 / 주소 등
             * PII 탐지에 사용한다.
             */
            long textRecognitionStart =
                    System.nanoTime();


            log.info(
                    "OCR STEP 5A - text recognition started"
            );


            String text =
                    tesseract.doOCR(
                            preprocessedImage
                    );


            long textRecognitionDurationMs =
                    (System.nanoTime() - textRecognitionStart)
                            / 1_000_000;


            log.info(
                    "OCR STEP 5A - text recognition completed. durationMs={}, textLength={}",
                    textRecognitionDurationMs,
                    text == null
                            ? 0
                            : text.length()
            );


            /*
             * 2단계
             * BoundingBox 생성을 위해
             * 단어 단위 OCR 결과와 좌표를 가져온다.
             */
            long wordRecognitionStart =
                    System.nanoTime();


            log.info(
                    "OCR STEP 5B - word recognition started"
            );


            List<Word> tessWords =
                    tesseract.getWords(
                            preprocessedImage,
                            ITessAPI.TessPageIteratorLevel.RIL_WORD
                    );


            long wordRecognitionDurationMs =
                    (System.nanoTime() - wordRecognitionStart)
                            / 1_000_000;


            log.info(
                    "OCR STEP 5B - word recognition completed. durationMs={}, rawWordCount={}",
                    wordRecognitionDurationMs,
                    tessWords.size()
            );

            /*
             * 개인정보 원문을 출력하지 않고
             * OCR 신뢰도 분포만 확인한다.
             */
            long confidence30 =
                    tessWords.stream()
                            .filter(word ->
                                    word.getConfidence() >= 30
                            )
                            .count();

            long confidence50 =
                    tessWords.stream()
                            .filter(word ->
                                    word.getConfidence() >= 50
                            )
                            .count();

            long confidence70 =
                    tessWords.stream()
                            .filter(word ->
                                    word.getConfidence() >= 70
                            )
                            .count();

            log.info(
                    "OCR confidence distribution. total={}, >=30={}, >=50={}, >=70={}",
                    tessWords.size(),
                    confidence30,
                    confidence50,
                    confidence70
            );

            /*
             * 빈 문자열 등 쓸모없는 OCR 결과를 제거한다.
             *
             * text 생성과 BoundingBox 생성이
             * 반드시 동일한 Word 목록을 기준으로 하도록 한다.
             */
            List<Word> validWords =
                    tessWords.stream()

                            .filter(word ->
                                    word.getText() != null
                                            &&
                                            !word.getText()
                                                    .isBlank()
                            )

                            .toList();

            /*
             * OCR 좌표는 전처리 이미지 기준이다.
             *
             * scaleRatio로 나눠
             * 원본 이미지 좌표계로 복원한다.
             */
            List<OcrWord> words =
                    validWords.stream()

                            .map(word -> {

                                Rectangle box =
                                        word.getBoundingBox();

                                return new OcrWord(

                                        word.getText(),

                                        word.getConfidence(),

                                        (int) Math.round(
                                                box.x
                                                        / scaleRatio
                                        ),

                                        (int) Math.round(
                                                box.y
                                                        / scaleRatio
                                        ),

                                        (int) Math.round(
                                                box.width
                                                        / scaleRatio
                                        ),

                                        (int) Math.round(
                                                box.height
                                                        / scaleRatio
                                        )
                                );
                            })

                            .toList();


            long totalDurationMs =
                    (System.nanoTime() - ocrStartTime)
                            / 1_000_000;


            log.info(
                    "OCR completed. validWordCount={}, textLength={}, durationMs={}",
                    words.size(),
                    text.length(),
                    totalDurationMs
            );


            return new OcrResult(
                    text,
                    words
            );


        } catch (IOException | TesseractException e) {

            throw new OcrException(
                    OcrErrorCode.ANALYSIS_FAILED,
                    e
            );
        }
    }

    private String buildTextFromWords(
            List<Word> words
    ) {

        if (
                words == null
                        ||
                        words.isEmpty()
        ) {
            return "";
        }


        StringBuilder text =
                new StringBuilder();


        Rectangle previousBox =
                null;


        for (Word word : words) {

            String value =
                    word.getText();

            if (
                    value == null
                            ||
                            value.isBlank()
            ) {
                continue;
            }


            Rectangle currentBox =
                    word.getBoundingBox();


            if (
                    previousBox != null
            ) {

                int previousCenterY =
                        previousBox.y
                                +
                                previousBox.height / 2;

                int currentCenterY =
                        currentBox.y
                                +
                                currentBox.height / 2;


                int lineThreshold =
                        Math.max(
                                8,
                                Math.max(
                                        previousBox.height,
                                        currentBox.height
                                ) / 2
                        );


                boolean newLine =
                        Math.abs(
                                currentCenterY
                                        -
                                        previousCenterY
                        ) > lineThreshold;


                text.append(
                        newLine
                                ? '\n'
                                : ' '
                );
            }


            text.append(
                    value.trim()
            );


            previousBox =
                    currentBox;
        }


        return text.toString();
    }

    private ITesseract createTesseract() {
        ITesseract tesseract = new Tesseract();

        tesseract.setDatapath(ocrProperties.getDatapath());
        tesseract.setLanguage(ocrProperties.getLanguage());

        // BufferedImage 변환 과정에서 DPI 정보가 유실될 수 있으므로
        // Tesseract가 저해상도로 판단하지 않도록 OCR DPI를 명시한다.
        tesseract.setVariable(
                "user_defined_dpi",
                String.valueOf(ocrProperties.getDpi())
        );

        tesseract.setPageSegMode(
                ocrProperties.getPageSegMode()
        );

        return tesseract;
    }

    private double resolveScaleRatio(
            BufferedImage originalImage
    ) {

        int width =
                originalImage.getWidth();

        int height =
                originalImage.getHeight();

        int maxDimension =
                Math.max(width, height);

        /*
         * 작은 이미지는 OCR 정확도를 위해 확대하고,
         * 충분한 해상도는 유지하며,
         * 지나치게 큰 이미지만 성능을 위해 축소한다.
         */
        if (maxDimension < MIN_OCR_DIMENSION) {

            double configuredScale =
                    Math.max(
                            1.0,
                            ocrProperties.getScaleFactor()
                    );

            return configuredScale;
        }

        if (maxDimension > MAX_OCR_DIMENSION) {

            return (double) MAX_OCR_DIMENSION
                    / maxDimension;
        }

        return 1.0;
    }

    private BufferedImage preprocess(
            BufferedImage originalImage,
            double scaleRatio
    ) {

        int targetWidth =
                Math.max(
                        1,
                        (int) Math.round(
                                originalImage.getWidth()
                                        * scaleRatio
                        )
                );

        int targetHeight =
                Math.max(
                        1,
                        (int) Math.round(
                                originalImage.getHeight()
                                        * scaleRatio
                        )
                );

        BufferedImage processedImage =
                new BufferedImage(
                        targetWidth,
                        targetHeight,
                        BufferedImage.TYPE_BYTE_GRAY
                );

        Graphics2D graphics =
                processedImage.createGraphics();

        try {

            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );

            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
            );

            graphics.drawImage(
                    originalImage,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    null
            );

        } finally {
            graphics.dispose();
        }

        return processedImage;
    }
}