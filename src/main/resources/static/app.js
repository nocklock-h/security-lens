// ============================================================
// Security Lens Frontend
// ============================================================

const FAST_API_URL = "/api/v1/images/analyze/fast";

const DEEP_API_URL = "/api/v1/images/analyze/deep";

const UI_MESSAGE_URL = "/api/v1/ui/messages";


// ============================================================
// STATE
// ============================================================

let selectedFile = null;
let previewUrl = null;
let loadingInterval = null;

let latestAnalysis = null;
let protectedPreviewUrl = null;

let protectedBlob = null;

let messages = {};
let currentLanguage = "en";

let analysisRequestId = 0;


// ============================================================
// DOM ELEMENTS
// ============================================================

const uploadSection =
    document.getElementById("uploadSection");

const previewSection =
    document.getElementById("previewSection");

const loadingSection =
    document.getElementById("loadingSection");

const resultSection =
    document.getElementById("resultSection");

const errorSection =
    document.getElementById("errorSection");


const dropZone =
    document.getElementById("dropZone");

const fileInput =
    document.getElementById("fileInput");


const fileName =
    document.getElementById("fileName");

const fileSize =
    document.getElementById("fileSize");


const resetButton =
    document.getElementById("resetButton");

const analyzeButton =
    document.getElementById("analyzeButton");

const newAnalysisButton =
    document.getElementById("newAnalysisButton");

const retryButton =
    document.getElementById("retryButton");

const protectButton =
    document.getElementById("protectButton");


const protectButtonLabel =
    protectButton?.querySelector(
        'span[data-i18n="ui.result.protect"]'
    );


const protectStatus =
    protectButton?.querySelector(
        "small"
    );

const previewImage =
    document.getElementById("previewImage");

const imageStage =
    document.getElementById("imageStage");

const overlayLayer =
    document.getElementById("overlayLayer");


const loadingMessage =
    document.getElementById("loadingMessage");

const loadingProgress =
    document.getElementById("loadingProgress");

const loadingProgressBar =
    document.getElementById("loadingProgressBar");


const riskScore =
    document.getElementById("riskScore");

const riskLevel =
    document.getElementById("riskLevel");

const riskDescription =
    document.getElementById("riskDescription");

const riskCircle =
    document.getElementById("riskCircle");


const visibleCount =
    document.getElementById("visibleCount");

const hiddenCount =
    document.getElementById("hiddenCount");

const detectedItems =
    document.getElementById("detectedItems");

const metadataItems =
    document.getElementById("metadataItems");

const documentTypeCard =
    document.getElementById("documentTypeCard");

const documentTypeTitle =
    document.getElementById("documentTypeTitle");

const documentTypeHeading =
    document.getElementById("documentTypeHeading");

const documentTypeDescription =
    document.getElementById("documentTypeDescription");

const documentTypeLabel =
    document.getElementById("documentTypeLabel");

const documentTypeValue =
    document.getElementById("documentTypeValue");

const aiContext =
    document.getElementById("aiContext");

const aiSummary =
    document.getElementById("aiSummary");


const imageResolution =
    document.getElementById("imageResolution");


const errorMessage =
    document.getElementById("errorMessage");

const analysisResultStatus =
    document.getElementById(
        "analysisResultStatus"
    );

const imageScanStatus =
    document.getElementById(
        "imageScanStatus"
    );


// ============================================================
// I18N
// ============================================================

function t(key, fallback = key) {

    return messages[key] ?? fallback;
}


async function initializeI18n() {

    const browserLanguage =
        navigator.languages?.[0] ||
        navigator.language ||
        "en";


    currentLanguage =
        browserLanguage
            .toLowerCase()
            .startsWith("ko")
            ? "ko"
            : "en";


    try {

        const response =
            await fetch(
                `${UI_MESSAGE_URL}?lang=${encodeURIComponent(currentLanguage)}`
            );


        if (!response.ok) {

            throw new Error(
                `Failed to load UI messages (${response.status})`
            );
        }


        const data =
            await response.json();

        messages =
            data.messages || {};


        currentLanguage =
            data.locale || currentLanguage;


        document.documentElement.lang =
            currentLanguage;


        applyStaticTranslations();


    } catch (error) {

        /*
            i18n API가 실패해도
            HTML에 작성된 기본 영어 문구로 서비스는 정상 동작한다.
        */

        console.error(
            "Failed to initialize Security Lens i18n.",
            error
        );


        document.documentElement.lang =
            currentLanguage;
    }
}


function applyStaticTranslations() {

    const elements =
        document.querySelectorAll(
            "[data-i18n]"
        );


    elements.forEach(element => {

        const key =
            element.dataset.i18n;


        element.textContent =
            t(
                key,
                element.textContent.trim()
            );
    });
}


// ============================================================
// FILE SELECT
// ============================================================

fileInput.addEventListener(
    "change",
    event => {

        const file =
            event.target.files?.[0];


        if (file) {
            selectFile(file);
        }
    }
);


// ============================================================
// DRAG & DROP
// ============================================================

[
    "dragenter",
    "dragover"
]
    .forEach(eventName => {

        dropZone.addEventListener(
            eventName,
            event => {

                event.preventDefault();

                dropZone.classList.add(
                    "drag-over"
                );
            }
        );
    });


[
    "dragleave",
    "drop"
]
    .forEach(eventName => {

        dropZone.addEventListener(
            eventName,
            event => {

                event.preventDefault();

                dropZone.classList.remove(
                    "drag-over"
                );
            }
        );
    });


dropZone.addEventListener(
    "drop",
    event => {

        const files =
            event.dataTransfer?.files;


        if (!files || files.length === 0) {
            return;
        }


        selectFile(files[0]);
    }
);


// ============================================================
// SELECT FILE
// ============================================================

