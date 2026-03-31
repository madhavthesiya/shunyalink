"use client";

import { useSortable, SortableContext, verticalListSortingStrategy, arrayMove } from "@dnd-kit/sortable";
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors, DragEndEvent } from "@dnd-kit/core";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical, Link2, ExternalLink, Hash } from "lucide-react";
import { toast } from "sonner";
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

interface BioLinksReorderProps {
  urls: UrlData[];
  setUrls: React.Dispatch<React.SetStateAction<UrlData[]>>;
  API_URL: string;
}

function SortableBioLinkItem({ url, API_URL }: { url: UrlData, API_URL: string }) {
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
    zIndex: isDragging ? 50 : 1,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "flex items-center gap-4 p-4 bg-card border border-border/50 rounded-xl transition-all shadow-sm",
        isDragging && "opacity-50 ring-2 ring-primary scale-[1.02] shadow-lg bg-secondary",
        "hover:border-primary/30 relative"
      )}
    >
      <button 
        type="button" 
        className="cursor-move text-muted-foreground hover:text-primary transition-colors touch-none"
        {...attributes} 
        {...listeners}
      >
        <GripVertical className="w-5 h-5" />
      </button>
      
      <div className="flex-1 min-w-0">
        <h4 className="text-sm font-semibold text-foreground truncate flex items-center gap-2">
          {url.title || url.shortId}
        </h4>
        <div className="flex items-center gap-2 mt-1 opacity-70">
          <Hash className="w-3 h-3" />
          <span className="text-xs font-mono truncate">{url.shortId}</span>
        </div>
      </div>
      
      <a
        href={`${API_URL}/${url.shortId}`}
        target="_blank"
        rel="noopener noreferrer"
        className="shrink-0 w-8 h-8 flex items-center justify-center rounded-lg hover:bg-primary/10 text-muted-foreground hover:text-primary transition-colors"
        title="Test Link"
      >
        <ExternalLink className="w-4 h-4" />
      </a>
    </div>
  );
}

export function BioLinksReorder({ urls, setUrls, API_URL }: BioLinksReorderProps) {
  const bioUrls = urls.filter(u => u.showOnBio);
  
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } })
  );

  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event;

    if (active.id !== over?.id) {
      const oldIndex = bioUrls.findIndex((url) => url.shortId === active.id);
      const newIndex = bioUrls.findIndex((url) => url.shortId === over?.id);
      
      const nextBioUrls = arrayMove(bioUrls, oldIndex, newIndex);
      
      // Update global urls explicitly reordering ONLY the bio items IN PLACE
      setUrls(prev => {
        const result = [...prev];
        let bioPointer = 0;
        for (let i = 0; i < result.length; i++) {
          if (result[i].showOnBio) {
            result[i] = nextBioUrls[bioPointer++];
          }
        }
        return result;
      });

      const token = localStorage.getItem("authToken");
      if (!token) return;
      try {
        const res = await fetch(`${API_URL}/api/v1/url/reorder`, {
          method: "PUT",
          headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
          body: JSON.stringify({ shortIds: nextBioUrls.map(u => u.shortId) })
        });
        if (!res.ok) toast.error("Failed to save new order");
      } catch (err) {
        toast.error("Network error while saving order");
      }
    }
  };

  if (bioUrls.length === 0) {
    return (
      <div className="p-8 text-center bg-card rounded-2xl border border-border/50 mt-8">
        <Link2 className="w-8 h-8 mx-auto mb-3 text-muted-foreground opacity-50" />
        <p className="text-sm text-foreground font-medium">No links on your Bio-Link profile yet.</p>
        <p className="text-xs text-muted-foreground mt-1">Go to "Your Links" and turn on the "Bio" toggle to feature them here.</p>
      </div>
    );
  }

  return (
    <div className="bg-background/50 rounded-3xl border border-border/50 overflow-hidden mt-8">
      <div className="p-6 md:p-8">
        <div className="mb-6">
          <h2 className="text-xl font-semibold text-foreground">Reorder Public Links</h2>
          <p className="text-sm text-muted-foreground mt-1">
            Drag and drop to arrange the sequence of links on your live Bio-Link profile.
          </p>
        </div>
        
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={bioUrls.map(u => u.shortId)} strategy={verticalListSortingStrategy}>
            <div className="space-y-3">
              {bioUrls.map((url) => (
                <SortableBioLinkItem key={url.shortId} url={url} API_URL={API_URL} />
              ))}
            </div>
          </SortableContext>
        </DndContext>
      </div>
    </div>
  );
}
