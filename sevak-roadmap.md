# SEVAK: The Complete Dual-Purpose Roadmap

This document serves as the master blueprint for the SEVAK project. It is split into two strict phases: ensuring a perfect grade for the college course ("The College Grade"), and transforming the project into a high-tier portfolio piece ("The MAANG Resume Pivot").

---

## 🏛️ Phase 1: The College Grade (Deadline: April 24)

*Goal: Follow the professor's strict instructions to get an 'A'. Build exactly what is asked in the syllabus.*

**Current Status:** ER Diagram and raw DDL script (the 21 `CREATE TABLE` entities) are COMPLETE.

### 1. Database Foundation (Immediate Next Steps)
*   [ ] **Create `data.sql` (Data Seeding script)** 
    *   Write raw `INSERT INTO` statements for all 21 tables.
    *   Generate realistic fake data (e.g., 5-10 rows per table). This is required to test queries.
    *   *Note: Foreign keys must be respected during insertion (insert parent rows before child rows).*
*   [ ] **Normalization Proof Document**
    *   Write a short document proving that your 21 tables are in 3rd Normal Form (3NF) or Boyce-Codd Normal Form (BCNF). Ensure you understand the functional dependencies for the Viva.

### 2. The Course Requirements
*   [ ] **Create `queries.sql` (The 10-15 Complex Queries)**
    *   Write raw SQL `SELECT` queries that use `JOIN`, `GROUP BY`, `HAVING`, and nested subqueries.
    *   *Example:* Find the highest-paid service providers in each region.
*   [ ] **Create `procedures_triggers.sql` (Database Logic)**
    *   **Trigger:** Write a PL/pgSQL trigger to fire after a new review/rating is inserted, automatically updating the provider's `average_rating`.
    *   **Procedure:** Write a PL/pgSQL stored procedure to execute a complex event (e.g., assigning a provider to a booking).
*   [ ] **The Java Core Application (`Main.java`)**
    *   **Architecture:** `public static void main(String[] args)` ONLY. **NO GUI. NO SPRING BOOT.**
    *   **Technology:** Pure **JDBC** (`java.sql.Connection`, `PreparedStatement`, `ResultSet`). No ORM.
    *   **Functionality:** Connect to the PostgreSQL database, execute the complex queries from `queries.sql`, and format the outputs to be printed nicely to the console.

### 3. Submission & Viva (April 20-24)
*   [ ] Submit printed ERDs, normalization proofs, SQL scripts, and Java JDBC code.
*   [ ] Defend the database normalization choices and raw SQL queries in the Viva.

---

## 🚀 Phase 2: The MAANG Resume Pivot (Late April – May)

*Goal: Keep the hardcore database engineering, discard the college "console app" wrapper, and add a modern backend + minimal UI stack.*

### 1. The Refactor
*   **Keep the Database:** The 21-table schema is the crown jewel. Do not touch the DDL.
*   **Discard the Console App:** Delete the pure JDBC Java Main app.
*   **Initialize Spring Boot:** Create a new Spring Boot Web project. 

### 2. Spring Boot Implementation
*   **Data Access:** Connect Spring Boot to PostgreSQL. You can use Spring Data JPA, MyBatis, or Spring JDBC. (MyBatis or Spring JDBC is often preferred for high-complexity, raw SQL schemas).
*   **Build REST APIs:** Rewrite the console logic into HTTP endpoints (e.g., `POST /api/bookings`, `GET /api/providers/top`).
*   **Hardcore Engineering additions:**
    *   **Transaction Management:** Use `@Transactional` to ensure multi-table operations (like booking creation + payment processing) are ACID compliant.
    *   **Concurrency / Row-Level Locking:** Implement `SELECT FOR UPDATE` when verifying provider availability to prevent double-booking race conditions.
    *   **Spatial Searching:** Implement **PostGIS** in PostgreSQL. Add queries that find "providers within a 10km radius."

### 3. The Minimal Frontend (The "Dashboard Masterpiece")
*   **Do not build a 50-page website.** Build **ONE high-quality Dashboard page.**
*   **Tech Stack:** React, Next.js, Tailwind CSS (reusing skills from ShunyaLink). Use Shadcn UI or Aceternity UI for premium components.
*   **The 3-Tab Architecture:**
    1.  **Customer Tab:** A map and search bar to find and "Book" service providers (triggers the PostGIS and Booking APIs).
    2.  **Provider Tab:** A simple table showing incoming jobs. Switching from Customer -> Provider instantly proves your database locks and concurrency handling.
    3.  **Admin/Engineering Tab:** Shows "Under the hood" metrics. Graphs of top categories and the DB health stats (proves your complex `GROUP BY` and `JOIN` SQL queries).

### 4. Zero-Cost Cloud Deployment
*   **Database:** Migrate local PostgreSQL to **Supabase** or **Neon.tech** (Free Tier).
*   **Backend:** Dockerize the Spring Boot application and push it to **Render.com** or **Railway.app** (Free Tier).
*   **Frontend:** Deploy the Next.js/React UI to **Vercel** or **Netlify** (Free Tier).

### 5. Final Resume Bullet Points
When finished, your resume entry should look exactly like this:

> **SEVAK — Full-Stack Service Marketplace** *(React, Spring Boot, PostgreSQL, PostGIS)*  [Live Demo] | [GitHub]
> - Designed a 21-entity relational database natively in PostgreSQL in 3NF/BCNF.
> - Handled high-concurrency race conditions during bookings using row-level locking.
> - Guaranteed ACID compliance across pricing, coupon application, and payment state using transactional domains.
> - Built spatial search for discovering service providers within a 10km radius using PostGIS.
