const express = require("express");
const puppeteer = require("puppeteer");

const app = express();
const PORT = 3001;

// ── Health Check ─────────────────────────────────────────────────────────────
app.get("/health", (_req, res) => res.json({ status: "UP" }));

// ── CodeChef Scraper ─────────────────────────────────────────────────────────
app.get("/codechef/:handle", async (req, res) => {
  const { handle } = req.params;
  console.log(`[CodeChef] Scraping profile: ${handle}`);

  let browser;
  try {
    browser = await puppeteer.launch({
      headless: "new",
      executablePath: process.env.PUPPETEER_EXECUTABLE_PATH || puppeteer.executablePath(),
      args: [
        "--no-sandbox",
        "--disable-setuid-sandbox",
        "--disable-dev-shm-usage",    // Use /tmp instead of /dev/shm (Docker fix)
        "--disable-gpu",
        "--disable-extensions",
        "--disable-background-networking",
        "--single-process",           // Reduce memory in Docker
      ],
    });

    const page = await browser.newPage();

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
    res.status(500).json({ error: err.message });
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
  }
});

// ── Start Server ─────────────────────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`🔍 ShunyaLink Scraper running on port ${PORT}`);
});
