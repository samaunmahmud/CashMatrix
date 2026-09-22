// Browser side of passkeys. The server sends WebAuthn options as JSON with binary fields in
// base64url; the browser API wants ArrayBuffers, and its answer has to go back as JSON again.

const toBuffer = (base64url) => {
  const base64 = base64url.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(base64url.length / 4) * 4, "=");
  return Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)).buffer;
};

const toBase64url = (buffer) =>
  btoa(String.fromCharCode(...new Uint8Array(buffer))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

const withIds = (list) => (list ?? []).map((c) => ({ ...c, id: toBuffer(c.id) }));

export const passkeysSupported = () =>
  typeof window !== "undefined" && Boolean(window.PublicKeyCredential) && Boolean(navigator.credentials?.create);

/** Makes a new passkey on this device from the server's registration options. */
export async function createPasskey(options) {
  const publicKey = {
    ...options.publicKey,
    challenge: toBuffer(options.publicKey.challenge),
    user: { ...options.publicKey.user, id: toBuffer(options.publicKey.user.id) },
    excludeCredentials: withIds(options.publicKey.excludeCredentials),
  };
  const credential = await navigator.credentials.create({ publicKey });
  return {
    id: credential.id,
    rawId: toBase64url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: toBase64url(credential.response.clientDataJSON),
      attestationObject: toBase64url(credential.response.attestationObject),
      transports: credential.response.getTransports?.() ?? [],
    },
    clientExtensionResults: credential.getClientExtensionResults(),
  };
}

/** Signs the server's login challenge with a passkey the user picks on this device. */
export async function signInWithPasskey(options) {
  const publicKey = {
    ...options.publicKey,
    challenge: toBuffer(options.publicKey.challenge),
    allowCredentials: withIds(options.publicKey.allowCredentials),
  };
  const credential = await navigator.credentials.get({ publicKey });
  const { response } = credential;
  return {
    id: credential.id,
    rawId: toBase64url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: toBase64url(response.clientDataJSON),
      authenticatorData: toBase64url(response.authenticatorData),
      signature: toBase64url(response.signature),
      userHandle: response.userHandle ? toBase64url(response.userHandle) : null,
    },
    clientExtensionResults: credential.getClientExtensionResults(),
  };
}

/** True when the user closed the fingerprint / face prompt rather than something going wrong. */
export const wasCancelled = (err) => err?.name === "NotAllowedError" || err?.name === "AbortError";
