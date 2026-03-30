import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 Load Test: ShunyaLink Local Cluster Benchmark 🚀
// Tests the redirect hot-path against the local 3-node Docker cluster.

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
  // Hit the LOCAL Nginx load balancer (port 80) → 3 backend replicas
  // Replace 'ce' with any valid shortId from your local database
  const res = http.get('https://sl.madhavv.me/ce', {
    redirects: 0, // Measure REDIRECT latency only, don't follow
  });

  check(res, {
    'is redirect (302)': (r) => r.status === 302,
    'has location header': (r) => r.headers['Location'] !== undefined,
  });

  sleep(0.1);
}
