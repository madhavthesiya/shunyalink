# API Reference

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/v1/url/shorten` | ✅ | Shorten a URL (supports custom alias, password, tags, auto-title) |
| `GET` | `/{shortId}` | — | Redirect (or OG preview for social bots) |
| `GET` | `/api/v1/url/stats/{shortId}` | — | Click count + timestamps (supports `?range=24h\|7d\|all`) |
| `GET` | `/api/v1/url/my-links` | ✅ | Paginated user links (supports `?search=` full-text) |
| `POST` | `/api/v1/url/bulk-delete` | ✅ | Bulk delete links |
| `POST` | `/api/v1/url/bulk-import` | ✅ | CSV bulk import (drag-and-drop) |
| `GET` | `/api/v1/url/export/csv` | ✅ | CSV export of all user links |
| `PUT` | `/api/v1/url/reorder` | ✅ | Drag-and-drop link reordering |
| `PUT` | `/api/v1/url/{shortId}/bio-visibility` | ✅ | Toggle show/hide on bio profile |
| `PATCH` | `/api/v1/url/{shortId}/metadata` | ✅ | Update link title and password |
| `PATCH` | `/api/v1/url/{shortId}/tags` | ✅ | Update link tags |
| `GET` | `/api/v1/url/{shortId}/reveal-password` | ✅ | On-demand password reveal (owner only) |
| `POST` | `/api/v1/url/resolve/{shortId}` | — | Verify password and resolve long URL |
| `GET` | `/api/v1/url/qr/{shortId}` | — | QR code image (PNG) |
| `GET` | `/api/v1/url/stats/public` | — | Public system stats (links, users, clicks) |
| `POST` | `/api/v1/auth/register` | — | Register |
| `POST` | `/api/v1/auth/login` | — | Login (JWT) |
| `POST` | `/api/v1/auth/google` | — | Google OAuth |
| `GET` | `/api/v1/profile/me` | ✅ | Get authenticated user's profile |
| `POST` | `/api/v1/profile/settings` | ✅ | Update bio-link profile settings |
| `POST` | `/api/v1/profile/picture` | ✅ | Upload profile picture (Cloudinary) |
| `GET` | `/api/v1/profile/username-check` | ✅ | Check username availability |
| `GET` | `/api/v1/profile/{username}` | — | Public bio-link page |
| `GET` | `/api/v1/portfolio/{username}` | — | Public CP Portfolio stats |
| `GET` | `/api/v1/portfolio/{username}/roast` | — | AI profile roast (Gemini/Groq) |

**Error codes:** `400` invalid input · `404` not found · `409` alias taken · `410` expired · `429` rate limited
