import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// The harness creates this file inside a disposable container; it never contains production credentials.
const fixture = JSON.parse(open('/work/fixture.json'));
const base = __ENV.BASE_URL;
if (!/^http:\/\/host\.testcontainers\.internal:\d+$/.test(base || '')) {
  throw new Error('Only the isolated Testcontainers host alias is permitted');
}
const profile = __ENV.PROFILE || 'normal';
const vus = Number(__ENV.VUS || '20');
if (!['normal', 'sustained', 'spike', 'academic-sustained', 'academic-spike'].includes(profile)) throw new Error('Invalid profile');
if (profile === 'normal' && ![20, 50, 100].includes(vus)) throw new Error('Normal VUs: 20, 50, 100');
if (profile === 'sustained' && ![100, 200].includes(vus)) throw new Error('Sustained VUs: 100, 200');
if (profile === 'academic-sustained' && ![100, 125, 150, 175, 200].includes(vus)) throw new Error('Academic sustained VUs: 100, 125, 150, 175, 200');
if (profile === 'academic-spike' && vus !== 500) throw new Error('Academic spike requires 500 VUs');
const accepted = new Counter('invoices_accepted');
const limited = new Counter('requests_limited');
const unavailable = new Counter('requests_unavailable');
const unknown = new Counter('invoice_response_unknown');
const admissionRejected = new Counter('invoice_rejected_before_commit');
const integrity = new Rate('idempotency_intact');
// Track individual synchronous endpoint latencies independently of the mixed HTTP trend.
const createDuration = new Trend('invoice_create_duration_ms', true);
const replayDuration = new Trend('invoice_replay_duration_ms', true);
const readDuration = new Trend('document_read_duration_ms', true);
const scenarios = profile === 'academic-sustained' ? {
  traffic: { executor: 'ramping-vus', startVUs: 0, stages: [
    { duration: '2m', target: vus },
    { duration: __ENV.DURATION || '5m', target: vus },
    { duration: '1m', target: 0 },
  ], gracefulRampDown: '15s', gracefulStop: '30s' },
} : profile === 'academic-spike' ? {
  traffic: { executor: 'ramping-vus', startVUs: 50, stages: [
    { duration: '30s', target: 50 },
    { duration: '1s', target: 500 },
    { duration: __ENV.DURATION || '1m', target: 500 },
    { duration: '1s', target: 0 },
    { duration: '30s', target: 0 },
  ], gracefulRampDown: '15s', gracefulStop: '30s' },
} : profile === 'spike' ? {
  traffic: { executor: 'ramping-vus', startVUs: 20, stages: [
    { duration: '20s', target: 20 }, { duration: '10s', target: 500 },
    { duration: __ENV.DURATION || '30s', target: 500 },
    { duration: '10s', target: 20 }, { duration: '30s', target: 20 },
  ], gracefulRampDown: '15s', gracefulStop: '30s' },
} : {
  traffic: { executor: 'constant-vus', vus, duration: __ENV.DURATION || (profile === 'sustained' ? '10m' : '30s'), gracefulStop: '30s' },
};
export const options = {
  scenarios,
  maxRedirects: 0,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    checks: ['rate==1'], idempotency_intact: ['rate==1'],
    http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<2000', 'p(99)<5000'],
  },
  // Never tag URLs containing per-document IDs, bodies, credentials or tenant IDs.
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
};

export function setup() {
  const response = http.post(`${base}/api/v1/auth/token`, JSON.stringify({
    clientId: fixture.clientId, clientSecret: fixture.clientSecret,
  }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'authentication' } });
  if (response.status !== 200) throw new Error(`Synthetic authentication failed: HTTP ${response.status}`);
  return { token: response.json('accessToken') };
}

export default function (data: { token: string }) {
  const key = `load-${fixture.runId}-${__VU}-${__ITER}`;
  const params = { headers: { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json',
    'Idempotency-Key': key }, tags: { name: 'invoice-admission' }, timeout: '10s' };
  const body = JSON.stringify({ ...fixture.invoice, issuedAt: new Date().toISOString() });
  const response = http.post(`${base}/api/v1/invoices`, body, params);
  createDuration.add(response.timings.duration);
  let rejectedBeforeCommit = false;
  if (response.status === 503) {
    try { rejectedBeforeCommit = response.json('code') === 'ADMISSION_SATURATED'; } catch { /* Other 503 remains unknown. */ }
  }
  if (rejectedBeforeCommit) admissionRejected.add(1);
  if (response.status === 0 || (response.status >= 500 && !rejectedBeforeCommit)) unknown.add(1);
  if (response.status === 429) { limited.add(1); sleep(1); return; }
  if (response.status === 503) { unavailable.add(1); sleep(1); return; }
  if (!check(response, { accepted: (r) => r.status === 202 })) return;
  accepted.add(1);
  const id = response.json('id');
  // Replay the identical key/body: no new document, access key, sequential or outbox event.
  const replay = http.post(`${base}/api/v1/invoices`, body, params);
  replayDuration.add(replay.timings.duration);
  const same = replay.status === 200 && replay.json('id') === id;
  integrity.add(same);
  check(replay, { 'same document on replay': () => same });
  const read = http.get(`${base}/api/v1/documents/${id}`, {
    headers: { Authorization: `Bearer ${data.token}` }, tags: { name: 'document-status' }, timeout: '10s',
  });
  readDuration.add(read.timings.duration);
  check(read, { 'durable document readable': (r) => r.status === 200 });
  sleep(0.2);
}
