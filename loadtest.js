import http from 'k6/http';
import { check } from 'k6';

// This is the production-level load configuration
export const options = {
  stages: [
    { duration: '5s', target: 50 },  // Ramp up to 50 concurrent users over 5 seconds
    { duration: '20s', target: 100 }, // Sustain 100 concurrent users for 20 seconds
    { duration: '5s', target: 0 },   // Ramp down to 0 users over 5 seconds
  ],
  thresholds: {
    // We want 95% of requests to complete in under 50ms
    http_req_duration: ['p(95)<50'],
    // We want the error rate to be less than 1%
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  // REPLACE "mytestlink" with the actual short ID you created in Step 2!
  const res = http.get('http://localhost:8080/f', {
    redirects: 0 // We don't want k6 to follow the redirect to Google. We just want to measure your server's response time to issue the 302 status.
  });

  // Verify your server responded with a 302 Found (Redirect)
  check(res, {
    'status is 302 (redirect)': (r) => r.status === 302,
  });
}
