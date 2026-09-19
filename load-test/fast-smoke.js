import http from 'k6/http';
import { check } from 'k6';

const image = open('./sample.png', 'b');

export const options = {
    vus: 1,
    iterations: 1,
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

    const response = http.post(url, payload, {
        headers: {
            'Accept-Language': 'ko',
        },
    });

    check(response, {
        'status is 200': (res) => res.status === 200,
    });
}