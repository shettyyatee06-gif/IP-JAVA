AICTE ACTIVITY TRACKER — CAMPUSLOOP REDESIGN
===========================================

This is a simple college-demo project with a student-friendly, colourful portal.
It keeps the Java backend and includes the interactive Leaflet map feature.

FILES
- index.html        Main login page and student/faculty dashboards
- style.css         Responsive colourful UI
- script.js         Dashboard logic, event records, location, QR, announcements
- aicteactivity.java Java demo backend for login and basic server check-in APIs

DEMO LOGIN
- Student: aditi / demo123
- Student: student2 / demo123
- Faculty: faculty / demo123

RUN IT
1. Extract the ZIP into a folder.
2. Open Command Prompt in that folder.
3. Compile the Java backend:
      javac aicteactivity.java
4. Start it:
      java aicteactivity
5. Open index.html in a browser. VS Code Live Server is recommended.
6. Sign in using one of the demo accounts above.

If the backend is not running, the frontend has a simple local demo login fallback.
For location and camera access, use localhost/Live Server or HTTPS; browsers usually
block these features on ordinary file:// pages. Internet access is needed for map tiles.

FEATURES
- Separate student and faculty navigation and dashboard views
- Colourful progress, point and activity summary cards
- Student activity records and faculty student directory
- Faculty can create events with date, duration, QR/event code, venue, coordinates and radius
- Faculty can approve or reject student submissions
- Events appear in student announcements, activity cards and map pins
- Student location check compares GPS distance with the event's allowed radius
- QR scanning where the browser supports BarcodeDetector; manual code entry is included
- 1 point for an event lasting 3–4 hours, added to totals only after faculty approval
- Simple rule-based AI-style activity suggestions
- Interactive event maps; faculty can click or drag a marker to choose venue coordinates

DEMO LIMITATIONS
- The redesigned event and activity records use browser localStorage so the demo stays
  simple. Records are local to that browser/device and are not shared between devices.
- The Java backend handles demo authentication and its own basic check-in API, but the
  redesigned event/approval workflow is a frontend demonstration, not a production system.
- Browser-side GPS checks can be spoofed and must not be used as secure official attendance.
- Demo credentials are hardcoded. Do not expose this project publicly or use real student data.
- The AI-style suggestions are simple rules, not a connected AI service.
- QR scanning depends on browser support. If unavailable, type the event code shared by faculty.