function selectFile(file) {

    const extensionMatch =
        file.name
            ?.toLowerCase()
            .match(/\.(jpg|jpeg|png)$/);


    const validExtension =
        Boolean(extensionMatch);


    const allowedMimeTypes =
        new Set([
            "image/jpeg",
            "image/png"
        ]);


    /*
     * 일부 환경에서는 file.type이 비어 있을 수 있으므로
     * MIME이 제공된 경우에만 형식을 엄격하게 확인한다.
     * 실제 보안 검증은 Backend가 담당한다.
     */
    const validMimeType =
        !file.type ||
        allowedMimeTypes.has(
            file.type.toLowerCase()
        );


    const validSize =
        file.size <=
        30 * 1024 * 1024;


    if (
        !validExtension ||
        !validMimeType
    ) {

        showError(
            t(
                "ui.error.unsupportedImage",
                "Only JPG, JPEG, and PNG image files are supported."
            )
        );

        return;
    }


    if (!validSize) {

        showError(
            t(
                "ui.error.imageTooLarge",
                "Image files must be 30 MB or smaller."
            )
        );

        return;
    }


    selectedFile = file;


    if (previewUrl) {

        URL.revokeObjectURL(
            previewUrl
        );
    }


    previewUrl =
        URL.createObjectURL(file);


    previewImage.src =
        previewUrl;

    const previewAlt =
        t(
            "ui.preview.imageAlt",
            "Image selected for privacy analysis"
        );


    previewImage.alt =
        file.name
            ? `${file.name} - ${previewAlt}`
            : previewAlt;


    fileName.textContent =
        file.name;


    fileSize.textContent =
        formatFileSize(
            file.size
        );


    clearAnalysisResult();


    showOnly(
        previewSection
    );
}


// ============================================================
// ANALYZE
// ============================================================

analyzeButton.addEventListener(
    "click",
    analyzeImage
);


async function analyzeImage() {

    if (!selectedFile) {
        return;
    }


    const requestId =
        ++analysisRequestId;


    showOnly(
        loadingSection
    );


    startLoadingAnimation();


    try {

        /*
         * ==========================================
         * 1. FAST SCAN
         * ==========================================
         */

        const fastData =
            await requestImageAnalysis(
                FAST_API_URL
            );


        /*
         * 사용자가 분석 도중 새 이미지로 넘어갔다면
         * 이전 요청 결과를 버린다.
         */
        if (
            requestId !==
            analysisRequestId
        ) {
            return;
        }


        latestAnalysis =
            fastData;


        stopLoadingAnimation();


        updateProtectionAvailability(
            fastData
        );


        renderAnalysis(
            fastData
        );


        /*
         * FAST가 끝나는 즉시 결과 화면을 보여준다.
         */
        showOnly(
            resultSection
        );


        requestAnimationFrame(
            () => {

                syncOverlayToImage();


                renderBoundingBoxes(
                    fastData.detectedItems || [],
                    fastData.image
                );


                resultSection.focus();
            }
        );


        showDeepAnalysisStatus();

        showDeepAnalysisPending();


        /*
         * ==========================================
         * 2. DEEP AI
         * ==========================================
         *
         * await 하지 않는다.
         * FAST 화면은 이미 사용자에게 표시된다.
         */
        void runDeepAnalysis(
            requestId
        );


    } catch (error) {

        console.error(
            error
        );


        stopLoadingAnimation();


        showError(
            error.message ||
            t(
                "ui.error.analysis",
                "An error occurred while analyzing this image."
            )
        );
    }
}


async function runDeepAnalysis(
    requestId
) {

    try {

        const deepData =
            await requestImageAnalysis(
                DEEP_API_URL
            );


        /*
         * 다른 사진 분석이 시작되었다면
         * 늦게 도착한 이전 결과는 무시한다.
         */
        if (
            requestId !==
            analysisRequestId
        ) {
            return;
        }

        stopRiskAnalyzingAnimation();

        showAnalysisCompleteStatus();

        resultSection.setAttribute(
            "aria-busy",
            "false"
        );

        latestAnalysis =
            deepData;


        updateProtectionAvailability(
            deepData
        );


        /*
         * AI 결과로 화면 전체를 자연스럽게 갱신한다.
         */
        renderAnalysis(
            deepData
        );


        requestAnimationFrame(
            () => {

                syncOverlayToImage();


                renderBoundingBoxes(
                    deepData.detectedItems || [],
                    deepData.image
                );
            }
        );


        console.log(
            "Security Lens Deep AI completed.",
            deepData
        );


    } catch (error) {

        console.error(
            "Security Lens Deep AI failed.",
            error
        );


        /*
         * Deep AI가 실패해도
         * 이미 나온 FAST 결과는 없애지 않는다.
         */
        if (
            requestId !==
            analysisRequestId
        ) {
            return;
        }


        showDeepAnalysisFailed();
    }
}


async function requestImageAnalysis(
    url
) {

    const formData =
        new FormData();


    formData.append(
        "image",
        selectedFile
    );


    const response =
        await fetch(
            url,
            {
                method: "POST",

                headers: {
                    "Accept-Language":
                    currentLanguage
                },

                body:
                formData
            }
        );


    if (!response.ok) {

        let message =
            `${t(
                "ui.error.analysis",
                "An error occurred while analyzing this image."
            )} (${response.status})`;


        try {

            const errorData =
                await response.json();


            if (
                errorData &&
                errorData.message
            ) {

                message =
                    errorData.message;
            }

        } catch (_) {

            /*
             * JSON 오류 응답이 아니면
             * 기본 메시지를 유지한다.
             */
        }


        throw new Error(
            message
        );
    }


    return await response.json();
}

function hasProtectableMetadata(data) {

    const metadata =
        data?.metadata;

    if (!metadata) {
        return false;
    }

    return Boolean(
        metadata.gps?.detected ||
        metadata.capturedAt ||
        metadata.device?.detected
    );
}

