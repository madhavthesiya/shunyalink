"use client";

import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Copy, QrCode, Loader2, TrendingUp, Trash2, Lock, Pencil, GripVertical } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { cn } from "@/lib/utils";

interface UrlData {
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

interface SortableUrlRowProps {
  url: UrlData;
  API_URL: string;
  isSelected: boolean;
  isTogglingBio: boolean;
  isCopied: boolean;
  onToggleSelect: () => void;
  onToggleBio: () => void;
  onCopy: () => void;
  onEdit: () => void;
  onInsights: () => void;
  onQR: () => void;
  onDelete: () => void;
  formatDate: (dateString: string) => string;
}

export function SortableUrlRow({
  url,
  API_URL,
  isSelected,
  isTogglingBio,
  isCopied,
  onToggleSelect,
  onToggleBio,
  onCopy,
  onEdit,
  onInsights,
  onQR,
  onDelete,
  formatDate
}: SortableUrlRowProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging
  } = useSortable({ id: url.shortId });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    zIndex: isDragging ? 10 : 1,
  };

  return (
    <tr
      ref={setNodeRef}
      style={style}
      className={cn(
        "border-b border-border/50 hover:bg-secondary/30 transition-colors",
        isSelected && "bg-primary/5 hover:bg-primary/10",
        isDragging && "opacity-50 bg-secondary"
      )}
    >
      <td className="px-6 py-4">
        <div className="flex items-center gap-2">
          <button 
            type="button" 
            className="cursor-move text-muted-foreground hover:text-primary transition-colors touch-none"
            {...attributes} 
            {...listeners}
            title="Drag to reorder"
          >
            <GripVertical className="w-4 h-4" />
          </button>
          <Checkbox 
            checked={isSelected}
            onCheckedChange={onToggleSelect}
            aria-label={`Select ${url.shortId}`}
            className="data-[state=checked]:bg-primary"
          />
        </div>
      </td>
      <td className="px-6 py-4">
        <a
          href={`${API_URL}/${url.shortId}`}
          target="_blank"
          rel="noopener noreferrer"
          className="text-primary hover:underline font-medium truncate flex items-center gap-2"
        >
          {url.title || url.shortId}
          {url.passwordProtected && (
            <Lock className="w-3 h-3 text-muted-foreground/60" />
          )}
        </a>
        {url.title && (
          <span className="text-[10px] text-muted-foreground/60 font-mono">
            /{url.shortId}
          </span>
        )}
      </td>
      <td className="px-6 py-4">
        <div className="truncate text-muted-foreground max-w-[300px]" title={url.longUrl}>
          {url.longUrl}
        </div>
      </td>
      <td className="px-6 py-4 text-center font-semibold">
        {url.clickCount}
      </td>
      <td className="px-6 py-4 text-muted-foreground">
        {formatDate(url.createdAt)}
      </td>
      <td className="px-6 py-4 text-center">
        <button 
          onClick={onToggleBio}
          disabled={isTogglingBio}
          className={`w-10 h-5 rounded-full transition-colors relative flex items-center ${url.showOnBio ? 'bg-primary' : 'bg-muted'} ${isTogglingBio ? 'opacity-50 cursor-not-allowed' : ''}`}
          title={url.showOnBio ? "Hide from Bio" : "Show on Bio"}
        >
          {isTogglingBio ? (
            <Loader2 className="w-3 h-3 animate-spin mx-auto text-white" />
          ) : (
            <div className={`absolute top-1 w-3 h-3 rounded-full bg-white transition-all ${url.showOnBio ? 'left-6' : 'left-1'}`} />
          )}
        </button>
      </td>
      <td className="px-6 py-4 text-right">
        <div className="flex items-center justify-end gap-2">
          <Button
            size="sm"
            variant="ghost"
            onClick={onCopy}
            className="h-8 w-8 p-0"
            title="Copy to clipboard"
          >
            <Copy
              className={`w-4 h-4 ${isCopied
                  ? "text-success"
                  : "text-muted-foreground"
                }`}
            />
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={onEdit}
            className="h-8 w-8 p-0 text-muted-foreground hover:text-primary transition-colors"
            title="Edit Link Metadata"
          >
            <Pencil className="w-4 h-4" />
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={onInsights}
            className="h-8 w-8 p-0 text-muted-foreground hover:text-primary hover:bg-primary/10"
            title="View detailed insights"
          >
            <TrendingUp className="w-4 h-4" />
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={onQR}
            className="h-8 w-8 p-0 text-muted-foreground"
            title="Generate QR code"
          >
            <QrCode className="w-4 h-4" />
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={onDelete}
            className="h-8 w-8 p-0 text-muted-foreground hover:text-destructive"
            title="Delete URL"
          >
            <Trash2 className="w-4 h-4" />
          </Button>
        </div>
      </td>
    </tr>
  );
}
