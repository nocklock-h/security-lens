import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const image = open('./sample.png', 'b');

const fastDuration = new Trend('fast_duration');
const deepDuration = new Trend('deep_duration');

const vus = Number(__ENV.VUS || 1);
const iterations = Number(__ENV.ITERATIONS || 1);

export const options = {
    scenarios: {
        full_flow: {
            executor: 'per-vu-iterations',
            vus: vus,
            iterations: iterations,
            maxDuration: '3m',
        },
    },
};

function createPayload() {
    return {
        image: http.file(
            image,
            'sample.png',
            'image/png'
        ),
    };
}

export default function () {

    const baseUrl =
        'https://securitylens.up.railway.app';

    // 1. FAST
    const fastResponse = http.post(
        `${baseUrl}/api/v1/images/analyze/fast`,
        createPayload(),
        {
            headers: {
                'Accept-Language': 'ko',
            },
            timeout: '60s',
        }
    );

    fastDuration.add(
        fastResponse.timings.duration
    );

    check(fastResponse, {
        'FAST status is 200':
            (res) => res.status === 200,
    });


    // 2. DEEP
    const deepResponse = http.post(
        `${baseUrl}/api/v1/images/analyze/deep`,
        createPayload(),
        {
            headers: {
                'Accept-Language': 'ko',
            },
            timeout: '120s',
        }
    );

    deepDuration.add(
        deepResponse.timings.duration
    );

    check(deepResponse, {
        'DEEP status is 200':
            (res) => res.status === 200,
    });
}