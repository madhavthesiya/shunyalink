import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 Load Test: ShunyaLink High-Performance Redirects 🚀
// Goal: Measure TPS and Latency on the hot redirect path.

export const options = {
  stages: [
    { duration: '30s', target: 50 }, // Ramp up to 50 users
    { duration: '1m', target: 50 },  // Stay at 50 users (Steady state)
    { duration: '30s', target: 0 },  // Ramp down to 0 users
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests should be below 500ms
    http_req_failed: ['rate<0.01'],    // Error rate should be less than 1%
  },
};

export default function () {
  // 🏁 Test a real shortId (replace with a valid one from your DB)
  const url = 'http://localhost:8080/p/abcd123'; 
  
  const res = http.get(url, {
    redirects: 0, // We want to measure the REDIRECT latency, not the destination
  });

  check(res, {
    'is redirect (302)': (r) => r.status === 302,
    'has location header': (r) => r.headers['Location'] !== undefined,
  });

  sleep(0.1); // Small sleep to simulate real user behavior
}
