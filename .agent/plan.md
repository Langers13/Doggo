# Project Plan

Build the Doggo app as per the approved project brief. 
Include:
1. Room Entity `HouseSitJob` (id, suburb, state, imageUrl, description, animals, lat, lng, startDate, endDate, isFavorited, isArchived, dateScraped).
2. Room DAO with methods for filtering and purging expired jobs.
3. AHSScraper using Ktor (GET session → search loop with searchid, x-requested-with, User-Agent, Referer).
4. GeminiParser using Google AI SDK to parse HTML fragments.
5. Bottom navigation (List, Map).
6. List screen with filters (Favorite, Archived, Date-range) and manual refresh.
7. Map screen with Google Maps and clustering.
8. Ensure modular design for future scrapers.
9. Handle Google Maps API key and Gemini API key.
10. Full Edge-to-Edge and Material 3 design.
11. Adaptive App Icon.

## Project Brief

# Project Brief: Doggo

Doggo is a personal house-sitting aggregator designed to simplify finding house-sitting opportunities in NSW and QLD. By combining traditional web scraping with the power of Gemini AI, Doggo transforms raw data from "Aussie House Sitters" into a structured, searchable, and highly visual experience.

### Features
* **AI-Driven Scraping & Parsing**: Utilizes Ktor and Jsoup to fetch data, which is then processed by Gemini 1.5 Flash to convert messy HTML into clean, structured `HouseSitJob` entities.
* **Hybrid Map & List Views**: Browse house-sitting jobs through an interactive Google Map with marker clustering or a detailed, filterable list.
* **Job Management & Filtering**: Easily filter jobs by state (NSW/QLD), date ranges, or "Favorite" status, and archive listings to keep your feed clean.
* **Smart Refresh & Maintenance**: A manual refresh mechanism that fetches the latest jobs while automatically purging expired listings from the local database.

### High-Level Tech Stack
* **Language**: Kotlin
* **UI Framework**: Jetpack Compose (Material 3)
* **Navigation**: Jetpack Navigation 3 (State-driven)
* **Adaptive Strategy**: Compose Material Adaptive library
* **Persistence**: Room Database (Entity: `HouseSitJob`)
* **Networking & Scraping**: Ktor (HTTP Client) and Jsoup (HTML Parsing)
* **AI Integration**: Google AI SDK (Gemini 1.5 Flash)
* **Maps**: Google Maps SDK for Android with Clustering Utility
* **Concurrency**: Kotlin Coroutines & Flow

## Implementation Steps
**Total Duration:** 1h 8m 42s

### Task_1_Setup_and_Persistence: Initialize project dependencies in libs.versions.toml and build.gradle.kts. Integrate Google Maps and Gemini API keys. Implement Room database with HouseSitJob entity and DAO for filtering and purging.
- **Status:** COMPLETED
- **Updates:** Initialized project dependencies for Ktor, Jsoup, Room, Gemini, and Google Maps. Integrated API keys via local.properties and BuildConfig. Implemented Room database with HouseSitJob entity, DAO (with filtering and purging), and TypeConverters. Project builds successfully.
- **Acceptance Criteria:**
  - Dependencies for Ktor, Jsoup, Room, Gemini, and Maps are added
  - HouseSitJob Room Entity and DAO are implemented
  - API Keys are securely integrated and accessible
  - Project builds successfully
- **Duration:** 20m 52s

### Task_2_Scraper_and_AI_Engine: Develop the AHSScraper using Ktor to fetch house-sitting listings. Implement the GeminiParser using Google AI SDK to transform raw HTML fragments into structured HouseSitJob entities.
- **Status:** COMPLETED
- **Updates:** Developed AHSScraper using Ktor and Jsoup. Implemented GeminiParser using Google AI SDK (Gemini 1.5 Flash). Integrated scraping logic with Room database and implemented Smart Refresh (purging expired jobs). The agent also seems to have implemented parts of the UI and navigation.
- **Acceptance Criteria:**
  - AHSScraper fetches HTML data from the source
  - GeminiParser correctly parses HTML into HouseSitJob objects
  - Scraped jobs are successfully saved to the local Room database
  - Manual refresh logic is implemented
- **Duration:** 27m 37s

### Task_3_UI_and_Navigation: Implement Jetpack Compose UI with Bottom Navigation (List and Map). Build the List screen with filtering (State, Favorites, Date) and the Map screen using Google Maps with marker clustering.
- **Status:** COMPLETED
- **Updates:** Implemented Bottom Navigation (List, Map). Created List screen with M3 cards, filtering (Favorites, Archived, State, Date-Range), and a FAB for manual refresh. Implemented Map screen with Google Maps, marker clustering, and info cards. Applied Material 3 styling and Edge-to-Edge display.
- **Acceptance Criteria:**
  - Bottom navigation switches between List and Map screens
  - List screen displays jobs with working filters and archive/favorite actions
  - Map screen displays jobs using marker clustering
  - UI follows Material 3 guidelines
- **Duration:** 10m 33s

### Task_4_Final_Polish_and_Verification: Apply final Material 3 theme refinements, implement full Edge-to-Edge display, and create an Adaptive App Icon. Conduct a final run to ensure stability and verify all requirements are met.
- **Status:** IN_PROGRESS
- **Updates:** App is crashing on launch due to a NoClassDefFoundError related to Ktor version mismatch. The Gemini SDK (generativeai:0.9.0) is likely incompatible with Ktor 3.x. Downgrading dependencies to stable versions (Ktor 2.3.12, AGP 8.7.3, Kotlin 2.1.0) as recommended by the critic agent.
- **Acceptance Criteria:**
  - Full Edge-to-Edge display is implemented
  - Adaptive app icon is created and functional
  - App does not crash and meets all project brief requirements
  - Build pass and all existing tests pass
- **Duration:** 9m 40s
- **StartTime:** 2026-04-28 17:34:42 AEST

