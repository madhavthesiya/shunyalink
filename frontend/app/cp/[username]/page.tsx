import type { Metadata } from "next";
import CpProfilePage from "./_client";

const SERVER_API = process.env.INTERNAL_API_URL || process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
const SITE_URL = "https://shunyalink.madhavv.me";

type Props = { params: Promise<{ username: string }> };

async function fetchProfile(username: string) {
  try {
    const res = await fetch(`${SERVER_API}/api/v1/profile/${username}`, {
      cache: "no-store",
    });
    if (!res.ok) return null;
    return res.json();
  } catch {
    return null;
  }
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { username } = await params;
  const cleanUsername = username.startsWith("%40")
    ? username.slice(3)
    : username.startsWith("@") ? username.slice(1) : username;

  const profile = await fetchProfile(cleanUsername);

  if (!profile) {
    return {
      title: "CP Profile Not Found | ShunyaLink",
      description: "This competitive programming profile doesn't exist. Build your own on ShunyaLink!",
    };
  }

  const displayName = profile.name || cleanUsername;

  // Dynamically describe CP platforms the user has connected
  const platforms: string[] = [];
  if (profile.leetcodeUsername) platforms.push("LeetCode");
  if (profile.codeforcesUsername) platforms.push("Codeforces");
  if (profile.codeChefHandle) platforms.push("CodeChef");
  if (profile.atCoderHandle) platforms.push("AtCoder");
  if (profile.githubUsername) platforms.push("GitHub");

  const platformStr = platforms.length > 0
    ? `Solves on ${platforms.join(", ")}. `
    : "";

  const description = `${platformStr}Check out ${displayName}'s CP profile on ShunyaLink. Build your own for free!`;
  const ogImage = profile.profilePictureUrl || `${SITE_URL}/logo-social.png`;

  return {
    title: `${displayName} — CP Profile | ShunyaLink`,
    description,
    openGraph: {
      title: `${displayName} — CP Profile | ShunyaLink`,
      description,
      url: `${SITE_URL}/cp/@${cleanUsername}`,
      siteName: "ShunyaLink",
      images: [
        {
          url: ogImage,
          width: 400,
          height: 400,
          alt: `${displayName}'s competitive programming profile`,
        },
      ],
      type: "profile",
    },
    twitter: {
      card: "summary",
      title: `${displayName} — CP Profile | ShunyaLink`,
      description,
      images: [ogImage],
    },
  };
}

export default function Page() {
  return <CpProfilePage />;
}
