"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ProfileCard } from "@/components/profile-card";
import { Loader2, UserX, User } from "lucide-react";
import { ThemeToggle } from "@/components/theme-toggle";
import Link from "next/link";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export default function CpProfilePage() {
  const params = useParams();
  const rawUsername = params.username as string;
  const username = rawUsername.startsWith("%40")
    ? rawUsername.slice(3)
    : rawUsername.startsWith("@") ? rawUsername.slice(1) : rawUsername;

  const [profile, setProfile] = useState<any>(null);
  const [portfolio, setPortfolio] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    async function fetchData() {
      try {
        const [profileRes, portfolioRes] = await Promise.all([
          fetch(`${API_URL}/api/v1/profile/${username}`, { cache: "no-store" }),
          fetch(`${API_URL}/api/v1/portfolio/${username}`, { cache: "no-store" }),
        ]);
        if (!profileRes.ok) throw new Error("Profile not found");
        const profileData = await profileRes.json();
        setProfile(profileData);
        if (portfolioRes.ok) {
          const portfolioData = await portfolioRes.json();
          setPortfolio(portfolioData);
        }
      } catch {
        setError(true);
      } finally {
        setLoading(false);
      }
    }
    fetchData();
  }, [username]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Loader2 className="w-10 h-10 animate-spin text-primary" />
      </div>
    );
  }

  if (error || !profile) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center text-center p-4">
        <UserX className="w-16 h-16 text-muted-foreground mb-4" />
        <h1 className="text-2xl font-bold">Profile Not Found</h1>
        <p className="text-muted-foreground mt-2">
          The user @{username} hasn&apos;t claimed this page yet.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-background min-h-screen relative">
      <div className="absolute top-4 right-4 z-50">
        <ThemeToggle />
      </div>
      <ProfileCard
        profile={{
          displayName: profile.name || "User",
          username: profile.username,
          bio: profile.bioText || "",
          themeColor: profile.themeColor || "#6366f1",
          profileType: "PROGRAMMER",
          profilePictureUrl: profile.profilePictureUrl || null,
          portfolio: portfolio,
          links: profile.links.map((link: any) => ({
            id: link.shortId,
            label: link.title || link.shortId,
            href: `${API_URL}/${link.shortId}`,
            category: link.category,
          })),
          leetcodeUsername: profile.leetcodeUsername,
          codeforcesUsername: profile.codeforcesUsername,
          codeChefHandle: profile.codeChefHandle,
          atCoderHandle: profile.atCoderHandle,
          githubUsername: profile.githubUsername,
        }}
      />
      <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-50">
        <Link
          href={`/@${profile.username}`}
          className="flex items-center gap-2 px-4 py-2 rounded-full bg-foreground/90 text-background text-xs font-bold shadow-lg backdrop-blur-sm hover:bg-foreground transition-all hover:scale-105"
        >
          <User className="w-3.5 h-3.5" />
          Back to Bio
        </Link>
      </div>
    </div>
  );
}
