/** The message to show for a failed API call: the server's own words when it gives some. */
export function errorMessage(err, fallback = "Couldn't save. Please try again.") {
  const data = err.response?.data;
  if (data?.error) return data.error;
  if (data && typeof data === "object") return Object.values(data).join(" ");
  return fallback;
}
