"use client";

import { GoogleOAuthProvider } from "@react-oauth/google";

export function GoogleProvider({ children }: { children: React.ReactNode }) {
  // Use environment variable for the client ID
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID || "520225938600-lci1t3a5eb08udd6qgup6qcqa6vv7cbr.apps.googleusercontent.com";
  
  return (
    <GoogleOAuthProvider clientId={clientId}>
      {children}
    </GoogleOAuthProvider>
  );
}
