import type { AlertPayload } from './types';

/**
 * Native ships `data` as a JSON string: an Android Bundle cannot carry an
 * arbitrary nested object, and re-encoding one field is far cheaper than a
 * bespoke converter on both platforms.
 */
export function normalizeAlert(raw: unknown): AlertPayload | null {
  if (!raw || typeof raw !== 'object') return null;
  const { dataJson, ...rest } = raw as AlertPayload & { dataJson?: string };
  let data: Record<string, unknown> | undefined;
  if (typeof dataJson === 'string' && dataJson.length > 0) {
    try {
      data = JSON.parse(dataJson) as Record<string, unknown>;
    } catch {
      data = undefined;
    }
  }
  const payload = rest as AlertPayload;
  return { ...payload, data: data ?? payload.data };
}
