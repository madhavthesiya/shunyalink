import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 load test: redirects spread across the 20 most-clicked links.
// Defaults to the local Nginx entry point; override with `-e BASE_URL=...`.
const BASE_URL = __ENV.BASE_URL || 'http://localhost';

// Top 20 short IDs by click count
const SHORT_IDS = [
  'f', 'p', 'b', 'r', 'l', 'B', 'V', 'd', 't', 'v',
  'x', 'wiki-test', 'T', 'z', '3', 'D', '1', 'Z', 'git-test', 'n',
];

export const options = {
  stages: [
    { duration: '10s', target: 20 },   // Warm up
    { duration: '30s', target: 50 },   // Ramp to 50 users
    { duration: '1m',  target: 50 },   // Steady state
    { duration: '10s', target: 0 },    // Cool down
  ],
};

export default function () {
  const id = SHORT_IDS[Math.floor(Math.random() * SHORT_IDS.length)];
  const res = http.get(`${BASE_URL}/${id}`, { redirects: 0 });

  check(res, {
    'is redirect': (r) => r.status === 302 || r.status === 301,
  });

  sleep(0.05);
}