function updateProtectionAvailability(
    data
) {

    const protectableItems =
        Array.isArray(
            data.detectedItems
        )
            ? data.detectedItems
                .filter(item => {

                    if (
                        isDocumentType(
                            item
                        )
                    ) {

                        return false;
                    }


                    const box =
                        item.boundingBox;


                    return Boolean(
                        box &&
                        box.width > 0 &&
                        box.height > 0
                    );
                })
            : [];


    const hasMetadata =
        hasProtectableMetadata(data);

    const canProtect =
        protectableItems.length > 0 ||
        hasMetadata;


    protectButton.disabled =
        !canProtect;


    protectButton.setAttribute(
        "aria-disabled",
        String(
            !canProtect
        )
    );


    if (protectStatus) {

        protectStatus.textContent =
            canProtect
                ? t(
                    "ui.result.readyToProtect",
                    "Ready"
                )
                : t(
                    "ui.result.nothingToProtect",
                    "No protectable area"
                );
    }
}


function showDeepAnalysisPending() {

    const items =
        Array.isArray(latestAnalysis?.detectedItems)
            ? latestAnalysis.detectedItems
            : [];


    if (items.length === 0) {

        startRiskAnalyzingAnimation();


        riskLevel.textContent =
            t(
                "ui.deep.analyzing"
            );


        riskDescription.textContent =
            t(
                "ui.deep.pendingDescription"
            );


        visibleCount.textContent =
            "…";


        detectedItems.innerHTML = `
            <div class="empty-result">
                ${escapeHtml(
            t(
                "ui.deep.pendingExposure"
            )
        )}
            </div>
        `;

    } else {

        /*
         * FAST 단계에서 이미 점수가 나온 경우에는
         * 점수를 유지하면서 Deep AI 진행 상태만 표시한다.
         */
        if (riskCircle) {

            riskCircle.classList.add(
                "is-analyzing"
            );
        }


        riskDescription.textContent =
            t(
                "ui.deep.pendingRiskDescription"
            );
    }


    aiContext.textContent =
        t(
            "ui.deep.pendingContext"
        );


    aiSummary.textContent =
        t(
            "ui.deep.pendingSummary"
        );
}


function showDeepAnalysisFailed() {

    stopRiskAnalyzingAnimation();

    showDeepAnalysisFailedStatus();

    resultSection.setAttribute(
        "aria-busy",
        "false"
    );


    if (latestAnalysis) {

        renderAnalysis(
            latestAnalysis
        );
    }


    aiContext.textContent =
        t(
            "ui.deep.failedContext",
            "Fast scan complete · Deep AI analysis failed"
        );


    aiSummary.textContent =
        t(
            "ui.deep.failedSummary",
            "Deep AI analysis could not be completed. The fast scan result remains available."
        );
}

function showDeepAnalysisStatus() {

    if (analysisResultStatus) {
        analysisResultStatus.textContent =
            t(
                "ui.deep.resultPending"
            );
    }


    if (imageScanStatus) {
        imageScanStatus.textContent =
            t(
                "ui.deep.scanPending"
            );
    }


    resultSection.setAttribute(
        "aria-busy",
        "true"
    );
}


function showAnalysisCompleteStatus() {

    if (analysisResultStatus) {
        analysisResultStatus.textContent =
            t(
                "ui.result.complete"
            );
    }


    if (imageScanStatus) {
        imageScanStatus.textContent =
            t(
                "ui.result.scanComplete"
            );
    }


    resultSection.setAttribute(
        "aria-busy",
        "false"
    );
}


function showDeepAnalysisFailedStatus() {

    if (analysisResultStatus) {
        analysisResultStatus.textContent =
            t(
                "ui.deep.resultFailed"
            );
    }


    if (imageScanStatus) {
        imageScanStatus.textContent =
            t(
                "ui.deep.scanCompleteDeepFailed"
            );
    }


    resultSection.setAttribute(
        "aria-busy",
        "false"
    );
}


// ============================================================
// ANALYSIS RENDER
// ============================================================

function startRiskAnalyzingAnimation() {
    if (riskCircle) {
        riskCircle.classList.add("is-analyzing");
    }

    if (riskScore) {
        riskScore.innerHTML = `
            <span class="loading-dots" aria-label="${escapeHtml(
            t(
                "ui.deep.analyzing",
                "Analyzing"
            )
        )}">
                <span></span>
                <span></span>
                <span></span>
            </span>
        `;
    }
}

function stopRiskAnalyzingAnimation() {
    if (riskCircle) {
        riskCircle.classList.remove("is-analyzing");
    }
}

function renderAnalysis(data) {

    const items =
        Array.isArray(data.detectedItems)
            ? data.detectedItems
            : [];


    renderRisk(
        data
    );


    renderDocumentType(
        items
    );


    renderVisibleExposure(
        items
    );


    renderMetadata(
        data.metadata
    );


    renderAiContext(
        data
    );


    renderSummary(
        data
    );


    renderImageInfo(
        data.image
    );
}

// ============================================================
// RISK
// ============================================================

function renderRisk(data) {

    const rawScore =
        Number(
            data.riskScore ?? 0
        );


    const score =
        Number.isFinite(rawScore)
            ? Math.min(
                Math.max(rawScore, 0),
                100
            )
            : 0;


    const level =
        String(
            data.riskLevel || "UNKNOWN"
        )
            .toUpperCase();


    riskScore.textContent =
        Math.round(score);


    riskLevel.textContent =
        formatRiskLevel(level);

    function formatRiskLevel(level) {

        switch (level) {

            case "CRITICAL":
                return t(
                    "ui.risk.level.critical",
                    "Critical"
                );

            case "HIGH":
                return t(
                    "ui.risk.level.high",
                    "High"
                );

            case "MEDIUM":
                return t(
                    "ui.risk.level.medium",
                    "Medium"
                );

            case "LOW":
                return t(
                    "ui.risk.level.low",
                    "Low"
                );

            default:
                return t(
                    "ui.risk.level.unknown",
                    "Unknown"
                );
        }
    }


    const riskConfig =
        getRiskConfig(
            level
        );


    riskLevel.style.color =
        riskConfig.color;


    riskLevel.style.background =
        riskConfig.background;


    riskDescription.textContent =
        riskConfig.description;


    const degree =
        score * 3.6;


    riskCircle.style.background =
        `conic-gradient(
            ${riskConfig.color} 0deg,
            ${riskConfig.color} ${degree}deg,
            #202833 ${degree}deg
        )`;
}


