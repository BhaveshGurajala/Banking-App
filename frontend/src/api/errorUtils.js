export function extractErrorMessage(err, fallback) {
  return err.response?.data?.message || err.response?.data?.error || fallback;
}