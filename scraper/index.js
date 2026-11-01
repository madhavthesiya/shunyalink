const crypto = require("crypto");
const express = require("express");
const puppeteer = require("puppeteer");

const app = express();
const PORT = 3001;

// ── Access control & abuse limits ───────────────────────────────────────────
// This service is publicly reachable, and every scrape holds a Chrome tab. Only our backend
// (which sends the shared secret) may use it, and parallel tabs are capped.
const API_KEY = process.env.SCRAPER_API_KEY || "";
const VALID_HANDLE = /^[A-Za-z0-9_]{1,30}$/;
const MAX_CONCURRENT_SCRAPES = 2;
const MAX_QUEUED_SCRAPES = 10;

if (!API_KEY) {
  console.warn("[Security] SCRAPER_API_KEY is not set: /codechef is open to anyone. Set it in production.");
}

function hasValidKey(req) {
  if (!API_KEY) return true;
  const provided = Buffer.from(req.get("X-Scraper-Key") || "");
  const expected = Buffer.from(API_KEY);
  return provided.length === expected.length && crypto.timingSafeEqual(provided, expected);
}

let activeScrapes = 0;
const waitingScrapes = [];

/** Resolves when a tab slot is free; null when the waiting line is full (caller answers 503). */
function acquireScrapeSlot() {
  if (activeScrapes < MAX_CONCURRENT_SCRAPES) {
    activeScrapes++;
    return Promise.resolve();
  }
  if (waitingScrapes.length >= MAX_QUEUED_SCRAPES) return null;
  return new Promise((resolve) => waitingScrapes.push(resolve));
}

function releaseScrapeSlot() {
  const next = waitingScrapes.shift();
  if (next) next(); // hand the slot straight to the next waiter
  else activeScrapes--;
}

// ── Persistent Browser Instance ─────────────────────────────────────────────
// Keep Chrome alive instead of launching/closing on every request.
// Saves ~3-4 seconds per scrape by reusing the same browser process.
let browser = null;

async function getBrowser() {
  if (browser && browser.connected) return browser;

  console.log("[Browser] Launching persistent Chrome instance...");
  browser = await puppeteer.launch({
    headless: "new",
    executablePath:
      process.env.PUPPETEER_EXECUTABLE_PATH || puppeteer.executablePath(),
    args: [
      "--no-sandbox",
      "--disable-setuid-sandbox",
      "--disable-dev-shm-usage",
      "--disable-gpu",
      "--disable-extensions",
      "--disable-background-networking",
      "--single-process",
    ],
  });

  // Auto-restart if Chrome crashes
  browser.on("disconnected", () => {
    console.log("[Browser] Chrome disconnected, will relaunch on next request");
    browser = null;
  });

  return browser;
}

// Pre-launch Chrome at startup so first request is fast
getBrowser().catch((err) =>
  console.error("[Browser] Failed to pre-launch:", err.message)
);

// ── Health Check ─────────────────────────────────────────────────────────────
app.get("/health", (_req, res) => res.json({ status: "UP" }));

// ── CodeChef Scraper ─────────────────────────────────────────────────────────
app.get("/codechef/:handle", async (req, res) => {
  if (!hasValidKey(req)) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  const { handle } = req.params;
  if (!VALID_HANDLE.test(handle)) {
    return res.status(400).json({ error: "Invalid handle" });
  }
  const slot = acquireScrapeSlot();
  if (!slot) {
    return res.status(503).json({ error: "Scraper is busy, try again shortly" });
  }
  await slot;
  console.log(`[CodeChef] Scraping profile: ${handle}`);

  let page;
  try {
    const b = await getBrowser();
    page = await b.newPage();

    // Mimic a real browser
    await page.setUserAgent(
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    );
    await page.setViewport({ width: 1280, height: 800 });

    // Block images, CSS, fonts to speed up loading
    await page.setRequestInterception(true);
    page.on("request", (req) => {
      const type = req.resourceType();
      if (["image", "stylesheet", "font", "media"].includes(type)) {
        req.abort();
      } else {
        req.continue();
      }
    });

    // Navigate to the CodeChef profile
    await page.goto(`https://www.codechef.com/users/${handle}`, {
      waitUntil: "domcontentloaded",
      timeout: 15000,
    });

    // Wait for the rating element to appear (max 10s)
    await page.waitForSelector(".rating-number, .user-details-container", {
      timeout: 10000,
    });

    // Give extra time for dynamic content
    await new Promise((r) => setTimeout(r, 2000));

    // Extract all stats from the page
    const stats = await page.evaluate(() => {
      const result = {};

      // Rating
      const ratingEl = document.querySelector(".rating-number");
      if (ratingEl) {
        const digits = ratingEl.textContent.replace(/[^0-9]/g, "");
        if (digits) result.rating = parseInt(digits);
      }

      // Stars — count <span> elements inside .rating-star
      const starsEl = document.querySelector(".rating-star");
      if (starsEl) {
        const spans = starsEl.querySelectorAll("span");
        if (spans.length > 0) {
          result.starCount = spans.length;
          result.stars = "★".repeat(spans.length);
        }
      }

      // Max Rating — look in <small> tags containing "Highest Rating"
      const smalls = document.querySelectorAll("small");
      for (const small of smalls) {
        if (small.textContent.includes("Highest Rating")) {
          const digits = small.textContent.replace(/[^0-9]/g, "");
          if (digits) result.maxRating = parseInt(digits);
          break;
        }
      }

      // Global Rank
      const rankEl = document.querySelector(".rating-ranks ul li a");
      if (rankEl) {
        result.globalRank = rankEl.textContent.trim();
      }

      // Problems Solved
      const headers = document.querySelectorAll("h3");
      for (const h3 of headers) {
        if (h3.textContent.includes("Total Problems Solved")) {
          const digits = h3.textContent.replace(/[^0-9]/g, "");
          if (digits) result.totalSolved = parseInt(digits);
          break;
        }
      }

      return result;
    });

    console.log(`[CodeChef] Result for ${handle}:`, JSON.stringify(stats));

    if (!stats.rating && !stats.totalSolved) {
      return res.status(404).json({ error: `No data found for ${handle}` });
    }

    res.json(stats);
  } catch (err) {
    console.error(`[CodeChef] Error scraping ${handle}:`, err.message);
    // Internal error details stay in the logs
    res.status(500).json({ error: "Scrape failed" });
  } finally {
    // Close only the page tab, NOT the browser
    if (page) {
      await page.close().catch(() => {});
    }
    releaseScrapeSlot();
  }
});

// ── Start Server ─────────────────────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`🔍 ShunyaLink Scraper running on port ${PORT}`);
});