function getRiskConfig(level) {

    switch (level) {

        case "CRITICAL":

            return {
                color:
                    "#ff4d5f",

                background:
                    "rgba(255, 77, 95, 0.10)",

                description:
                    t(
                        "ui.risk.critical",
                        "Privacy exposure is very high. Review this image before sharing it."
                    )
            };


        case "HIGH":

            return {
                color:
                    "#ff7a4d",

                background:
                    "rgba(255, 122, 77, 0.10)",

                description:
                    t(
                        "ui.risk.high",
                        "Multiple privacy exposure risks were detected."
                    )
            };


        case "MEDIUM":

            return {
                color:
                    "#ffd95a",

                background:
                    "rgba(255, 217, 90, 0.10)",

                description:
                    t(
                        "ui.risk.medium",
                        "Some privacy exposure risks were detected."
                    )
            };


        case "LOW":

            return {
                color:
                    "#52d6a0",

                background:
                    "rgba(82, 214, 160, 0.10)",

                description:
                    t(
                        "ui.risk.low",
                        "The detected privacy exposure risk is currently low."
                    )
            };


        default:

            return {
                color:
                    "#8b97a8",

                background:
                    "rgba(139, 151, 168, 0.10)",

                description:
                    t(
                        "ui.result.description",
                        "Review the privacy exposure detected in this image."
                    )
            };
    }
}

// ============================================================
// DOCUMENT CONTEXT
// ============================================================

function isDocumentType(item) {

    return String(
            item?.type || ""
        ).toUpperCase() ===
        "SENSITIVE_DOCUMENT";
}


function renderDocumentType(items) {

    const documentItem =
        items.find(
            item =>
                isDocumentType(item)
        );


    if (!documentItem) {

        documentTypeCard.classList.add(
            "hidden"
        );

        documentTypeValue.textContent =
            "";

        return;
    }


    /*
        기존 i18n API에 새 키를 추가하지 않아도
        한국어 / 영어가 자연스럽게 보이도록 처리.
    */

    documentTypeTitle.textContent =
        t(
            "ui.document.title",
            "Document Type"
        );

    documentTypeHeading.textContent =
        t(
            "ui.document.title",
            "Document Type"
        );

    documentTypeDescription.textContent =
        t(
            "ui.document.description",
            "Document context recognized from the image."
        );

    documentTypeLabel.textContent =
        t(
            "ui.document.recognized",
            "Recognized document"
        );


    /*
        기존 함수가 DRIVER_LICENSE를 이미 처리한다.
    */
    documentTypeValue.textContent =
        formatDetectedValue(
            documentItem
        );


    documentTypeCard.classList.remove(
        "hidden"
    );
}

// ============================================================
// VISIBLE EXPOSURE
// ============================================================

function renderVisibleExposure(items) {

    detectedItems.innerHTML =
        "";


    /*
        SENSITIVE_DOCUMENT는 개인정보 자체가 아니라
        이미지 전체에 대한 문서 분류 결과다.

        따라서 Visible Exposure에서 제외한다.
    */
    const privacyItems =
        items.filter(
            item =>
                !isDocumentType(item)
        );


    visibleCount.textContent =
        privacyItems.length;


    if (privacyItems.length === 0) {

        detectedItems.innerHTML =
            `
                <div class="empty-result">
                    ${escapeHtml(
                t(
                    "ui.empty.visible",
                    "No directly visible personal information was detected."
                )
            )}
                </div>
            `;


        return;
    }


    privacyItems.forEach(item => {

        const element =
            document.createElement(
                "div"
            );


        element.className =
            "detected-item";


        const type =
            formatPiiType(
                item.type
            );


        const value =
            formatDetectedValue(
                item
            );


        const box =
            item.boundingBox;


        const protectable =
            Boolean(
                box &&
                box.width > 0 &&
                box.height > 0
            );


        const itemId =
            String(
                item.id || ""
            );


        element.innerHTML =
            `
        <div class="detected-item-left">

            <div class="detected-item-type">
                ${escapeHtml(type)}
            </div>

            <div class="detected-item-value">
                ${escapeHtml(value)}
            </div>

        </div>

        <label class="protect-choice">

            <input
                type="checkbox"
                class="protect-item-checkbox"
                data-item-id="${escapeHtml(itemId)}"
                ${protectable ? "checked" : "disabled"}
            >

            <span>
                ${escapeHtml(
                t(
                    "ui.result.protectThis"
                )
            )}
            </span>

        </label>
    `;


        detectedItems.appendChild(
            element
        );
    });
}


// ============================================================
// METADATA
// ============================================================

function renderMetadata(metadata) {

    metadataItems.innerHTML =
        "";


    let count = 0;


    if (!metadata) {

        hiddenCount.textContent =
            "0";


        showNoMetadata();

        return;
    }


    // GPS
    if (
        metadata.gps &&
        metadata.gps.detected
    ) {

        count++;

        /* TODO 좌표를 지도로
        appendMetadataItem(
            t(
                "ui.metadata.location",
                "GPS Location"
            ),
            formatCoordinates(
                metadata.gps.latitude,
                metadata.gps.longitude
            )
        );
        */

        appendMetadataItem(
            t(
                "ui.metadata.location",
                "Location Information"
            ),
            t(
                "ui.metadata.locationDetected",
                "Location data is embedded in this image."
            )
        );
    }


    // CAPTURE TIME
    if (metadata.capturedAt) {

        count++;


        appendMetadataItem(
            t(
                "ui.metadata.capturedAt",
                "Captured At"
            ),
            formatCapturedAt(
                metadata.capturedAt
            )
        );
    }


    // DEVICE
    if (
        metadata.device &&
        metadata.device.detected
    ) {

        count++;


        const deviceName =
            [
                metadata.device.make,
                metadata.device.model
            ]
                .filter(Boolean)
                .join(" ");


        appendMetadataItem(
            t(
                "ui.metadata.device",
                "Device"
            ),
            deviceName ||
            t(
                "ui.metadata.deviceDetected",
                "Device information detected"
            )
        );
    }


    hiddenCount.textContent =
        count;


    if (count === 0) {

        showNoMetadata();
    }
}


