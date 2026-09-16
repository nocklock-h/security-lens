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

@Service
public class OcrService {

    private final OcrProperties ocrProperties;

    public OcrService(OcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
    }

    public String extractText(MultipartFile image) {
        try {
            BufferedImage originalImage = ImageIO.read(image.getInputStream());

            if (originalImage == null) {
                throw new OcrException(OcrErrorCode.UNSUPPORTED_IMAGE);
            }

            BufferedImage preprocessedImage = preprocess(originalImage);

            ITesseract tesseract = createTesseract();

            return tesseract.doOCR(preprocessedImage);

        } catch (IOException | TesseractException e) {
            throw new OcrException(
                    OcrErrorCode.ANALYSIS_FAILED,
                    e
            );
        }
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

    private BufferedImage preprocess(BufferedImage originalImage) {
        int scaleFactor = ocrProperties.getScaleFactor();

        int targetWidth = originalImage.getWidth() * scaleFactor;
        int targetHeight = originalImage.getHeight() * scaleFactor;

        BufferedImage processedImage = new BufferedImage(
                targetWidth,
                targetHeight,
                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics2D graphics = processedImage.createGraphics();

        try {
            // 작은 글자를 확대할 때 문자 경계가 지나치게 깨지는 것을 줄인다.
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