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

import com.nocklock.securitylens.dto.BoundingBox;

@Service
public class OcrService {

    private final OcrProperties ocrProperties;

    private static final Logger log =
            LoggerFactory.getLogger(OcrService.class);

    private static final int MIN_OCR_DIMENSION = 1600;
    private static final int MAX_OCR_DIMENSION = 3000;

    private static final double DOCUMENT_FALLBACK_MIN_HIGH_CONFIDENCE_RATIO = 0.45;
    private static final int DOCUMENT_FALLBACK_MIN_WORDS = 8;

    private static final int DOCUMENT_PSM = 11;
    private static final int DOCUMENT_TARGET_DIMENSION = 2800;

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
             * Single-pass OCR
             *
             * 기존에는 같은 이미지에 대해
             * doOCR() + getWords()를 각각 실행했다.
             *
             * 이제 getWords() 한 번으로
             * 텍스트와 BoundingBox를 모두 생성한다.
             */
            long recognitionStart =
                    System.nanoTime();


            log.info(
                    "OCR STEP 5 - single-pass recognition started"
            );


            List<Word> tessWords =
                    tesseract.getWords(
                            preprocessedImage,
                            ITessAPI.TessPageIteratorLevel.RIL_WORD
                    );


            long recognitionDurationMs =
                    (System.nanoTime() - recognitionStart)
                            / 1_000_000;