function appendMetadataItem(
    type,
    value
) {

    const element =
        document.createElement(
            "div"
        );


    element.className =
        "detected-item";


    element.innerHTML =
        `
            <div class="detected-item-left">

                <div class="detected-item-type">
                    ${escapeHtml(type)}
                </div>

                <div class="detected-item-value">
                    ${escapeHtml(value)}
                </div>

            </div>
        `;


    metadataItems.appendChild(
        element
    );
}


function showNoMetadata() {

    metadataItems.innerHTML =
        `
            <div class="empty-result">
                ${escapeHtml(
            t(
                "ui.empty.metadata",
                "No hidden location or device metadata was detected."
            )
        )}
            </div>
        `;
}

// ============================================================
// AI CONTEXT
// ============================================================

function renderAiContext(data) {

    const items =
        Array.isArray(data.detectedItems)
            ? data.detectedItems
            : [];


    const signals =
        [];


    /*
        1. 문서 맥락
    */
    const documentItem =
        items.find(
            item =>
                isDocumentType(item)
        );


    if (documentItem) {

        signals.push(
            formatDetectedValue(
                documentItem
            )
        );
    }


    /*
        2. 화면에서 탐지된 개인정보
    */
    items
        .filter(
            item =>
                !isDocumentType(item)
        )
        .forEach(item => {

            const label =
                formatPiiType(
                    item.type
                );


            if (
                label &&
                !signals.includes(label)
            ) {

                signals.push(
                    label
                );
            }
        });


    /*
        3. 숨겨진 메타데이터
    */
    const metadata =
        data.metadata;


    if (
        metadata?.gps?.detected
    ) {

        signals.push(
            t(
                "ui.ai.locationMetadata",
                "Location Metadata"
            )
        );
    }


    if (
        metadata?.device?.detected
    ) {

        signals.push(
            t(
                "ui.ai.deviceInformation",
                "Device Information"
            )
        );
    }


    if (metadata?.capturedAt) {

        signals.push(
            t(
                "ui.ai.captureTime",
                "Capture Time"
            )
        );
    }


    if (signals.length === 0) {

        aiContext.textContent =
            t(
                "ui.ai.noSignals",
                "No clear privacy exposure signals were detected."
            );

        return;
    }


    aiContext.textContent =
        signals.join(" · ");
}

// ============================================================
// AI SUMMARY
// ============================================================

function renderSummary(data) {

    if (
        data.summary &&
        data.summary.trim().length > 0
    ) {

        aiSummary.textContent =
            data.summary;


        return;
    }


    if (
        Array.isArray(
            data.inferences
        ) &&
        data.inferences.length > 0
    ) {

        const inferenceText =
            data.inferences

                .map(item =>
                    item.description ||
                    item.message ||
                    item.content
                )

                .filter(Boolean)

                .join(" ");


        if (inferenceText) {

            aiSummary.textContent =
                inferenceText;


            return;
        }
    }


    aiSummary.textContent =
        t(
            "ui.result.description",
            "Security Lens analyzed the image for privacy exposure."
        );
}


// ============================================================
// IMAGE INFO
// ============================================================

function renderImageInfo(image) {

    if (
        image &&
        image.width &&
        image.height
    ) {

        imageResolution.textContent =
            `${image.width} × ${image.height}`;


        return;
    }


    /*
        Backend에서 해상도를 주지 않는 경우
        브라우저가 읽은 실제 이미지 크기를 fallback으로 사용.
    */

    if (
        previewImage.naturalWidth &&
        previewImage.naturalHeight
    ) {

        imageResolution.textContent =
            `${previewImage.naturalWidth} × ${previewImage.naturalHeight}`;


        return;
    }


    imageResolution.textContent =
        "";
}


// ============================================================
// OVERLAY PREPARATION
// ============================================================

/*
    내일 BoundingBox 구현 시 중요.

    image-stage 전체가 아니라
    실제 렌더링된 이미지 영역에 overlay를 정확히 맞춘다.

    이후 BoundingBox 좌표는 overlayLayer 기준으로 그린다.
*/
function syncOverlayToImage() {

    if (
        !previewImage ||
        !imageStage ||
        !overlayLayer
    ) {
        return;
    }


    const imageRect =
        previewImage.getBoundingClientRect();


    const stageRect =
        imageStage.getBoundingClientRect();


    if (
        imageRect.width === 0 ||
        imageRect.height === 0
    ) {
        return;
    }


    overlayLayer.style.left =
        `${imageRect.left - stageRect.left}px`;


    overlayLayer.style.top =
        `${imageRect.top - stageRect.top}px`;


    overlayLayer.style.width =
        `${imageRect.width}px`;


    overlayLayer.style.height =
        `${imageRect.height}px`;
}

function renderBoundingBoxes(
    items,
    image
) {

    overlayLayer.innerHTML = "";


    if (
        !Array.isArray(items) ||
        !image ||
        !image.width ||
        !image.height
    ) {
        return;
    }


    items.forEach(item => {

        /*
            운전면허증 같은 문서 분류는
            이미지 전체 맥락이므로 PII 박스로 표시하지 않는다.
        */
        if (isDocumentType(item)) {
            return;
        }


        const box =
            item.boundingBox;


        if (
            !box ||
            box.width <= 0 ||
            box.height <= 0
        ) {
            return;
        }


        const element =
            document.createElement(
                "div"
            );


        element.className =
            "detection-box";


        element.style.left =
            `${(box.x / image.width) * 100}%`;

        element.style.top =
            `${(box.y / image.height) * 100}%`;

        element.style.width =
            `${(box.width / image.width) * 100}%`;

        element.style.height =
            `${(box.height / image.height) * 100}%`;

        overlayLayer.appendChild(
            element
        );
    });
}

previewImage.addEventListener(
    "load",
    () => {

        requestAnimationFrame(
            syncOverlayToImage
        );
    }
);


window.addEventListener(
    "resize",
    () => {

        requestAnimationFrame(
            () => {

                syncOverlayToImage();
            }
        );
    }
);

