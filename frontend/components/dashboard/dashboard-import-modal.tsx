"use client";

import { useEffect, useState, useRef } from "react";
import { X, UploadCloud, Loader2, FileSpreadsheet, CheckCircle2, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";

interface ImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function DashboardImportModal({ isOpen, onClose, onSuccess }: ImportModalProps) {
  const [file, setFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!isOpen) {
      setFile(null);
      setIsUploading(false);
    }
  }, [isOpen]);

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

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selectedFile = e.target.files[0];
      if (selectedFile.type !== "text/csv" && !selectedFile.name.endsWith('.csv')) {
        toast.error("Please upload a valid CSV file");
        return;
      }
      setFile(selectedFile);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const selectedFile = e.dataTransfer.files[0];
      if (selectedFile.type !== "text/csv" && !selectedFile.name.endsWith('.csv')) {
        toast.error("Please upload a valid CSV file");
        return;
      }
      setFile(selectedFile);
    }
  };

  const handleUpload = async () => {
    if (!file) return;

    setIsUploading(true);
    const token = localStorage.getItem("authToken");

    const formData = new FormData();
    formData.append("file", file);

    try {
      const response = await fetch(`${API_URL}/api/v1/url/bulk-import`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
        },
        body: formData,
      });

      const data = await response.json();

      if (response.ok) {
        if (data.errors && data.errors.length > 0) {
          toast.warning(`Imported ${data.successCount} links with ${data.failCount} failures`, {
            description: "Check the console for detailed failure reasons."
          });
          console.warn("Import errors:", data.errors);
        } else {
          toast.success(`Successfully imported ${data.successCount} links`);
        }
        onSuccess();
        onClose();
      } else {
        toast.error(data.message || "Failed to import links");
      }
    } catch (err) {
      toast.error("Network error during import. Please try again.");
    } finally {
      setIsUploading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div 
      className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-foreground/10 backdrop-blur-md animate-in fade-in-0 duration-200"
      onClick={onClose}
    >
      <div 
        className="w-full max-w-lg glass-card rounded-2xl shadow-2xl animate-in zoom-in-95 slide-in-from-bottom-4 duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between p-6 border-b border-border/50">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 flex items-center justify-center">
              <FileSpreadsheet className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-foreground">Bulk Import CSV</h2>
              <p className="text-xs text-muted-foreground">Upload multiple links at once</p>
            </div>
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="rounded-full hover:bg-muted/50"
            disabled={isUploading}
          >
            <X className="w-5 h-5" />
          </Button>
        </div>

        <div className="p-6 space-y-6">
          <div className="bg-primary/5 border border-primary/10 rounded-xl p-4 text-sm text-muted-foreground leading-relaxed">
            <p className="font-medium text-foreground mb-1">CSV Format Requirements:</p>
            <ul className="list-disc list-inside space-y-1 text-xs">
              <li>Header row is required</li>
              <li>Required column: <code className="bg-primary/10 text-primary px-1 rounded">longUrl</code> or <code className="bg-primary/10 text-primary px-1 rounded">URL</code></li>
              <li>Optional columns: <code className="bg-primary/10 text-primary px-1 rounded">customAlias</code>, <code className="bg-primary/10 text-primary px-1 rounded">title</code>, <code className="bg-primary/10 text-primary px-1 rounded">tags</code> (separated by |)</li>
            </ul>
          </div>

          <div 
            className={`border-2 border-dashed rounded-2xl p-8 text-center transition-all ${
              file ? "border-primary/50 bg-primary/5" : "border-border hover:border-primary/30 hover:bg-muted/30"
            }`}
            onDragOver={handleDragOver}
            onDrop={handleDrop}
            onClick={() => !file && fileInputRef.current?.click()}
          >
            <input 
              type="file" 
              accept=".csv,text/csv" 
              className="hidden" 
              ref={fileInputRef}
              onChange={handleFileChange}
            />
            
            {file ? (
              <div className="space-y-4">
                <div className="w-12 h-12 rounded-full bg-primary/20 flex items-center justify-center mx-auto">
                  <CheckCircle2 className="w-6 h-6 text-primary" />
                </div>
                <div>
                  <p className="text-sm font-medium text-foreground">{file.name}</p>
                  <p className="text-xs text-muted-foreground mt-1">{(file.size / 1024).toFixed(1)} KB</p>
                </div>
                <Button 
                  variant="outline" 
                  size="sm" 
                  onClick={(e) => {
                    e.stopPropagation();
                    setFile(null);
                  }}
                  disabled={isUploading}
                >
                  Remove file
                </Button>
              </div>
            ) : (
              <div className="space-y-4 cursor-pointer">
                <div className="w-12 h-12 rounded-full bg-secondary/50 flex items-center justify-center mx-auto">
                  <UploadCloud className="w-6 h-6 text-muted-foreground" />
                </div>
                <div>
                  <p className="text-sm font-medium text-foreground">Click or drag and drop your CSV</p>
                  <p className="text-xs text-muted-foreground mt-1">Max 50 links per import</p>
                </div>
              </div>
            )}
          </div>

          <div className="flex gap-3 pt-2">
            <Button
              variant="outline"
              onClick={onClose}
              className="flex-1 h-12 rounded-xl"
              disabled={isUploading}
            >
              Cancel
            </Button>
            <Button
              onClick={handleUpload}
              disabled={!file || isUploading}
              className="flex-[2] h-12 rounded-xl font-semibold bg-primary hover:bg-primary/90 text-primary-foreground btn-glow transition-all"
            >
              {isUploading ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  Importing...
                </>
              ) : (
                "Import Links"
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
