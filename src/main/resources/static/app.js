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

let latestAnalysis = null;
let protectedPreviewUrl = null;

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

        latestAnalysis =
            data;


        const protectableItems =
            Array.isArray(data.detectedItems)
                ? data.detectedItems.filter(item => {

                    if (isDocumentType(item)) {
                        return false;
                    }


                    const box =
                        item.boundingBox;


                    return (
                        box &&
                        box.width > 0 &&
                        box.height > 0
                    );
                })
                : [];


        const canProtect =
            protectableItems.length > 0;


        protectButton.disabled =
            !canProtect;


        protectButton.setAttribute(
            "aria-disabled",
            String(!canProtect)
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

                renderBoundingBoxes(
                    data.detectedItems || [],
                    data.image
                );

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
    const isKorean =
        currentLanguage === "ko";


    documentTypeTitle.textContent =
        isKorean
            ? "문서 유형"
            : "Document Type";


    documentTypeHeading.textContent =
        isKorean
            ? "문서 유형"
            : "Document Type";


    documentTypeDescription.textContent =
        isKorean
            ? "이미지 전체 맥락에서 인식된 문서입니다."
            : "Document context recognized from the image.";


    documentTypeLabel.textContent =
        isKorean
            ? "인식된 문서"
            : "Recognized document";


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
            currentLanguage === "ko"
                ? "위치 메타데이터"
                : "Location Metadata"
        );
    }


    if (
        metadata?.device?.detected
    ) {

        signals.push(
            currentLanguage === "ko"
                ? "기기 정보"
                : "Device Information"
        );
    }


    if (metadata?.capturedAt) {

        signals.push(
            currentLanguage === "ko"
                ? "촬영 시간"
                : "Capture Time"
        );
    }


    if (signals.length === 0) {

        aiContext.textContent =
            currentLanguage === "ko"
                ? "뚜렷한 개인정보 노출 신호가 발견되지 않았습니다."
                : "No clear privacy exposure signals were detected.";

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


    if (selectedItemIds.size === 0) {

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

function downloadProtectedImage() {

    if (!protectedPreviewUrl) {
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


    const link =
        document.createElement(
            "a"
        );


    link.href =
        protectedPreviewUrl;


    link.download =
        `${originalName}-protected.${extension}`;


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


    latestAnalysis =
        null;


    if (protectedPreviewUrl) {

        URL.revokeObjectURL(
            protectedPreviewUrl
        );


        protectedPreviewUrl =
            null;
    }


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
                "ui.result.readyToProtect",
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
        type === "SENSITIVE_DOCUMENT" &&
        item.maskedValue === "DRIVER_LICENSE"
    ) {

        return t(
            "ui.document.driverLicense"
        );
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