// ============================================================
// IMAGE PROTECTION
// ============================================================

protectButton.addEventListener(
    "click",
    protectImage
);


function protectImage() {

    /*
        이미 보호 이미지가 만들어졌다면
        두 번째 클릭은 다운로드로 사용한다.
    */
    if (protectedPreviewUrl) {

        downloadProtectedImage();

        return;
    }


    if (
        !latestAnalysis ||
        !previewImage.naturalWidth ||
        !previewImage.naturalHeight
    ) {

        return;
    }


    const items =
        Array.isArray(
            latestAnalysis.detectedItems
        )
            ? latestAnalysis.detectedItems
            : [];

    const selectedItemIds =
        new Set(
            Array.from(
                document.querySelectorAll(
                    ".protect-item-checkbox:checked"
                )
            )
                .map(
                    checkbox =>
                        checkbox.dataset.itemId
                )
                .filter(Boolean)
        );


    const hasMetadata =
        hasProtectableMetadata(
            latestAnalysis
        );

    if (
        selectedItemIds.size === 0 &&
        !hasMetadata
    ) {

        alert(
            t(
                "ui.result.selectProtection",
                "Select at least one item to protect."
            )
        );

        return;
    }


    const width =
        previewImage.naturalWidth;


    const height =
        previewImage.naturalHeight;


    const canvas =
        document.createElement(
            "canvas"
        );


    canvas.width =
        width;


    canvas.height =
        height;


    const context =
        canvas.getContext(
            "2d"
        );


    if (!context) {
        return;
    }


    /*
        원본 이미지를 Canvas에 그대로 복사한다.
    */
    context.drawImage(
        previewImage,
        0,
        0,
        width,
        height
    );


    /*
        실제 이미지 크기에 따라
        적당한 보호 여백을 계산한다.
    */
    const protectionPadding =
        Math.max(
            3,
            Math.min(
                8,
                Math.round(
                    Math.min(
                        width,
                        height
                    ) * 0.003
                )
            )
        );


    items.forEach(item => {

        /*
            문서 유형 자체는 PII가 아니므로
            전체 면허증을 가리지 않는다.
        */
        if (isDocumentType(item)) {
            return;
        }

        if (
            !selectedItemIds.has(
                String(item.id)
            )
        ) {

            return;
        }

        const box =
            item.boundingBox;


        if (
            !box ||
            box.width <= 0 ||
            box.height <= 0
        ) {

            return;
        }


        /*
            실제 보호에서는 분석 Box보다
            조금 더 넓게 가린다.

            이유:
            문자 가장자리가 남는 것을 방지하기 위해서다.
        */
        const x =
            Math.max(
                0,
                box.x -
                protectionPadding
            );


        const y =
            Math.max(
                0,
                box.y -
                protectionPadding
            );


        const maskWidth =
            Math.min(
                width - x,
                box.width +
                protectionPadding * 2
            );


        const maskHeight =
            Math.min(
                height - y,
                box.height +
                protectionPadding * 2
            );


        /*
            MVP에서는 Blur보다
            완전 불투명 Masking을 사용한다.

            개인정보 보호 측면에서
            약한 Blur보다 안전하다.
        */
        context.fillStyle =
            "#0b0f14";


        const radius =
            Math.min(
                6,
                maskHeight / 4
            );


        context.beginPath();


        if (
            typeof context.roundRect ===
            "function"
        ) {

            context.roundRect(
                x,
                y,
                maskWidth,
                maskHeight,
                radius
            );

            context.fill();

        } else {

            /*
               오래된 Browser fallback
           */
            context.fillRect(
                x,
                y,
                maskWidth,
                maskHeight
            );
        }

    });

    /*
        JPEG/PNG를 Canvas에서 다시 생성하면
        기존 EXIF 메타데이터도 포함되지 않는다.

        즉:
        화면 PII Masking +
        숨겨진 Metadata 제거
        를 동시에 수행하게 된다.
    */
    const outputType =
        selectedFile?.type === "image/png"
            ? "image/png"
            : "image/jpeg";


    canvas.toBlob(
        blob => {

            if (!blob) {
                return;
            }


            if (protectedPreviewUrl) {

                URL.revokeObjectURL(
                    protectedPreviewUrl
                );
            }

            protectedBlob = blob;

            protectedPreviewUrl =
                URL.createObjectURL(
                    blob
                );

            protectButton.disabled =
                false;


            protectButton.setAttribute(
                "aria-disabled",
                "false"
            );

            previewImage.src =
                protectedPreviewUrl;


            previewImage.alt =
                t(
                    "ui.protect.previewAlt",
                    "Protected privacy-safe image"
                );


            /*
                보호 결과에서는
                빨간 탐지 Box가 더 이상 필요 없다.
            */
            overlayLayer.innerHTML =
                "";


            if (protectButtonLabel) {

                protectButtonLabel.textContent =
                    t(
                        "ui.result.downloadProtected",
                        "Download protected image"
                    );
            }


            if (protectStatus) {

                protectStatus.textContent =
                    t(
                        "ui.result.protected",
                        "Protected"
                    );
            }

        },
        outputType,
        0.94
    );
}

