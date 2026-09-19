import http from 'k6/http';
import { check } from 'k6';

const image = open('./sample.png', 'b');

const vus = Number(__ENV.VUS || 10);
const iterations = Number(__ENV.ITERATIONS || 1);

export const options = {
    scenarios: {
        fast_load: {
            executor: 'per-vu-iterations',
            vus: vus,
            iterations: iterations,
            maxDuration: '2m',
        },
    },

    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<10000'],
    },
};

export default function () {

    const url =
        'https://securitylens.up.railway.app/api/v1/images/analyze/fast';

    const payload = {
        image: http.file(
            image,
            'sample.png',
            'image/png'
        ),
    };

    const response = http.post(
        url,
        payload,
        {
            headers: {
                'Accept-Language': 'ko',
            },
            timeout: '60s',
        }
    );

    check(response, {
        'status is 200': (res) => res.status === 200,
    });
}