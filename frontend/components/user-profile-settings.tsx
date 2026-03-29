"use client"

import { useState, useEffect } from "react"
import { 
  User, 
  AtSign, 
  Check, 
  X, 
  Loader2, 
  Smartphone,
  Palette,
  Type,
  Sparkles,
  Link,
  Copy,
  ExternalLink
} from "lucide-react"
import { toast } from "sonner"
import { motion, AnimatePresence } from "framer-motion"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

const PRESET_COLORS = [
  { name: "Indigo", value: "#6366f1" },
  { name: "Rose", value: "#f43f5e" },
  { name: "Emerald", value: "#10b981" },
  { name: "Amber", value: "#f59e0b" },
  { name: "Cyan", value: "#06b6d4" },
  { name: "Violet", value: "#8b5cf6" },
]

type AvailabilityStatus = "idle" | "checking" | "available" | "taken"

export function UserProfileSettings() {
  const [username, setUsername] = useState("")
  const [bio, setBio] = useState("")
  const [themeColor, setThemeColor] = useState(PRESET_COLORS[0].value)
  const [customHex, setCustomHex] = useState("")
  const [availabilityStatus, setAvailabilityStatus] = useState<AvailabilityStatus>("idle")
  const [isSaving, setIsSaving] = useState(false)
  const [displayName, setDisplayName] = useState("")
  const [showSuccess, setShowSuccess] = useState(false)

  // Load initial data
  useEffect(() => {
    const fetchProfile = async () => {
      const token = localStorage.getItem("authToken");
      if (!token) return;

      try {
        const res = await fetch(`${API_URL}/api/v1/profile/me`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        if (res.ok) {
          const data = await res.json();
          setUsername(data.username || "");
          setDisplayName(data.name || "");
          setBio(data.bioText || "");
          setThemeColor(data.themeColor || PRESET_COLORS[0].value);
        }
      } catch (err) {
        console.error("Failed to fetch profile", err);
      }
    };
    fetchProfile();
  }, []);

  // Professional Debounced Availability Check
  useEffect(() => {
    if (!username || username.length < 3) {
      setAvailabilityStatus("idle")
      return
    }

    setAvailabilityStatus("checking")
    
    const checkUsername = async () => {
      const token = localStorage.getItem("authToken");
      try {
        const res = await fetch(`${API_URL}/api/v1/profile/username-check?username=${username}`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        if (res.ok) {
          const data = await res.json();
          setAvailabilityStatus(data.available ? "available" : "taken");
        }
      } catch (err) {
        console.error("Username check failed", err);
        setAvailabilityStatus("idle");
      }
    };

    const timer = setTimeout(checkUsername, 400); // 400ms debounce
    return () => clearTimeout(timer);
  }, [username]);

  const handleCustomHexChange = (value: string) => {
    setCustomHex(value)
    if (/^#[0-9A-Fa-f]{6}$/.test(value)) {
      setThemeColor(value)
    }
  }

  const handleSave = async () => {
    setIsSaving(true)
    const token = localStorage.getItem("authToken");
    try {
      const response = await fetch(`${API_URL}/api/v1/profile/settings`, {
        method: "POST",
        headers: { 
          "Authorization": `Bearer ${token}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          username: username,
          bioText: bio,
          themeColor: themeColor,
          name: displayName
        })
      });

      if (!response.ok) {
        const data = await response.json();
        toast.error(data.message || "Failed to update profile");
      } else {
        localStorage.setItem("userHandle", username); // Save username for Dashboard navigation
        setShowSuccess(true);
        toast.success("Profile updated successfully!", {
          description: "Your public bio-link is now live.",
          icon: <Sparkles className="h-4 w-4 text-emerald-500" />
        });
        setTimeout(() => setShowSuccess(false), 3000);
      }
    } catch (err) {
      console.error("Save error", err);
      toast.error("An error occurred while saving profile");
    } finally {
      setIsSaving(false)
    }
  }

  const bioCharCount = bio.length
  const maxBioChars = 160

  return (
    <div className="bg-background/50 rounded-3xl border border-border/50 overflow-hidden">
      <div className="p-6 md:p-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground md:text-3xl">
            Profile Settings
          </h1>
          <p className="mt-1.5 text-sm text-muted-foreground">
            Customize your public profile appearance
          </p>
          
        {username && (
          <motion.div 
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="mt-4 flex flex-wrap items-center gap-2 p-3 bg-primary/5 rounded-xl border border-primary/10 w-fit"
          >
            <div className="flex items-center gap-1.5 mr-2">
              <Link className="h-3.5 w-3.5 text-primary" />
              <span className="text-[10px] font-bold text-primary/60 uppercase tracking-widest">Public Bio Link</span>
            </div>
            <code className="text-sm font-bold text-primary bg-primary/10 px-2 py-0.5 rounded-md">
              {typeof window !== 'undefined' ? window.location.host : 'shunyalink.madhavv.me'}/@{username}
            </code>
            <div className="flex items-center gap-1 ml-2">
              <Button 
                variant="ghost" 
                size="sm" 
                className="h-8 w-8 p-0 rounded-full hover:bg-primary/20"
                onClick={() => {
                  const url = `${window.location.protocol}//${window.location.host}/@${username}`;
                  navigator.clipboard.writeText(url);
                  toast.success("Link copied to clipboard!");
                }}
              >
                <Copy className="h-3.5 w-3.5 text-primary" />
              </Button>
              <Button 
                variant="ghost" 
                size="sm" 
                className="h-8 w-8 p-0 rounded-full hover:bg-primary/20"
                onClick={() => window.open(`/@${username}`, '_blank')}
              >
                <ExternalLink className="h-3.5 w-3.5 text-primary" />
              </Button>
            </div>
          </motion.div>
        )}
        </div>

        <div className="flex flex-col gap-8 lg:flex-row lg:items-start">
          {/* Settings Panel */}
          <div className="flex-1 space-y-6">
            {/* Glass Card Container */}
            <div className="rounded-2xl border border-border/50 bg-card/40 p-6 backdrop-blur-xl">
              {/* Display Name */}
              <div className="mb-6">
                <Label htmlFor="displayName" className="mb-2 flex items-center gap-2 text-sm font-medium text-foreground">
                  <User className="h-4 w-4 text-muted-foreground" />
                  Display Name
                </Label>
                <Input
                  id="displayName"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="Your display name"
                  className="h-11 border-border/50 bg-input/50 text-foreground placeholder:text-muted-foreground focus-visible:ring-1 focus-visible:ring-ring/50"
                />
              </div>

              {/* Unique Username */}
              <div className="mb-6">
                <Label htmlFor="username" className="mb-2 flex items-center gap-2 text-sm font-medium text-foreground">
                  <AtSign className="h-4 w-4 text-muted-foreground" />
                  Unique Username
                </Label>
                <div className="relative">
                  <div className="flex items-center gap-0 rounded-lg border border-border/50 bg-input/50 focus-within:ring-1 focus-within:ring-ring/50">
                    <span className="whitespace-nowrap pl-3 text-sm text-muted-foreground">
                      @{/* Changed to show only @ for simplicity */}
                    </span>
                    <Input
                      id="username"
                      value={username}
                      onChange={(e) => setUsername(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, ''))}
                      placeholder="username"
                      className="h-11 border-0 bg-transparent pl-0 text-foreground placeholder:text-muted-foreground focus-visible:ring-0"
                    />
                    {/* Availability Badge */}
                    <div className="flex items-center pr-3">
                      {availabilityStatus === "checking" && (
                        <div className="flex items-center gap-1.5 rounded-full bg-muted/50 px-2.5 py-1 text-xs">
                          <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />
                          <span className="text-muted-foreground">Checking</span>
                        </div>
                      )}
                      {availabilityStatus === "available" && (
                        <div className="flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs">
                          <Check className="h-3 w-3 text-emerald-500" />
                          <span className="text-emerald-500">Available</span>
                        </div>
                      )}
                      {availabilityStatus === "taken" && (
                        <div className="flex items-center gap-1.5 rounded-full bg-rose-500/10 px-2.5 py-1 text-xs">
                          <X className="h-3 w-3 text-rose-500" />
                          <span className="text-rose-500">Taken</span>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
                <p className="mt-2 text-xs text-muted-foreground">
                  Only lowercase letters, numbers, and underscores allowed
                </p>
              </div>

              {/* Bio */}
              <div className="mb-6">
                <div className="mb-2 flex items-center justify-between">
                  <Label htmlFor="bio" className="flex items-center gap-2 text-sm font-medium text-foreground">
                    <Type className="h-4 w-4 text-muted-foreground" />
                    Bio
                  </Label>
                  <span className={cn(
                    "text-xs",
                    bioCharCount > maxBioChars ? "text-rose-500" : "text-muted-foreground"
                  )}>
                    {bioCharCount}/{maxBioChars}
                  </span>
                </div>
                <Textarea
                  id="bio"
                  value={bio}
                  onChange={(e) => setBio(e.target.value.slice(0, maxBioChars))}
                  placeholder="Tell others about yourself..."
                  rows={4}
                  className="resize-none border-border/50 bg-input/50 text-foreground placeholder:text-muted-foreground focus-visible:ring-1 focus-visible:ring-ring/50"
                />
              </div>

              {/* Theme Color Picker */}
              <div>
                <Label className="mb-3 flex items-center gap-2 text-sm font-medium text-foreground">
                  <Palette className="h-4 w-4 text-muted-foreground" />
                  Theme Color
                </Label>
                
                {/* Preset Colors */}
                <div className="mb-4 flex flex-wrap gap-3">
                  {PRESET_COLORS.map((color) => (
                    <button
                      key={color.value}
                      onClick={() => {
                        setThemeColor(color.value)
                        setCustomHex("")
                      }}
                      className={cn(
                        "group relative h-10 w-10 rounded-full transition-all duration-200 hover:scale-110",
                        themeColor === color.value && "ring-2 ring-foreground ring-offset-2 ring-offset-background"
                      )}
                      style={{ backgroundColor: color.value }}
                      aria-label={`Select ${color.name} theme`}
                    >
                      {themeColor === color.value && (
                        <Check className="absolute inset-0 m-auto h-4 w-4 text-white drop-shadow-md" />
                      )}
                      <span className="absolute -bottom-6 left-1/2 -translate-x-1/2 whitespace-nowrap text-xs text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100">
                        {color.name}
                      </span>
                    </button>
                  ))}
                </div>

                {/* Custom Hex Input */}
                <div className="flex items-center gap-3">
                  <div 
                    className="h-10 w-10 shrink-0 rounded-full border border-border/50"
                    style={{ backgroundColor: /^#[0-9A-Fa-f]{6}$/.test(customHex) ? customHex : themeColor }}
                  />
                  <div className="relative flex-1">
                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground">
                      #
                    </span>
                    <Input
                      value={customHex.replace('#', '')}
                      onChange={(e) => handleCustomHexChange('#' + e.target.value.replace('#', '').slice(0, 6))}
                      placeholder="Custom hex (e.g., 6366f1)"
                      className="h-10 border-border/50 bg-input/50 pl-7 font-mono text-sm text-foreground placeholder:text-muted-foreground focus-visible:ring-1 focus-visible:ring-ring/50"
                      maxLength={6}
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Save Button */}
            <Button
              onClick={handleSave}
              disabled={isSaving || availabilityStatus === "taken" || !username}
              className="h-12 w-full text-base font-medium transition-all duration-200 disabled:opacity-50"
              style={{ 
                backgroundColor: themeColor,
                color: '#ffffff'
              }}
            >
              {isSaving ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="h-5 w-5 animate-spin" />
                  Saving Changes...
                </span>
              ) : showSuccess ? (
                <motion.span 
                  initial={{ scale: 0.5, opacity: 0 }}
                  animate={{ scale: 1, opacity: 1 }}
                  className="flex items-center gap-2"
                >
                  <Check className="h-5 w-5" />
                  All Saved!
                </motion.span>
              ) : (
                <span className="flex items-center gap-2">
                  <Sparkles className="h-5 w-5" />
                  Save Profile
                </span>
              )}
            </Button>
          </div>

          {/* Live Preview */}
          <div className="lg:sticky lg:top-8 lg:w-80">
            <div className="mb-3 flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Smartphone className="h-4 w-4" />
              Live Preview
            </div>
            
            {/* Mobile Frame */}
            <div className="mx-auto w-full max-w-[280px] lg:max-w-none">
              <div className="rounded-[2rem] border-4 border-border/50 bg-background/80 p-2 backdrop-blur-xl">
                {/* Phone Notch */}
                <div className="mx-auto mb-2 h-6 w-24 rounded-full bg-border/50" />
                
                {/* Preview Content */}
                <div 
                  className="relative overflow-hidden rounded-2xl p-4"
                  style={{ 
                    background: `linear-gradient(135deg, ${themeColor}20 0%, transparent 50%)` 
                  }}
                >
                  {/* Avatar Placeholder with Glow */}
                  <div className="relative mx-auto mb-3 h-20 w-20">
                    <div 
                      className="absolute inset-0 animate-spin-slow rounded-full opacity-70"
                      style={{ 
                        background: `conic-gradient(from 0deg, ${themeColor}, transparent, ${themeColor})`,
                        padding: '2px'
                      }}
                    />
                    <div 
                      className="absolute inset-0.5 rounded-full bg-background flex items-center justify-center border-2"
                      style={{ borderColor: `${themeColor}40` }}
                    >
                      <User className="h-8 w-8 text-foreground/60" />
                    </div>
                  </div>

                  {/* Display Name */}
                  <div className="mb-1 text-center">
                    <h3 className="text-lg font-semibold text-foreground">
                      {displayName || "Your Name"}
                    </h3>
                  </div>

                  {/* Username */}
                  <div className="mb-3 text-center">
                    <span 
                      className="text-sm font-medium"
                      style={{ color: themeColor }}
                    >
                      @{username || "username"}
                    </span>
                  </div>

                  {/* Bio */}
                  <p className="mb-4 text-center text-xs leading-relaxed text-muted-foreground">
                    {bio || "Your bio will appear here..."}
                  </p>

                  {/* Sample Link Buttons */}
                  <div className="space-y-2">
                    {["Portfolio", "Twitter", "GitHub"].map((link) => (
                      <div
                        key={link}
                        className="w-full rounded-lg border border-border/50 bg-card/50 px-4 py-2.5 text-center text-xs font-medium text-foreground backdrop-blur-sm transition-all duration-200 hover:scale-[1.02]"
                        style={{
                          borderColor: `${themeColor}30`,
                        }}
                      >
                        {link}
                      </div>
                    ))}
                  </div>

                  {/* Branded Footer */}
                  <div className="mt-4 flex items-center justify-center gap-1 text-[10px] text-muted-foreground/50">
                    <Sparkles className="h-2.5 w-2.5" />
                    shunyalink.madhavv.me
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