async function downloadProtectedImage() {

    if (
        !protectedPreviewUrl ||
        !protectedBlob
    ) {
        return;
    }


    const extension =
        selectedFile?.type === "image/png"
            ? "png"
            : "jpg";


    const originalName =
        selectedFile?.name
            ?.replace(
                /\.[^.]+$/,
                ""
            )
        ||
        "security-lens";


    const downloadName =
        `${originalName}-protected.${extension}`;


    const file =
        new File(
            [protectedBlob],
            downloadName,
            {
                type:
                    protectedBlob.type ||
                    (extension === "png"
                        ? "image/png"
                        : "image/jpeg")
            }
        );


    /*
     * 모바일 / 인앱 브라우저에서는
     * Web Share API를 우선 사용한다.
     *
     * 카카오톡 등에서 blob 다운로드가 막혀도
     * Android / iOS 시스템 공유창을 통해
     * 이미지 저장 또는 다른 앱으로 공유할 수 있다.
     */
    const canShareFile =
        typeof navigator.share === "function" &&
        typeof navigator.canShare === "function" &&
        navigator.canShare({
            files: [file]
        });

    const userAgent =
        navigator.userAgent || "";

    const isAndroidKakao =
        /Android/i.test(userAgent) &&
        /KAKAOTALK/i.test(userAgent);

    // TODO: 카카오톡 인앱 브라우저 안내 문구를
    //       messages.properties / i18n 메시지 API로 분리한다.
    if (isAndroidKakao) {

        alert(
            currentLanguage === "ko"
                ? "카카오톡 브라우저에서는 이미지 저장이 제한됩니다.\n우측 상단 ⋮ 메뉴에서 '다른 브라우저로 열기'를 선택해 주세요."
                : "Image saving is limited in the KakaoTalk browser.\nOpen the ⋮ menu and choose 'Open in another browser'."
        );

        return;
    }

    if (canShareFile) {

        try {

            await navigator.share({
                files: [file],
                title:
                    t(
                        "ui.result.protectedImage",
                        "Security Lens Protected Image"
                    )
            });

            return;

        } catch (error) {

            /*
             * 사용자가 공유창을 직접 닫은 경우는
             * 오류로 취급하지 않는다.
             */
            if (
                error?.name === "AbortError"
            ) {
                return;
            }

            console.warn(
                "Web Share failed. Falling back to download.",
                error
            );
        }
    }


    /*
     * PC Chrome / 일반 브라우저 fallback
     */
    const link =
        document.createElement(
            "a"
        );


    link.href =
        protectedPreviewUrl;


    link.download =
        downloadName;


    document.body.appendChild(
        link
    );


    link.click();


    link.remove();
}

// ============================================================
// LOADING
// ============================================================

function startLoadingAnimation() {

    stopLoadingAnimation();


    const stages = [

        {
            message:
                t(
                    "ui.loading.prepare",
                    "Preparing your image securely..."
                ),

            progress:
                18
        },

        {
            message:
                t(
                    "ui.loading.personal",
                    "Detecting visible personal information..."
                ),

            progress:
                42
        },

        {
            message:
                t(
                    "ui.loading.metadata",
                    "Inspecting hidden metadata..."
                ),

            progress:
                65
        },

        {
            message:
                t(
                    "ui.loading.ai",
                    "Analyzing privacy exposure with AI..."
                ),

            progress:
                84
        },

        {
            message:
                t(
                    "ui.loading.risk",
                    "Calculating your privacy risk score..."
                ),

            progress:
                94
        }

    ];


    let index =
        0;


    updateLoadingStage(
        stages[index]
    );


    loadingInterval =
        setInterval(
            () => {

                if (
                    index <
                    stages.length - 1
                ) {

                    index++;


                    updateLoadingStage(
                        stages[index]
                    );
                }

            },
            1100
        );
}


function updateLoadingStage(stage) {

    loadingMessage.textContent =
        stage.message;


    loadingProgressBar.style.width =
        `${stage.progress}%`;


    loadingProgress.setAttribute(
        "aria-valuenow",
        String(stage.progress)
    );
}


function stopLoadingAnimation() {

    if (loadingInterval) {

        clearInterval(
            loadingInterval
        );


        loadingInterval =
            null;
    }
}


function completeLoadingAnimation() {

    loadingProgressBar.style.width =
        "100%";


    loadingProgress.setAttribute(
        "aria-valuenow",
        "100"
    );
}


// ============================================================
// RESET
// ============================================================

resetButton.addEventListener(
    "click",
    resetApplication
);


newAnalysisButton.addEventListener(
    "click",
    resetApplication
);


retryButton.addEventListener(
    "click",
    resetApplication
);


function resetApplication() {

    stopLoadingAnimation();

    stopRiskAnalyzingAnimation();

    analysisRequestId++;


    latestAnalysis =
        null;


    if (protectedPreviewUrl) {

        URL.revokeObjectURL(
            protectedPreviewUrl
        );


        protectedPreviewUrl = null;

    }

    protectedBlob = null;

    selectedFile =
        null;


    fileInput.value =
        "";


    clearAnalysisResult();


    if (previewUrl) {

        URL.revokeObjectURL(
            previewUrl
        );


        previewUrl =
            null;
    }


    previewImage.src =
        "";


    const previewAlt =
        t(
            "ui.preview.imageAlt",
            "Image selected for privacy analysis"
        );


    previewImage.alt =
        previewAlt;


    showOnly(
        uploadSection
    );


    window.scrollTo({
        top: 0,
        behavior: prefersReducedMotion()
            ? "auto"
            : "smooth"
    });


    requestAnimationFrame(
        () => {

            fileInput.focus();
        }
    );
}


function clearAnalysisResult() {

    detectedItems.innerHTML =
        "";


    metadataItems.innerHTML =
        "";


    overlayLayer.innerHTML =
        "";


    visibleCount.textContent =
        "0";


    hiddenCount.textContent =
        "0";


    riskScore.textContent =
        "0";


    riskLevel.textContent =
        "UNKNOWN";


    documentTypeCard.classList.add(
        "hidden"
    );

    documentTypeValue.textContent =
        "";


    aiContext.textContent =
        "";


    aiSummary.textContent =
        "";


    imageResolution.textContent =
        "";


    loadingProgressBar.style.width =
        "0%";


    loadingProgress.setAttribute(
        "aria-valuenow",
        "0"
    );

    protectButton.disabled =
        true;


    protectButton.setAttribute(
        "aria-disabled",
        "true"
    );


    if (protectButtonLabel) {

        protectButtonLabel.textContent =
            t(
                "ui.result.protect",
                "Protect Image"
            );
    }


    if (protectStatus) {

        protectStatus.textContent =
            t(
                "ui.result.readyAfterAnalysis",
                "Ready after analysis"
            );
    }
}


// ============================================================
// UI
// ============================================================

