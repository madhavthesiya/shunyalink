"use client";

import { useEffect, useState } from "react";
import { X, Save, Key, Type, Loader2, Eye, EyeOff, ShieldCheck, Tag, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

interface EditMetadataModalProps {
  shortId: string;
  initialTitle?: string;
  initialPasswordProtected: boolean;
  initialTags?: string[];
  isOpen: boolean;
  onClose: () => void;
  onUpdate: (shortId: string, newTitle: string, newPasswordProtected: boolean, newTags?: string[]) => void;
}

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function EditMetadataModal({ 
  shortId, 
  initialTitle, 
  initialPasswordProtected,
  initialTags = [],
  isOpen, 
  onClose,
  onUpdate
}: EditMetadataModalProps) {
  const [title, setTitle] = useState(initialTitle || "");
  const [password, setPassword] = useState("");
  const [removePassword, setRemovePassword] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);
  const [tags, setTags] = useState<string[]>(initialTags);
  const [tagInput, setTagInput] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setTitle(initialTitle || "");
      setPassword("");
      setRemovePassword(false);
      setTags(initialTags || []);
      setTagInput("");
      setError(null);
    }
  }, [isOpen, initialTitle, initialPasswordProtected, initialTags]);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    if (isOpen) {
      document.addEventListener("keydown", handleEscape);
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.body.style.overflow = "";
    };
  }, [isOpen, onClose]);

  const handleSave = async () => {
    setIsUpdating(true);
    setError(null);
    const token = localStorage.getItem("authToken");

    // Logic for password:
    // - If removePassword is true -> send "" (remove)
    // - If password has value -> send password (update)
    // - Otherwise -> send null (no change)
    let passwordToSend: string | null = null;
    if (removePassword) {
      passwordToSend = "";
    } else if (password.trim() !== "") {
      passwordToSend = password.trim();
    }

    try {
        const promises = [
          fetch(`${API_URL}/api/v1/url/${shortId}/metadata`, {
            method: "PATCH",
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({
              title: title.trim(),
              password: passwordToSend,
            }),
          })
        ];

        // Also update tags if initialTags differ or just aggressively send it
        promises.push(
          fetch(`${API_URL}/api/v1/url/${shortId}/tags`, {
            method: "PATCH",
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({ tags }),
          })
        );

        const responses = await Promise.all(promises);
        const failedResponse = responses.find(r => !r.ok);

        if (!failedResponse) {
          let isNowProtected = initialPasswordProtected;
          if (removePassword) isNowProtected = false;
          else if (password.trim() !== "") isNowProtected = true;

          onUpdate(shortId, title.trim(), isNowProtected, tags);
          onClose();
        } else {
          try {
            const data = await failedResponse.json();
            setError(data.message || "Failed to update one or more fields.");
          } catch {
            setError("Failed to update one or more fields.");
          }
        }
    } catch (err) {
      setError("Network error. Please try again.");
    } finally {
      setIsUpdating(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-foreground/10 backdrop-blur-md animate-in fade-in-0 duration-200"
      onClick={onClose}
    >
      <div 
        className="w-full max-w-md glass-card rounded-2xl shadow-2xl animate-in zoom-in-95 slide-in-from-bottom-4 duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-border/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 flex items-center justify-center">
              <ShieldCheck className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-foreground">Edit Link</h2>
              <p className="text-xs text-muted-foreground font-mono">/{shortId}</p>
            </div>
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="rounded-full hover:bg-muted/50"
          >
            <X className="w-5 h-5" />
          </Button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-6">
          {error && (
            <div className="p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-sm font-medium">
              {error}
            </div>
          )}

          <div className="space-y-4">
            {/* Title Input */}
            <div className="space-y-2">
              <Label htmlFor="edit-title" className="flex items-center gap-2 text-sm font-medium text-foreground">
                <Type className="w-4 h-4 text-primary" />
                Link Title
              </Label>
              <Input
                id="edit-title"
                placeholder="e.g. My Portfolio"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                className="h-12 bg-background/50 border-border/50 rounded-xl premium-input focus:border-primary/50 focus:ring-2 focus:ring-primary/10 transition-all"
                disabled={isUpdating}
              />
              <p className="text-[10px] text-muted-foreground/60 italic">Friendly name shown on your Bio Profile.</p>
            </div>

            {/* Tags Input */}
            <div className="space-y-2">
              <Label htmlFor="edit-tags" className="flex items-center gap-2 text-sm font-medium text-foreground">
                <Tag className="w-4 h-4 text-primary" />
                Folders & Tags
              </Label>
              <div className="flex gap-2">
                <Input
                  id="edit-tagInput"
                  type="text"
                  placeholder="e.g. Marketing, YouTube"
                  value={tagInput}
                  onChange={(e) => setTagInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      e.stopPropagation();
                      if (tagInput.trim() && !tags.includes(tagInput.trim())) {
                        setTags([...tags, tagInput.trim()]);
                        setTagInput("");
                      }
                    }
                  }}
                  className="h-10 bg-background/50 border-border/50 rounded-xl"
                  disabled={isUpdating}
                />
                <Button 
                  type="button" 
                  variant="outline" 
                  onClick={() => {
                    if (tagInput.trim() && !tags.includes(tagInput.trim())) {
                      setTags([...tags, tagInput.trim()]);
                      setTagInput("");
                    }
                  }}
                  disabled={isUpdating}
                  className="rounded-xl"
                >
                  <Plus className="w-4 h-4" />
                </Button>
              </div>
              {tags.length > 0 && (
                <div className="flex flex-wrap gap-2 mt-2">
                  {tags.map((tag) => (
                    <span key={tag} className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-semibold bg-primary/10 text-primary border border-primary/20 rounded-full">
                      {tag}
                      <button type="button" onClick={() => setTags(tags.filter(t => t !== tag))} className="text-primary hover:text-primary/70">
                        <X className="w-3 h-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>

            {/* Password Input */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="edit-password" className="flex items-center gap-2 text-sm font-medium text-foreground">
                  <Key className="w-4 h-4 text-primary" />
                  {initialPasswordProtected ? "Update Password" : "Set Password"}
                </Label>
                {initialPasswordProtected && !removePassword && (
                  <Button 
                    variant="ghost" 
                    size="sm" 
                    onClick={() => setRemovePassword(true)}
                    className="h-7 px-2 text-[10px] text-destructive hover:bg-destructive/10 rounded-lg uppercase tracking-wider font-bold"
                  >
                    Remove Protection
                  </Button>
                )}
                {removePassword && (
                  <Button 
                    variant="ghost" 
                    size="sm" 
                    onClick={() => setRemovePassword(false)}
                    className="h-7 px-2 text-[10px] text-primary hover:bg-primary/10 rounded-lg uppercase tracking-wider font-bold"
                  >
                    Restore Protection
                  </Button>
                )}
              </div>
              
              {!removePassword ? (
                <div className="relative">
                  <Input
                    id="edit-password"
                    type={showPassword ? "text" : "password"}
                    placeholder={initialPasswordProtected ? "Enter a new password to replace the current one" : "Enter password"}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    minLength={4}
                    maxLength={20}
                    className="h-12 bg-background/50 border-border/50 rounded-xl premium-input focus:border-primary/50 focus:ring-2 focus:ring-primary/10 transition-all pr-12"
                    disabled={isUpdating}
                  />
                  <div className="absolute right-1 top-1/2 -translate-y-1/2 flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      aria-label={showPassword ? "Hide password" : "Show password"}
                      className="p-2 rounded-lg text-muted-foreground hover:bg-primary/10 hover:text-primary transition-colors"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              ) : (
                <div className="h-12 flex items-center px-4 bg-destructive/5 border border-dashed border-destructive/30 rounded-xl text-destructive text-xs font-medium">
                  Protection will be removed upon saving.
                </div>
              )}
              
              <p className="text-[10px] text-muted-foreground/60 italic leading-snug">
                {initialPasswordProtected 
                  ? (removePassword ? "Changes will be applied." : "Link is protected. Passwords are stored as one-way hashes, so they can't be shown — enter a new one to change it, or remove protection above.")
                  : "Link is public. Provide a password to protect it."}
              </p>
            </div>
          </div>

          <div className="flex gap-3 pt-2">
            <Button
              variant="outline"
              onClick={onClose}
              className="flex-1 h-12 rounded-xl text-foreground/70"
              disabled={isUpdating}
            >
              Cancel
            </Button>
            <Button
              onClick={handleSave}
              className="flex-[2] h-12 rounded-xl font-semibold bg-gradient-to-r from-primary to-primary/90 hover:from-primary/90 hover:to-primary/80 text-primary-foreground btn-glow transition-all duration-300 shadow-lg shadow-primary/20"
              disabled={isUpdating}
            >
              {isUpdating ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="w-4 h-4 mr-2" />
                  Save Changes
                </>
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
