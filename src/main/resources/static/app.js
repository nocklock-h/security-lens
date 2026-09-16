// ============================================================
// Security Lens Frontend
// ============================================================

const API_URL = "/api/v1/images/analyze";
const UI_MESSAGE_URL = "/api/v1/ui/messages";


// ============================================================
// STATE
// ============================================================

let selectedFile = null;
let previewUrl = null;
let loadingInterval = null;

let messages = {};
let currentLanguage = "en";


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


const aiSummary =
    document.getElementById("aiSummary");


const imageResolution =
    document.getElementById("imageResolution");


const errorMessage =
    document.getElementById("errorMessage");


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

    const validMimeType =
        file.type?.startsWith("image/");


    const validExtension =
        /\.(jpe?g|png)$/i.test(
            file.name
        );


    if (!validMimeType && !validExtension) {

        showError(
            t(
                "ui.error.imageOnly",
                "Only image files can be uploaded."
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


    previewImage.alt =
        file.name
            ? `${file.name} - privacy analysis image`
            : "Image selected for privacy analysis";


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


    showOnly(
        loadingSection
    );


    startLoadingAnimation();


    const formData =
        new FormData();


    /*
        Spring Boot Controller:

        @RequestPart("image")
        MultipartFile image
    */
    formData.append(
        "image",
        selectedFile
    );


    try {

        const response =
            await fetch(
                API_URL,
                {
                    method: "POST",

                    headers: {
                        "Accept-Language":
                        currentLanguage
                    },

                    body: formData
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

                // JSON 오류 응답이 아니어도
                // 기본 오류 메시지를 사용한다.
            }


            throw new Error(
                message
            );
        }


        const data =
            await response.json();


        console.log(
            "Security Lens Analysis Result:",
            data
        );


        stopLoadingAnimation();


        renderAnalysis(
            data
        );


        /*
            먼저 hidden을 제거한 뒤 focus를 이동해야
            키보드 / Screen Reader에서 정상 동작한다.
        */
        showOnly(
            resultSection
        );


        requestAnimationFrame(
            () => {

                syncOverlayToImage();

                resultSection.focus();
            }
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


// ============================================================
// ANALYSIS RENDER
// ============================================================

function renderAnalysis(data) {

    renderRisk(
        data
    );


    renderVisibleExposure(
        data.detectedItems || []
    );


    renderMetadata(
        data.metadata
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
        level;


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
// VISIBLE EXPOSURE
// ============================================================

function renderVisibleExposure(items) {

    detectedItems.innerHTML =
        "";


    visibleCount.textContent =
        items.length;


    if (items.length === 0) {

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


    items.forEach(item => {

        const element =
            document.createElement(
                "div"
            );


        element.className =
            "detected-item";


        const confidence =
            normalizeConfidence(
                item.confidence
            );


        const type =
            formatPiiType(
                item.type
            );


        const value =
            item.maskedValue ||
            item.value ||
            "Detected";


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

                ${
                confidence !== null
                    ? `
                            <div class="confidence">
                                ${confidence}%
                            </div>
                        `
                    : ""
            }
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


        appendMetadataItem(
            "GPS LOCATION",
            formatCoordinates(
                metadata.gps.latitude,
                metadata.gps.longitude
            )
        );
    }


    // CAPTURE TIME
    if (metadata.capturedAt) {

        count++;


        appendMetadataItem(
            "CAPTURED AT",
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
            "DEVICE",
            deviceName ||
            "Device information detected"
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
            syncOverlayToImage
        );
    }
);


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


    previewImage.alt =
        "Image selected for privacy analysis";


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


    return String(type)
        .replaceAll(
            "_",
            " "
        );
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

        return "Location detected";
    }


    return `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
}


function formatCapturedAt(value) {

    if (!value) {

        return "";
    }


    /*
        EXIF 시간이 ISO가 아닐 수 있으므로
        강제로 Date 객체로 변환하지 않는다.
    */

    return String(value)
        .replace(
            "T",
            " "
        );
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