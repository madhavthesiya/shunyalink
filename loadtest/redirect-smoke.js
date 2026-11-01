import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 load test: single-link redirect hot path.
// Defaults to the local Nginx entry point (3 backend replicas). Point it elsewhere
// explicitly, e.g. `k6 run -e BASE_URL=https://sl.madhavv.me -e SHORT_ID=ce redirect-smoke.js`.
const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const SHORT_ID = __ENV.SHORT_ID || 'ce';

export const options = {
  stages: [
    { duration: '15s', target: 20 },  // Warm up
    { duration: '30s', target: 50 },  // Ramp to 50 concurrent users
    { duration: '1m',  target: 50 },  // Steady state at 50 users
    { duration: '15s', target: 0 },   // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95% of requests under 1s
    http_req_failed: ['rate<0.01'],    // Less than 1% error rate
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/${SHORT_ID}`, {
    redirects: 0, // Measure REDIRECT latency only, don't follow
  });

  check(res, {
    'is redirect (302)': (r) => r.status === 302,
    'has location header': (r) => r.headers['Location'] !== undefined,
  });

  sleep(0.1);
}
