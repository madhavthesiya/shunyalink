# ADR 0001: Link passwords are encrypted with AES-256-GCM, not hashed

- **Status:** Accepted (2026-11-02)
- **Code:** `LinkPasswordCipher`, `LinkPasswordBackfill`, `DbUrlService#resolveProtectedLink` / `#revealLinkPassword`

## Context

A password-protected short link asks viewers for a password before redirecting. That password is
not an account credential: the owner chooses it in order to **give it to other people**, often many
of them, over weeks. Owners forget it and need to share it again; if they could only reset it,
everyone who already has the old one would be locked out.

The original implementation supported this with an owner-only "reveal", but stored values with
`AES/ECB/PKCS5Padding` under a space-padded string key:

- ECB is deterministic: equal passwords give equal ciphertext, so the database shows which links share a password.
- No integrity: modified ciphertext is not detected.
- The key was a human-chosen string, not 32 random bytes.

(Separately, several endpoints leaked destinations or passwords outright; those were fixed independently of storage.)

## Options

| Option | For | Against |
|---|---|---|
| Keep AES/ECB | No migration | All three weaknesses above |
| **BCrypt hash** (briefly implemented on the branch, then reverted) | Nothing recoverable even with full server compromise | No reveal: forgetting the code means resetting it and re-sharing with everyone. The destination URL is stored in plaintext anyway, so a DB leak already exposes what the password protects; the extra protection is mainly against owners reusing personal passwords |
| **AES-256-GCM, link-bound, versioned** | Reveal stays possible; random IV hides equal values; tag detects tampering | Anyone holding both the DB and the key can read codes, so the key must be managed and backed up |

## Decision

AES-256-GCM via `LinkPasswordCipher`:

- New random 12-byte IV per value; 128-bit authentication tag.
- The link's short ID is **associated data**: a ciphertext copied onto another link does not decrypt.
- Stored as `v1:base64(iv || ciphertext+tag)`; the prefix allows key rotation (add `v2`, re-encrypt).
- Key `LINK_PASSWORD_KEY` = 32 random bytes, separate from the legacy `ENCRYPTION_SECRET`;
  startup fails if it is missing or not exactly 32 bytes.

Supporting measures for the remaining risk (DB + key compromise, password reuse):

- Reveal is owner-only (ownership is part of the query), rate-limited to 20/hour per user and audit-logged.
- The UI generates random access codes and warns against reusing a personal password.
- Unlock attempts are rate-limited and compared in constant time.

Migration: `LinkPasswordBackfill` re-encrypts every legacy ECB value at startup. It selects only
non-`v1` rows, so it is idempotent, safe to run on several replicas at once, and never blocks
startup. Values that were hashed with BCrypt by an older version can still be verified but not revealed.

## Consequences

- (+) Owners can always recover and re-share a code; viewers are never locked out by a reset.
- (+) Tampering and ciphertext swapping are detected instead of silently accepted.
- (−) `LINK_PASSWORD_KEY` is now a critical secret: losing it makes every link password unreadable.
- (−) A leak of the database **and** the key exposes the codes. Next step: keep the key in a managed
  key store (e.g. Azure Key Vault) instead of an app setting.
- Follow-up: once production logs `converted=0`, delete `EncryptionUtils` and `ENCRYPTION_SECRET`.

## Lesson

The first fix (BCrypt) treated "reversible" as the bug. The actual bug was the *mode and key*.
Classifying the data first (credential vs. shared access code) leads to the right primitive.