function showOnly(section) {

    [
        uploadSection,
        previewSection,
        loadingSection,
        resultSection,
        errorSection
    ]
        .forEach(item => {

            item.classList.add(
                "hidden"
            );
        });


    section.classList.remove(
        "hidden"
    );
}


function showError(message) {

    errorMessage.textContent =
        message;


    showOnly(
        errorSection
    );


    requestAnimationFrame(
        () => {

            errorSection.focus?.();
        }
    );
}


// ============================================================
// UTILITY
// ============================================================

function formatFileSize(bytes) {

    if (bytes < 1024) {

        return `${bytes} B`;
    }


    if (
        bytes <
        1024 * 1024
    ) {

        return `${(
            bytes / 1024
        ).toFixed(1)} KB`;
    }


    return `${(
        bytes /
        (1024 * 1024)
    ).toFixed(1)} MB`;
}


function formatPiiType(type) {

    if (!type) {
        return "UNKNOWN";
    }

    switch (String(type).toUpperCase()) {

        case "PHONE":
        case "PHONE_NUMBER":
            return t(
                "ui.pii.phone",
                "Phone Number"
            );

        case "EMAIL":
            return t(
                "ui.pii.email",
                "Email"
            );

        case "PERSON_NAME":
        case "NAME":
            return t(
                "ui.pii.personName",
                "Person Name"
            );

        case "ADDRESS":
            return t(
                "ui.pii.address",
                "Address"
            );

        case "RRN":
            return t(
                "ui.pii.rrn",
                "Resident Registration Number"
            );

        case "DRIVER_LICENSE_NUMBER":
            return t(
                "ui.pii.driverLicenseNumber",
                "Driver License Number"
            );

        case "PASSPORT_NUMBER":
            return t(
                "ui.pii.passportNumber",
                "Passport Number"
            );

        case "DATE_OF_BIRTH":
            return t(
                "ui.pii.dateOfBirth",
                "Date of Birth"
            );

        case "SENSITIVE_DOCUMENT":
            return t(
                "ui.pii.sensitiveDocument",
                "Sensitive Document"
            );

        default:
            return String(type)
                .replaceAll("_", " ");
    }
}

function formatDetectedValue(item) {

    if (!item) {
        return "";
    }


    const type =
        String(item.type || "")
            .toUpperCase();


    if (
        type === "SENSITIVE_DOCUMENT"
    ) {

        const documentType =
            String(
                item.maskedValue || ""
            ).toUpperCase();


        switch (documentType) {

            case "DRIVER_LICENSE":
                return t(
                    "ui.document.driverLicense"
                );

            case "PASSPORT":
                return t(
                    "ui.document.passport"
                );

            default:
                return t(
                    "ui.pii.sensitiveDocument"
                );
        }
    }


    const detectedLabel =
        t(
            "ui.pii.detected"
        );


    /*
     * 이름은 OCR 문자열이 일부 누락될 수 있으므로
     * 잘못된 부분 마스킹 값을 사용자에게 보여주지 않는다.
     */
    if (
        type === "PERSON_NAME"
    ) {

        return detectedLabel;
    }


    /*
     * 주소도 OCR/AI가 안전한 표시값을 만들지 못한 경우
     * 감지 사실만 표시한다.
     */
    if (
        type === "ADDRESS" &&
        (
            !item.maskedValue ||
            item.maskedValue === "ADDRESS_DETECTED" ||
            item.maskedValue === "***"
        )
    ) {

        return detectedLabel;
    }


    /*
     * 전화번호 / 이메일 / 정상적으로 마스킹된 주소 등은
     * 감지 상태 + 안전한 마스킹 값을 함께 표시한다.
     */
    if (
        item.maskedValue &&
        !item.maskedValue.endsWith("_DETECTED")
    ) {

        return `${detectedLabel} · ${item.maskedValue}`;
    }


    return detectedLabel;
}

function normalizeConfidence(value) {

    if (
        value === null ||
        value === undefined
    ) {

        return null;
    }


    const number =
        Number(value);


    if (!Number.isFinite(number)) {

        return null;
    }


    /*
        Backend가
        0.99 또는 99 어느 형태로 보내도 대응.
    */

    if (number <= 1) {

        return Math.round(
            number * 100
        );
    }


    return Math.min(
        100,
        Math.round(number)
    );
}


function formatCoordinates(
    latitude,
    longitude
) {

    const lat =
        Number(latitude);


    const lng =
        Number(longitude);


    if (
        !Number.isFinite(lat) ||
        !Number.isFinite(lng)
    ) {

        return t(
            "ui.metadata.locationDetected",
            "Location detected"
        );
    }


    return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
}


function formatCapturedAt(value) {

    if (!value) {
        return "";
    }


    const text =
        String(value)
            .trim()
            .replace(
                "T",
                " "
            );


    /*
     * EXIF:
     * 2025:08:06 10:09:13
     *
     * ISO 계열:
     * 2025-08-06 10:09:13
     */
    const match =
        text.match(
            /^(\d{4})[:-](\d{2})[:-](\d{2})\s+(\d{2}):(\d{2}):(\d{2})/
        );


    if (!match) {
        return text;
    }


    const [
        ,
        year,
        month,
        day,
        hour,
        minute,
        second
    ] = match;


    if (currentLanguage === "ko") {

        return `${year}년 ${Number(month)}월 ${Number(day)}일 ${hour}:${minute}:${second}`;
    }


    return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
}


function escapeHtml(value) {

    if (
        value === null ||
        value === undefined
    ) {

        return "";
    }


    return String(value)

        .replaceAll(
            "&",
            "&amp;"
        )

        .replaceAll(
            "<",
            "&lt;"
        )

        .replaceAll(
            ">",
            "&gt;"
        )

        .replaceAll(
            '"',
            "&quot;"
        )

        .replaceAll(
            "'",
            "&#039;"
        );
}


function prefersReducedMotion() {

    return window.matchMedia(
        "(prefers-reduced-motion: reduce)"
    ).matches;
}


// ============================================================
// INITIALIZE
// ============================================================

initializeI18n();


console.log(
    "Security Lens frontend loaded."
);