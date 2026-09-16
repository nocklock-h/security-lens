package com.nocklock.securitylens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security-lens.ocr")
public class OcrProperties {

    private String datapath;
    private String language;
    private int dpi;
    private int pageSegMode;
    private int scaleFactor;

    public String getDatapath() {
        return datapath;
    }

    public void setDatapath(String datapath) {
        this.datapath = datapath;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getDpi() {
        return dpi;
    }

    public void setDpi(int dpi) {
        this.dpi = dpi;
    }

    public int getPageSegMode() {
        return pageSegMode;
    }

    public void setPageSegMode(int pageSegMode) {
        this.pageSegMode = pageSegMode;
    }

    public int getScaleFactor() {
        return scaleFactor;
    }

    public void setScaleFactor(int scaleFactor) {
        this.scaleFactor = scaleFactor;
    }
}