export interface DashboardUrlData {
  showOnBio: boolean;
  shortId: string;
  longUrl: string;
  clickCount: number;
  lastAccessedTime: string | null;
  createdAt: string;
  title?: string;
  passwordProtected: boolean;
  password?: string;
}
