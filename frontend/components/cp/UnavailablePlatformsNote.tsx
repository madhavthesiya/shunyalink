const PLATFORM_NAMES: Record<string, string> = {
  leetcode: "LeetCode",
  codeforces: "Codeforces",
  codechef: "CodeChef",
  atcoder: "AtCoder",
  github: "GitHub",
}

/**
 * The portfolio API lists platforms that failed or missed their deadline in `unavailablePlatforms`.
 * Say so, instead of silently showing fewer widgets and a smaller total.
 */
export function UnavailablePlatformsNote({ platforms }: { platforms?: unknown }) {
  if (!Array.isArray(platforms)) return null
  const names = platforms
    .filter((platform): platform is string => typeof platform === "string")
    .map((platform) => PLATFORM_NAMES[platform] ?? platform)
  if (names.length === 0) return null

  return (
    <p role="status" className="mt-4 text-center text-xs text-foreground/50">
      Couldn&apos;t load {names.join(", ")} right now, so the total may be incomplete. Refresh in a minute to try again.
    </p>
  )
}