            log.info(
                    "OCR STEP 5 - single-pass recognition completed. durationMs={}, rawWordCount={}",
                    recognitionDurationMs,
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
             * getWords() 결과에서 전체 OCR 텍스트를 재구성한다.
             *
             * 별도의 doOCR() 호출이 없으므로
             * OCR 엔진은 한 번만 실행된다.
             */
            String text =
                    buildTextFromWords(
                            validWords
                    );

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


        } catch (IOException e) {

            throw new OcrException(
                    OcrErrorCode.ANALYSIS_FAILED,
                    e
            );
        }
    }

    public OcrResult analyzeRegion(
            MultipartFile image,
            BoundingBox boundingBox
    ) {

        try {

            BufferedImage originalImage =
                    ImageIO.read(
                            image.getInputStream()
                    );


            if (originalImage == null) {

                throw new OcrException(
                        OcrErrorCode.UNSUPPORTED_IMAGE
                );
            }


            if (boundingBox == null) {

                return new OcrResult(
                        "",
                        List.of()
                );
            }


            int imageWidth =
                    originalImage.getWidth();

            int imageHeight =
                    originalImage.getHeight();


            int x =
                    Math.max(
                            0,
                            (int) Math.floor(
                                    boundingBox.x()
                            )
                    );

            int y =
                    Math.max(
                            0,
                            (int) Math.floor(
                                    boundingBox.y()
                            )
                    );


            int right =
                    Math.min(
                            imageWidth,
                            (int) Math.ceil(
                                    boundingBox.x()
                                            + boundingBox.width()
                            )
                    );

            int bottom =
                    Math.min(
                            imageHeight,
                            (int) Math.ceil(
                                    boundingBox.y()
                                            + boundingBox.height()
                            )
                    );


            int width =
                    right - x;

            int height =
                    bottom - y;


            if (
                    width <= 0
                            ||
                            height <= 0
            ) {

                return new OcrResult(
                        "",
                        List.of()
                );
            }


            Rectangle roi =
                    new Rectangle(
                            x,
                            y,
                            width,
                            height
                    );


            log.info(
                    "OCR document region started. x={}, y={}, width={}, height={}",
                    roi.x,
                    roi.y,
                    roi.width,
                    roi.height
            );


            long startTime =
                    System.nanoTime();


            OcrResult result =
                    recognizeDocumentRegion(
                            originalImage,
                            roi
                    );


            long durationMs =
                    (System.nanoTime() - startTime)
                            / 1_000_000;


            if (result == null) {

                log.info(
                        "OCR document region completed. success=false, durationMs={}",
                        durationMs
                );

                return new OcrResult(
                        "",
                        List.of()
                );
            }


            log.info(
                    "OCR document region completed. success=true, wordCount={}, quality={}, durationMs={}",
                    result.words().size(),
                    calculateOcrQuality(
                            result.words()
                    ),
                    durationMs
            );


            return result;


        } catch (IOException e) {

            throw new OcrException(
                    OcrErrorCode.ANALYSIS_FAILED,
                    e
            );
        }
    }

    private OcrResult tryDocumentFallback(
            BufferedImage originalImage,
            List<OcrWord> primaryWords
    ) {

        Rectangle roi =
                resolveDenseTextRoi(
                        originalImage,
                        primaryWords
                );


        if (roi == null) {

            log.info(
                    "OCR document fallback skipped. denseTextRoiFound=false"
            );

            return null;
        }


        log.info(
                "OCR dense document ROI resolved. x={}, y={}, width={}, height={}",
                roi.x,
                roi.y,
                roi.width,
                roi.height
        );


        OcrResult result =
                recognizeDocumentRegion(
                        originalImage,
                        roi
                );


        if (result == null) {
            return null;
        }


        log.info(
                "OCR dense document fallback completed. wordCount={}, score={}",
                result.words().size(),
                calculateOcrQuality(
                        result.words()
                )
        );


        return result;
    }

    private Rectangle resolveDenseTextRoi(
            BufferedImage image,
            List<OcrWord> words
    ) {

        if (
                words == null
                        ||
                        words.isEmpty()
        ) {

            return null;
        }


        /*
         * 먼저 confidence 50 이상만 사용한다.
         * 너무 적으면 30 이상까지 완화한다.
         */
        List<OcrWord> reliableWords =
                words.stream()

                        .filter(
                                word ->
                                        word.confidence() >= 50
                        )

                        .toList();


        if (reliableWords.size() < 4) {

            reliableWords =
                    words.stream()

                            .filter(
                                    word ->
                                            word.confidence() >= 30
                            )

                            .toList();
        }


        if (reliableWords.size() < 3) {
            return null;
        }


        int searchWidth =
                (int) Math.round(
                        image.getWidth() * 0.55
                );

        int searchHeight =
                (int) Math.round(
                        image.getHeight() * 0.45
                );


        OcrWord bestAnchor =
                null;

        int bestCount =
                0;


        /*
         * 각 OCR Word 주변에 가상의 창을 놓고
         * 가장 많은 신뢰 가능한 Word가 모이는 위치를 찾는다.
         */
        for (OcrWord anchor : reliableWords) {

            int centerX =
                    anchor.x()
                            + anchor.width() / 2;

            int centerY =
                    anchor.y()
                            + anchor.height() / 2;


            int count =
                    0;


            for (OcrWord candidate : reliableWords) {

                int candidateX =
                        candidate.x()
                                + candidate.width() / 2;

                int candidateY =
                        candidate.y()
                                + candidate.height() / 2;


                if (
                        Math.abs(candidateX - centerX)
                                <= searchWidth / 2
                                &&
                                Math.abs(candidateY - centerY)
                                        <= searchHeight / 2
                ) {

                    count++;
                }
            }


            if (count > bestCount) {

                bestCount =
                        count;

                bestAnchor =
                        anchor;
            }
        }


        if (
                bestAnchor == null
                        ||
                        bestCount < 3
        ) {

            return null;
        }


        int bestCenterX =
                bestAnchor.x()
                        + bestAnchor.width() / 2;

        int bestCenterY =
                bestAnchor.y()
                        + bestAnchor.height() / 2;


        final int centerX =
                bestCenterX;

        final int centerY =
                bestCenterY;


        List<OcrWord> cluster =
                reliableWords.stream()

                        .filter(word -> {

                            int x =
                                    word.x()
                                            + word.width() / 2;

                            int y =
                                    word.y()
                                            + word.height() / 2;


                            return Math.abs(x - centerX)
                                    <= searchWidth / 2
                                    &&
                                    Math.abs(y - centerY)
                                            <= searchHeight / 2;
                        })

                        .toList();


        int minX =
                cluster.stream()
                        .mapToInt(OcrWord::x)
                        .min()
                        .orElse(0);

        int minY =
                cluster.stream()
                        .mapToInt(OcrWord::y)
                        .min()
                        .orElse(0);


        int maxX =
                cluster.stream()

                        .mapToInt(
                                word ->
                                        word.x()
                                                + word.width()
                        )

                        .max()
                        .orElse(
                                image.getWidth()
                        );


        int maxY =
                cluster.stream()

                        .mapToInt(
                                word ->
                                        word.y()
                                                + word.height()
                        )

                        .max()
                        .orElse(
                                image.getHeight()
                        );


        int contentWidth =
                Math.max(
                        1,
                        maxX - minX
                );

        int contentHeight =
                Math.max(
                        1,
                        maxY - minY
                );


        /*
         * 이미 읽힌 글자 주변을 넉넉하게 확장한다.
         * 글자를 못 읽은 이름/번호도 crop 안에 포함시키기 위함.
         */
        int marginX =
                Math.max(
                        120,
                        (int) Math.round(
                                contentWidth * 0.45
                        )
                );

        int marginY =
                Math.max(
                        120,
                        (int) Math.round(
                                contentHeight * 0.60
                        )
                );


        int x =
                Math.max(
                        0,
                        minX - marginX
                );

        int y =
                Math.max(
                        0,
                        minY - marginY
                );


        int right =
                Math.min(
                        image.getWidth(),
                        maxX + marginX
                );

        int bottom =
                Math.min(
                        image.getHeight(),
                        maxY + marginY
                );


        return new Rectangle(
                x,
                y,
                right - x,
                bottom - y
        );
    }

    private OcrResult recognizeDocumentRegion(
            BufferedImage originalImage,
            Rectangle roi
    ) {

        OcrResult sparseResult =
                recognizeDocumentRegion(
                        originalImage,
                        roi,
                        DOCUMENT_PSM
                );


        if (
                sparseResult != null
                        &&
                        !isLowQuality(
                                sparseResult
                        )
        ) {

            log.info(
                    "Document OCR PSM 11 accepted. wordCount={}",
                    sparseResult.words().size()
            );

            return sparseResult;
        }


        log.info(
                "Document OCR PSM 11 quality insufficient. retrying with PSM 6"
        );


        OcrResult blockResult =
                recognizeDocumentRegion(
                        originalImage,
                        roi,
                        6
                );


        if (sparseResult == null) {
            return blockResult;
        }


        if (blockResult == null) {
            return sparseResult;
        }


        double sparseScore =
                calculateOcrQuality(
                        sparseResult.words()
                );

        double blockScore =
                calculateOcrQuality(
                        blockResult.words()
                );


        log.info(
                "Document OCR comparison. psm11Score={}, psm6Score={}",
                sparseScore,
                blockScore
        );


        return blockScore > sparseScore
                ? blockResult
                : sparseResult;
    }

    private OcrResult recognizeDocumentRegion(
            BufferedImage originalImage,
            Rectangle roi,
            int pageSegMode
    ) {

        try {

            BufferedImage croppedImage =
                    originalImage.getSubimage(
                            roi.x,
                            roi.y,
                            roi.width,
                            roi.height
                    );


            double scaleRatio =
                    resolveDocumentScaleRatio(
                            croppedImage
                    );


            BufferedImage processedImage =
                    preprocess(
                            croppedImage,
                            scaleRatio
                    );


            ITesseract tesseract =
                    createTesseract(
                            pageSegMode
                    );


            long startTime =
                    System.nanoTime();


            List<Word> tessWords =
                    tesseract.getWords(
                            processedImage,
                            ITessAPI.TessPageIteratorLevel.RIL_WORD
                    );


            long durationMs =
                    (System.nanoTime() - startTime)
                            / 1_000_000;


            List<Word> validWords =
                    tessWords.stream()

                            .filter(
                                    word ->
                                            word.getText() != null
                                                    &&
                                                    !word.getText().isBlank()
                            )

                            .toList();


            String text =
                    buildTextFromWords(
                            validWords
                    );


            List<OcrWord> words =
                    validWords.stream()

                            .map(word -> {

                                Rectangle box =
                                        word.getBoundingBox();


                                return new OcrWord(

                                        word.getText(),

                                        word.getConfidence(),

                                        roi.x
                                                +
                                                (int) Math.round(
                                                        box.x
                                                                / scaleRatio
                                                ),

                                        roi.y
                                                +
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


            log.info(
                    "Document OCR pass completed. psm={}, rawWordCount={}, validWordCount={}, durationMs={}",
                    pageSegMode,
                    tessWords.size(),
                    words.size(),
                    durationMs
            );


            return new OcrResult(
                    text,
                    words
            );


        } catch (Exception e) {

            log.warn(
                    "OCR document region failed. psm={}",
                    pageSegMode,
                    e
            );

            return null;
        }
    }

    public boolean isLowQuality(
            OcrResult result
    ) {

        if (
                result == null
                        ||
                        result.words() == null
                        ||
                        result.words().isEmpty()
        ) {

            return true;
        }


        long confidence70 =
                result.words()
                        .stream()

                        .filter(
                                word ->
                                        word.confidence() >= 70
                        )

                        .count();


        double highConfidenceRatio =
                (double) confidence70
                        / result.words().size();


        return result.words().size()
                < DOCUMENT_FALLBACK_MIN_WORDS
                ||
                highConfidenceRatio
                        < DOCUMENT_FALLBACK_MIN_HIGH_CONFIDENCE_RATIO;
    }

    private double calculateOcrQuality(
            List<OcrWord> words
    ) {

        if (
                words == null
                        ||
                        words.isEmpty()
        ) {

            return 0.0;
        }


        long confidence50 =
                words.stream()

                        .filter(
                                word ->
                                        word.confidence() >= 50
                        )

                        .count();


        long confidence70 =
                words.stream()

                        .filter(
                                word ->
                                        word.confidence() >= 70
                        )

                        .count();


        double averageConfidence =
                words.stream()

                        .mapToDouble(
                                OcrWord::confidence
                        )

                        .average()
                        .orElse(0.0);


        double confidence50Ratio =
                (double) confidence50
                        / words.size();

        double confidence70Ratio =
                (double) confidence70
                        / words.size();


        /*
         * 무작정 많은 단어보다
         * 신뢰도 높은 단어 비율을 훨씬 중요하게 평가한다.
         */
        return averageConfidence
                +
                confidence50Ratio * 40.0
                +
                confidence70Ratio * 60.0
                +
                Math.min(
                        words.size(),
                        40
                ) * 0.5;
    }

    private double resolveDocumentScaleRatio(
            BufferedImage image
    ) {

        int maxDimension =
                Math.max(
                        image.getWidth(),
                        image.getHeight()
                );


        if (maxDimension <= 0) {
            return 1.0;
        }


        double ratio =
                (double) DOCUMENT_TARGET_DIMENSION
                        / maxDimension;


        return Math.max(
                0.5,
                Math.min(
                        3.0,
                        ratio
                )
        );
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

    private ITesseract createTesseract(
            int pageSegMode
    ) {

        ITesseract tesseract =
                new Tesseract();


        tesseract.setDatapath(
                ocrProperties.getDatapath()
        );

        tesseract.setLanguage(
                ocrProperties.getLanguage()
        );


        tesseract.setVariable(
                "user_defined_dpi",
                String.valueOf(
                        ocrProperties.getDpi()
                )
        );


        tesseract.setPageSegMode(
                pageSegMode
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