// No 0/O or 1/l/I: codes are often read aloud or retyped by the people they're shared with.
const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

/**
 * Random access code for a password-protected link, from the browser's CSPRNG. A generated code
 * (instead of a typed one) means owners never reuse a personal password as a link password.
 */
export function generateAccessCode(length = 10): string {
  // Bytes at or above this limit are discarded so every character is equally likely
  const limit = 256 - (256 % ALPHABET.length)
  let code = ""
  while (code.length < length) {
    for (const byte of crypto.getRandomValues(new Uint8Array(length * 2))) {
      if (byte < limit && code.length < length) {
        code += ALPHABET[byte % ALPHABET.length]
      }
    }
  }
  return code
}
