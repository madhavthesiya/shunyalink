import type { Metadata } from "next";
import PublicProfilePage from "./_client";

// Server-side API URL — uses nginx service name inside Docker
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
      title: "Profile Not Found | ShunyaLink",
      description: "This profile doesn't exist yet. Build your own free bio-link profile on ShunyaLink!",
    };
  }

  const displayName = profile.name || cleanUsername;
  const bio = profile.bioText
    ? `${profile.bioText} • Build your own free profile on ShunyaLink!`
    : `Check out ${displayName}'s bio-link profile. Build your own for free on ShunyaLink!`;

  // Use user's uploaded profile picture if available, else fall back to ShunyaLink banner
  const ogImage = profile.profilePictureUrl || `${SITE_URL}/logo-social.png`;

  return {
    title: `${displayName} | ShunyaLink`,
    description: bio,
    openGraph: {
      title: `${displayName} | ShunyaLink`,
      description: bio,
      url: `${SITE_URL}/@${cleanUsername}`,
      siteName: "ShunyaLink",
      images: [
        {
          url: ogImage,
          width: 400,
          height: 400,
          alt: `${displayName}'s profile picture`,
        },
      ],
      type: "profile",
    },
    twitter: {
      card: "summary",
      title: `${displayName} | ShunyaLink`,
      description: bio,
      images: [ogImage],
    },
  };
}

export default function Page() {
  return <PublicProfilePage />;
}
