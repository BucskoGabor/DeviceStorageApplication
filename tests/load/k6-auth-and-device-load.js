import http from 'k6/http'
import { check, sleep } from 'k6'

/**
 * k6 Terhelésteszt: Authentication (Argon2id CPU terhelés) & Device query lapozással
 *
 * Futtatás:
 *   k6 run tests/load/k6-auth-and-device-load.js
 */

export const options = {
  stages: [
    { duration: '30s', target: 20 }, // 20 virtual users felfutás
    { duration: '1m', target: 50 },  // 50 virtual users tartós terhelés
    { duration: '20s', target: 0 },  // Leállás
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
    http_req_failed: ['rate<0.01'],    // Error rate must be below 1%
  },
}

const BASE_URL = __ENV.BACKEND_URL || 'http://localhost:8080'

export default function () {
  const loginPayload = JSON.stringify({
    email: 'admin@tanszek.local',
    password: 'ChangeMe123!',
  })

  const loginParams = {
    headers: { 'Content-Type': 'application/json' },
  }

  // 1. Login kérés
  const loginRes = http.post(`${BASE_URL}/api/auth/login`, loginPayload, loginParams)
  check(loginRes, {
    'login status is 200': (r) => r.status === 200,
    'has access token': (r) => JSON.parse(r.body).accessToken !== undefined,
  })

  if (loginRes.status === 200) {
    const token = JSON.parse(loginRes.body).accessToken
    const authParams = {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }

    // 2. Eszközök lekérdezése lapozással
    const devicesRes = http.get(`${BASE_URL}/api/devices?page=0&size=20`, authParams)
    check(devicesRes, {
      'devices status is 200': (r) => r.status === 200,
    })
  }

  sleep(1)
}
