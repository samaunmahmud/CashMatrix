import { settingsApi } from "./api";

// Browser side of push notifications: ask permission, subscribe this device, and tell the server about it.

export const pushSupported = () =>
  typeof window !== "undefined" &&
  "serviceWorker" in navigator &&
  "PushManager" in window &&
  "Notification" in window;

// The browser wants the server's public key as bytes, but it is sent as URL-safe base64 text.
function keyToBytes(base64Url) {
  const padded = base64Url + "=".repeat((4 - (base64Url.length % 4)) % 4);
  const raw = atob(padded.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

const registration = () => navigator.serviceWorker.register("/sw.js");

/** The subscription this browser already has, or null. Never asks for permission. */
export async function currentPushSubscription() {
  if (!pushSupported()) return null;
  const reg = await navigator.serviceWorker.getRegistration("/sw.js");
  return reg ? reg.pushManager.getSubscription() : null;
}

export async function enablePush(publicKey) {
  const permission = await Notification.requestPermission();
  if (permission !== "granted") {
    throw new Error("Notifications are blocked for this site. Allow them in your browser settings and try again.");
  }
  await registration();
  const reg = await navigator.serviceWorker.ready;
  const subscription =
    (await reg.pushManager.getSubscription()) ||
    (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyToBytes(publicKey) }));
  await settingsApi.subscribePush(subscription.toJSON());
}

export async function disablePush() {
  const subscription = await currentPushSubscription();
  if (!subscription) return;
  await settingsApi.unsubscribePush(subscription.endpoint);
  await subscription.unsubscribe();
}
