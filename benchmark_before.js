import http from 'k6/http';
import { check, sleep } from 'k6';

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
  const res = http.get(`http://localhost/${id}`, { redirects: 0 });

  check(res, {
    'is redirect': (r) => r.status === 302 || r.status === 301,
  });

  sleep(0.05);
}
