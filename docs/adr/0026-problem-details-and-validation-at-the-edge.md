# ADR 0026: Problem Details for errors (without breaking clients), and validation at the edge

- **Status:** Accepted (roadmap item 3.8a)
- **Code:** `GlobalExceptionHandler`, `BioVisibilityRequest`, `UpdateMetadataRequest`, `UpdateTagsRequest`, `LinkTags`,
  `ReorderRequest`, `ShortenRequest`, `UrlController`, `CsvImportService`, `GlobalExceptionHandlerTest`, `UrlControllerTest`

## Context

- **Errors had a custom shape** (`timestamp`, `status`, `message`, `requestId`). RFC 9457 "Problem Details" is the
  standard that Spring supports natively and that clients and tools understand.
- **Some request bodies were untyped maps**, checked by hand or not at all. A title edit had no length limit; tags had
  none anywhere, so one request could attach thousands of tags, or a tag longer than the 255-character column, which
  failed inside the database as a misleading "conflict". Bulk delete accepted an unbounded list.
- **Every unique-constraint conflict** returned the same "This conflicts with existing data", even when the cause was
  as simple as a taken username.

## Decision

- **Problem Details, additively.** Errors are `application/problem+json` with `type`, `title`, `status`, `detail` and
  `instance`, *plus* the old `message`, `timestamp` and `requestId`. The frontend reads `message` and never checks the
  content type, so it keeps working unchanged. Removing the old fields later is a separate, coordinated change.
- **Validate at the edge.** Every request body is a typed record or class with Bean Validation, so a bad request is
  rejected with a 400 before any service code runs. Limits match the rest of the app: title ≤ 100, password ≤ 20,
  at most 20 tags of at most 50 characters (create, edit and CSV import), bulk delete ≤ 200, reorder ≤ 1,000. CSV rows
  bypass request validation, so the import checks the same tag limits itself.
- **Say what conflicts** for the unique constraints a user can hit (username, email, alias), by constraint name.
  Values are never echoed, and an unknown constraint keeps the generic message.

Not done: `spring.mvc.problemdetails.enabled=true`. It adds Spring's own handler for framework exceptions, which would
compete with ours; our handler already returns Problem Details for everything.

## Consequences

- (+) A standard error format, and no client had to change.
- (+) Bad input stops at the controller with a clear message instead of reaching the database.
- (−) Responses are slightly larger while both the old and the new fields are present.
- (−) Limits are product rules too: 20 tags per link is a choice, easy to change in `LinkTags`.
