"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ProfileCard } from "@/components/profile-card";
import { Loader2, UserX } from "lucide-react";
import { ThemeToggle } from "@/components/theme-toggle";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export default function PublicProfilePage() {
  const params = useParams();
  const rawUsername = params.username as string;
  // Remove the @ prefix if it exists (e.g., /@madhav -> madhav)
  const username = rawUsername.startsWith("%40") 
    ? rawUsername.slice(3) 
    : rawUsername.startsWith("@") ? rawUsername.slice(1) : rawUsername;

  const [profile, setProfile] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    async function fetchProfile() {
      try {
        const res = await fetch(`${API_URL}/api/v1/profile/${username}`, {
          cache: 'no-store'
        });
        if (!res.ok) throw new Error("Not found");
        const data = await res.json();
        setProfile(data);
      } catch (err) {
        setError(true);
      } finally {
        setLoading(false);
      }
    }
    fetchProfile();
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
        <p className="text-muted-foreground mt-2">The user @{username} hasn't claimed this page yet.</p>
      </div>
    );
  }

  return (
    <div className="bg-background min-h-screen relative">
      <div className="absolute top-4 right-4 z-10">
        <ThemeToggle />
      </div>
      <ProfileCard 
        profile={{
          displayName: profile.name || "User",
          username: profile.username,
          bio: profile.bioText || "",
          themeColor: profile.themeColor || "#6366f1",
          links: profile.links.map((link: any) => ({
            id: link.shortId,
            label: link.title || link.shortId, 
            href: `${API_URL}/${link.shortId}`
          }))
        }} 
      />
    </div>
  );
}
