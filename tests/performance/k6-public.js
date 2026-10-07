import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://luas-pet.devbyjose.org';

export const options = {
  stages: [
    { duration: '15s', target: 1 },
    { duration: '30s', target: 5 },
    { duration: '30s', target: 10 },
    { duration: '15s', target: 0 },
  ],

  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    http_req_duration: ['p(95)<3000'],
  },
};

export default function () {
  const home = http.get(`${BASE_URL}/`, {
    tags: { endpoint: 'home' },
  });

  check(home, {
    'home devuelve 200': (r) => r.status === 200,
  });

  const login = http.get(`${BASE_URL}/login`, {
    tags: { endpoint: 'login' },
  });

  check(login, {
    'login devuelve 200': (r) => r.status === 200,
  });

  const health = http.get(`${BASE_URL}/health`, {
    tags: { endpoint: 'health' },
  });

  check(health, {
    'health devuelve 200': (r) => r.status === 200,
    'health devuelve OK': (r) => r.body === 'OK',
  });

  sleep(1);
}
