# Frontend Architecture & UI/UX Design System

> **Note (2026-10-17):** parts of this page predate the current implementation. Where it conflicts with `README.md`, `docs/RAG_PIPELINE.md`, `docs/OPERATIONS.md` or `docs/LIMITATIONS.md`, those are correct. In particular: retrieval is keyword matching (not semantic), eligibility is evaluated in Java (`EligibilityEvaluator`), status is computed from verified dates, and the chat model is `openai/gpt-oss-120b` by default (`GROQ_MODEL`).

## 1. Architectural Philosophy

The frontend is engineered as a modern, consumer-facing Single Page Application (SPA) using **React 18**, **Vite**, **Tailwind CSS**, and **Framer Motion**.

### Core UX Tenets:
1. **Student-First, Zero Developer Clutter**: All developer artifacts (e.g. `pgvector`, `Spring Boot Live`, `Swagger UI`, `Jsoup`, dimension labels) have been excised. The user interacts with a clean scholarship portal.
2. **Automated Hidden RAG Triggers**: Rather than forcing students to construct complex prompts in a text box, filter changes (income slider, academic marks, domicile state, social category, gender, course) automatically trigger the backend RAG pipeline behind the scenes with a hidden standard query.
3. **Responsive Multi-Column Dashboard Grid**: Results are rendered using a responsive CSS grid (`grid-cols-1 lg:grid-cols-2 2xl:grid-cols-3 gap-6`), ensuring uniform card heights and clean visual balance on displays ranging from mobile screens to 4K monitors.
4. **Dark Cyber Aesthetic**: Built using Pure Black (`#000000`), Dark Charcoal (`#18181b`), and Electric Neon Orange (`#f97316` / `#ea580c`).

---

## 2. Directory Structure

```text
frontend/
├── public/                 # Static assets (favicons, manifest)
├── src/
│   ├── components/
│   │   ├── EligibilityView.jsx      # Core Matcher dashboard with filters & dynamic grid
│   │   ├── ExploreSchemesView.jsx   # Searchable scheme catalog
│   │   ├── Navbar.jsx               # Consumer brand header with state selector
│   │   └── Footer.jsx               # Clean footer with state portal links
│   ├── context/
│   │   └── RegionContext.jsx        # Global state context for Pan-India states
│   ├── pages/
│   │   ├── MatcherPage.jsx          # Route: /
│   │   ├── ChatPage.jsx             # Route: /chat (AI Scholarship Chat)
│   │   └── ExplorePage.jsx          # Route: /explore (Scheme Directory)
│   ├── App.jsx                      # App root, animated route transitions
│   ├── main.jsx                     # Vite DOM entrypoint
│   └── index.css                    # Tailwind utility and font configurations
├── nginx.conf                       # Production Nginx reverse-proxy configuration
├── Dockerfile                       # Multi-stage production build container
├── package.json                     # Dependencies & scripts
└── vite.config.js                   # Vite configuration with dev proxy
```

---

## 3. Key Components & Implementation

### A. `EligibilityView.jsx`: Automated RAG & Equal-Height Grid

#### Hidden Default RAG Query:
When a student adjusts any profile slider, dropdown, or clicks a quick preset, the application automatically dispatches the following hidden prompt to the recommendation engine:
```javascript
const DEFAULT_RAG_PROMPT = 
  "Evaluate eligibility based strictly on the provided profile parameters and detail the financial benefits.";
```

#### Responsive Grid Container:
```jsx
<motion.div
  initial="hidden"
  animate="visible"
  variants={{
    hidden: { opacity: 0 },
    visible: { opacity: 1, transition: { staggerChildren: 0.06 } }
  }}
  className="grid grid-cols-1 lg:grid-cols-2 2xl:grid-cols-3 gap-6"
>
  {/* Uniform Equal-Height Cards */}
</motion.div>
```

#### Equal-Height Card Architecture:
Every card in the grid utilizes `h-full flex flex-col justify-between` to ensure that regardless of the length of the scholarship title or eligibility explanation, all cards in a row align:
- **Card Body (`flex-1 flex flex-col space-y-4`)**: Contains the provider, scheme title, "Eligible" badge, financial benefits pill, grounded reasoning box, and expandable document checklist.
- **Card Footer (`mt-4 pt-3 border-t border-zinc-800`)**: Anchored to the bottom, displaying the application deadline and the official portal "Apply" link.

---

### B. `RegionContext.jsx`: Global State Synchronization

The application provides continuous state synchronization across views:
- The top navigation bar contains a prominent **State / Domicile selector** (supporting All India, Maharashtra, Karnataka, Uttar Pradesh, Gujarat, Bihar, Andhra Pradesh, etc.).
- When a user changes their state in the navigation bar or within the sidebar filters, `RegionContext` immediately broadcasts the change, updates local storage, and triggers a real-time re-evaluation.

---

### C. Nginx Reverse Proxy Configuration (`nginx.conf`)

In production (inside the Docker container), Nginx serves the compiled Vite assets and transparently proxies `/api/` requests to the Spring Boot backend:

```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html index.htm;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
    }
}
```
