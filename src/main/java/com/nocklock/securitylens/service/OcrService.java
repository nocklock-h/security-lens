package com.nocklock.securitylens.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import com.nocklock.securitylens.exception.OcrException;
import com.nocklock.securitylens.config.OcrProperties;
import com.nocklock.securitylens.exception.OcrErrorCode;

@Service
public class OcrService {

    private final OcrProperties ocrProperties;

    public OcrService(OcrProperties ocrProperties) {
        this.ocrProperties = ocrProperties;
    }

    public String extractText(MultipartFile image) {

        try {
            BufferedImage bufferedImage =
                    ImageIO.read(image.getInputStream());

            if(bufferedImage == null){
                throw new OcrException(
                        OcrErrorCode.UNSUPPORTED_IMAGE
                );
            }

            ITesseract tesseract = new Tesseract();

            tesseract.setDatapath(ocrProperties.getDatapath());
            tesseract.setLanguage(ocrProperties.getLanguage());

            return tesseract.doOCR(bufferedImage);

        } catch (IOException | TesseractException e) {
            throw new OcrException(
                    OcrErrorCode.ANALYSIS_FAILED,
                    e
            );
        }

    }
}
