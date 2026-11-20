export interface DashboardUrlData {
  showOnBio: boolean;
  shortId: string;
  longUrl: string;
  clickCount: number;
  lastAccessedTime: string | null;
  createdAt: string;
  title?: string;
  passwordProtected: boolean;
  /** FLAGGED: the safety checks blocked the link, so it no longer redirects (3.4c). */
  status?: "ACTIVE" | "FLAGGED";
  category?: string;
  tags?: string[];
}
