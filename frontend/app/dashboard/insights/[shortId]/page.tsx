"use client";

import React, { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { 
  LineChart, 
  Line, 
  AreaChart, 
  Area, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend
} from "recharts";
import { 
  ArrowLeft, 
  BarChart3, 
  Clock, 
  Calendar, 
  Globe, 
  MousePointer2,
  TrendingUp,
  ChevronRight,
  Monitor,
  Share2
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import Link from "next/link";

interface StatsData {
  shortId: string;
  longUrl: string;
  clickCount: number;
  lastAccessedTime: string;
  createdAt: string;
  title: string;
  timeSeries: Record<string, number>;
  countries: Record<string, number>;
  referrers: Record<string, number>;
  devices: Record<string, number>;
}

export default function InsightsPage() {
  const { shortId } = useParams();
  const router = useRouter();
  const [range, setRange] = useState<"24h" | "7d" | "all">("all");
  const [data, setData] = useState<StatsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const COLORS = ["#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899"];

  useEffect(() => {
    fetchStats();
  }, [shortId, range]);

  const fetchStats = async () => {
    setLoading(true);
    try {
      const token = localStorage.getItem("authToken");
      if (!token) {
        router.push("/login");
        return;
      }

      const response = await fetch(`${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"}/api/v1/url/stats/${shortId}?range=${range}`, {
        headers: { Authorization: `Bearer ${token}` }
      });

      if (!response.ok) throw new Error("Failed to fetch statistics");
      
      const result = await response.json();
      setData(result);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const chartData = data?.timeSeries 
    ? Object.entries(data.timeSeries).map(([name, value]) => ({ name, value }))
    : [];

  const geoData = data?.countries
    ? Object.entries(data.countries).map(([name, value]) => ({ name, value }))
    : [];

  const referrerData = data?.referrers
    ? Object.entries(data.referrers).map(([name, value]) => ({ name, value }))
    : [];

  const deviceData = data?.devices
    ? Object.entries(data.devices).map(([name, value]) => ({ name, value }))
    : [];

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <Card className="max-w-md w-full border-destructive/20 bg-destructive/5">
          <CardContent className="pt-6 text-center space-y-4">
            <div className="w-12 h-12 rounded-full bg-destructive/10 flex items-center justify-center mx-auto">
              <BarChart3 className="w-6 h-6 text-destructive" />
            </div>
            <h2 className="text-xl font-semibold">Error Loading Insights</h2>
            <p className="text-muted-foreground">{error}</p>
            <Button onClick={() => window.location.reload()} variant="outline">Try Again</Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background gradient-bg p-4 sm:p-8">
      <div className="max-w-6xl mx-auto space-y-8">
        {/* Navigation */}
        <div className="flex items-center justify-between">
          <Button variant="ghost" size="sm" asChild className="hover:bg-background/50">
            <Link href="/dashboard" className="flex items-center gap-2">
              <ArrowLeft className="w-4 h-4" />
              Back to Dashboard
            </Link>
          </Button>
          <div className="flex items-center gap-1 bg-secondary/30 p-1 rounded-xl backdrop-blur-md border border-border/50">
            {(["24h", "7d", "all"] as const).map((r) => (
              <Button
                key={r}
                size="sm"
                variant={range === r ? "secondary" : "ghost"}
                className={`rounded-lg px-4 transition-all duration-300 ${range === r ? "shadow-sm" : "text-muted-foreground hover:text-foreground"}`}
                onClick={() => setRange(r)}
              >
                {r === "all" ? "All Time" : r === "24h" ? "24 Hours" : "7 Days"}
              </Button>
            ))}
          </div>
        </div>

        {/* Header Section */}
        <div className="space-y-2">
          {loading ? (
            <Skeleton className="h-10 w-64 rounded-xl" />
          ) : (
            <h1 className="text-3xl font-bold tracking-tight">
              Insights for <span className="text-gradient font-extrabold">{data?.title || shortId}</span>
            </h1>
          )}
          <p className="text-muted-foreground flex items-center gap-2">
            <Globe className="w-4 h-4" />
            shunyalink.madhavv.me/{shortId}
          </p>
        </div>

        {/* Stats Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[
            { label: "Total Clicks", value: data?.clickCount, icon: MousePointer2, color: "text-blue-500", bg: "bg-blue-500/10" },
            { label: "Last Click", value: data?.lastAccessedTime ? new Date(data.lastAccessedTime).toLocaleDateString() : "Never", icon: Clock, color: "text-purple-500", bg: "bg-purple-500/10" },
            { label: "Created", value: data?.createdAt ? new Date(data.createdAt).toLocaleDateString() : "-", icon: Calendar, color: "text-emerald-500", bg: "bg-emerald-500/10" },
            { label: "Range Growth", value: `${chartData.length > 0 ? "+" + chartData.reduce((a, b) => a + b.value, 0) : "0"} Clicks`, icon: TrendingUp, color: "text-orange-500", bg: "bg-orange-500/10" },
          ].map((stat, i) => (
            <Card key={i} className="glass-card border-border/50 overflow-hidden group">
              <CardContent className="p-6">
                <div className="flex items-center justify-between">
                  <div className={`p-2 rounded-lg ${stat.bg} ${stat.color} group-hover:scale-110 transition-transform duration-300`}>
                    <stat.icon className="w-5 h-5" />
                  </div>
                  {loading ? <Skeleton className="h-8 w-16" /> : <span className="text-2xl font-bold tracking-tight">{stat.value}</span>}
                </div>
                <p className="text-xs font-medium text-muted-foreground mt-4 uppercase tracking-wider">{stat.label}</p>
              </CardContent>
            </Card>
          ))}
        </div>

        {/* Charts Section */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Click Activity Chart */}
          <Card className="glass-card border-border/50 overflow-hidden lg:col-span-2 min-h-[450px] flex flex-col">
            <CardHeader className="flex flex-row items-center justify-between border-b border-border/50 bg-secondary/10 pb-4">
              <div>
                <CardTitle className="text-lg">Click Activity</CardTitle>
                <CardDescription>Frequency of interactions over the selected period</CardDescription>
              </div>
              <BarChart3 className="w-5 h-5 text-muted-foreground" />
            </CardHeader>
            <CardContent className="flex-1 p-6 pt-10">
              {loading ? (
                <Skeleton className="h-[350px] w-full" />
              ) : chartData.length > 0 ? (
                <div className="h-[350px] w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={chartData}>
                      <defs>
                        <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
                          <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#88888820" />
                      <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 12 }} dy={10} />
                      <YAxis axisLine={false} tickLine={false} tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 12 }} />
                      <Tooltip contentStyle={{ backgroundColor: 'hsl(var(--background))', borderColor: 'hsl(var(--border))', borderRadius: '12px' }} />
                      <Area type="monotone" dataKey="value" stroke="#3b82f6" strokeWidth={3} fill="url(#colorValue)" />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <div className="h-full flex flex-col items-center justify-center text-muted-foreground py-20">
                  <p>No data recorded for this time period yet.</p>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Geo-Distribution Pie Chart */}
          <Card className="glass-card border-border/50 overflow-hidden flex flex-col">
            <CardHeader className="border-b border-border/50 bg-secondary/10 pb-4">
              <CardTitle className="text-lg">Geo-Distribution</CardTitle>
              <CardDescription>Traffic sources by country</CardDescription>
            </CardHeader>
            <CardContent className="flex-1 p-6 flex items-center justify-center">
              {loading ? (
                <Skeleton className="h-[300px] w-[300px] rounded-full" />
              ) : geoData.length > 0 ? (
                <ResponsiveContainer width="100%" height={350}>
                  <PieChart>
                    <Pie data={geoData} innerRadius={60} outerRadius={100} paddingAngle={5} dataKey="value">
                      {geoData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex flex-col items-center justify-center text-muted-foreground py-20">
                  <Globe className="w-12 h-12 mb-4 opacity-20" />
                  <p>No geographic data available yet</p>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Referrer & Device Charts */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Referrer Sources */}
          <Card className="glass-card border-border/50 overflow-hidden flex flex-col">
            <CardHeader className="border-b border-border/50 bg-secondary/10 pb-4">
              <CardTitle className="text-lg flex items-center gap-2"><Share2 className="w-5 h-5 text-muted-foreground" /> Referrer Sources</CardTitle>
              <CardDescription>Where your traffic is coming from</CardDescription>
            </CardHeader>
            <CardContent className="flex-1 p-6 flex items-center justify-center">
              {loading ? (
                <Skeleton className="h-[300px] w-[300px] rounded-full" />
              ) : referrerData.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                  <PieChart>
                    <Pie data={referrerData} innerRadius={50} outerRadius={90} paddingAngle={5} dataKey="value">
                      {referrerData.map((entry, index) => (
                        <Cell key={`ref-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex flex-col items-center justify-center text-muted-foreground py-20">
                  <Share2 className="w-12 h-12 mb-4 opacity-20" />
                  <p>No referrer data available yet</p>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Device / Browser */}
          <Card className="glass-card border-border/50 overflow-hidden flex flex-col">
            <CardHeader className="border-b border-border/50 bg-secondary/10 pb-4">
              <CardTitle className="text-lg flex items-center gap-2"><Monitor className="w-5 h-5 text-muted-foreground" /> Device / Browser</CardTitle>
              <CardDescription>What devices and browsers visitors use</CardDescription>
            </CardHeader>
            <CardContent className="flex-1 p-6 flex items-center justify-center">
              {loading ? (
                <Skeleton className="h-[300px] w-[300px] rounded-full" />
              ) : deviceData.length > 0 ? (
                <ResponsiveContainer width="100%" height={300}>
                  <PieChart>
                    <Pie data={deviceData} innerRadius={50} outerRadius={90} paddingAngle={5} dataKey="value">
                      {deviceData.map((entry, index) => (
                        <Cell key={`dev-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex flex-col items-center justify-center text-muted-foreground py-20">
                  <Monitor className="w-12 h-12 mb-4 opacity-20" />
                  <p>No device data available yet</p>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
