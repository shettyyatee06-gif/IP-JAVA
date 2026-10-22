import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class aicteactivity {
    private static final int PORT = 8080;
    private static final int BASE_POINTS = 75;
    private static final int TARGET = 100;
    private static final int POINTS_PER_CHECKIN = 5;
    private static final Path DATA_FILE = Path.of("aicte-data.properties");

    // Demo credentials only. Replace with hashed passwords and a proper user store for real use.
    private static final ConcurrentMap<String, String> DEMO_USERS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> TOKENS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, StudentData> STUDENTS = new ConcurrentHashMap<>();
    private static final Object SAVE_LOCK = new Object();

    static {
        DEMO_USERS.put("aditi", "demo123");
        DEMO_USERS.put("student2", "demo123");
        DEMO_USERS.put("faculty", "demo123");
    }

    private static final class StudentData {
        private int checkins;
        private final List<String> timestamps = new ArrayList<>();
        private StudentData(int checkins) { this.checkins = checkins; }
    }

    public static void main(String[] args) throws IOException {
        loadData();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/login", aicteactivity::handleLogin);
        server.createContext("/api/logout", aicteactivity::handleLogout);
        server.createContext("/api/checkin", aicteactivity::handleCheckin);
        server.createContext("/api/status", aicteactivity::handleStatus);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("AICTE Activity Tracker backend running at http://localhost:" + PORT);
        System.out.println("Demo login: aditi / demo123 (or student2 / demo123)");
        System.out.println("Data file: " + DATA_FILE.toAbsolutePath());
    }

    private static void handleLogin(HttpExchange ex) throws IOException {
        addCors(ex);
        if (preflightOrWrongMethod(ex, "POST")) return;
        try {
            String body = readBody(ex);
            String username = jsonString(body, "username");
            String password = jsonString(body, "password");
            if (username == null || password == null ||
                !password.equals(DEMO_USERS.get(username.toLowerCase()))) {
                send(ex, 401, "{\"message\":\"Invalid username or password.\"}");
                return;
            }
            username = username.toLowerCase();
            STUDENTS.computeIfAbsent(username, k -> new StudentData(0));
            String token = UUID.randomUUID().toString();
            TOKENS.put(token, username);
            send(ex, 200, "{\"status\":\"success\",\"student\":\"" + escapeJson(displayName(username))
                    + "\",\"token\":\"" + token + "\"}");
        } finally { ex.close(); }
    }

    private static void handleLogout(HttpExchange ex) throws IOException {
        addCors(ex);
        if (preflightOrWrongMethod(ex, "POST")) return;
        try {
            String token = bearerToken(ex);
            if (token != null) TOKENS.remove(token);
            send(ex, 200, "{\"status\":\"success\",\"message\":\"Logged out.\"}");
        } finally { ex.close(); }
    }

    private static void handleCheckin(HttpExchange ex) throws IOException {
        addCors(ex);
        if (preflightOrWrongMethod(ex, "POST")) return;
        try {
            String username = authenticatedUser(ex);
            if (username == null) {
                send(ex, 401, "{\"message\":\"Please log in to check in.\"}");
                return;
            }
            StudentData data = STUDENTS.computeIfAbsent(username, k -> new StudentData(0));
            synchronized (data) {
                if (BASE_POINTS + data.checkins * POINTS_PER_CHECKIN >= TARGET) {
                    send(ex, 409, "{\"message\":\"You have already reached the points target.\"}");
                    return;
                }
                LocalDate today = LocalDate.now();
                for (String stamp : data.timestamps) {
                    try {
                        if (Instant.parse(stamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().equals(today)) {
                            send(ex, 409, "{\"message\":\"You have already checked in today. Try again tomorrow.\"}");
                            return;
                        }
                    } catch (Exception ignored) { }
                }
                data.timestamps.add(Instant.now().toString());
                data.checkins++;
                saveData();
                send(ex, 200, "{\"status\":\"success\",\"message\":\"Check-in recorded successfully.\"}");
            }
        } finally { ex.close(); }
    }

    private static void handleStatus(HttpExchange ex) throws IOException {
        addCors(ex);
        if (preflightOrWrongMethod(ex, "GET")) return;
        try {
            String username = authenticatedUser(ex);
            if (username == null) {
                send(ex, 401, "{\"message\":\"Please log in again.\"}");
                return;
            }
            StudentData data = STUDENTS.computeIfAbsent(username, k -> new StudentData(0));
            synchronized (data) {
                int points = Math.min(TARGET, BASE_POINTS + data.checkins * POINTS_PER_CHECKIN);
                StringBuilder recent = new StringBuilder("[");
                int start = Math.max(0, data.timestamps.size() - 5);
                for (int i = data.timestamps.size() - 1, shown = 0; i >= start; i--, shown++) {
                    if (shown > 0) recent.append(',');
                    recent.append('"').append(escapeJson(data.timestamps.get(i))).append('"');
                }
                recent.append(']');
                send(ex, 200, "{\"student\":\"" + escapeJson(displayName(username))
                        + "\",\"points\":" + points + ",\"target\":" + TARGET
                        + ",\"checkins\":" + data.checkins
                        + ",\"targetReached\":" + (points >= TARGET)
                        + ",\"recent\":" + recent + "}");
            }
        } finally { ex.close(); }
    }

    private static boolean preflightOrWrongMethod(HttpExchange ex, String expected) throws IOException {
        String method = ex.getRequestMethod().toUpperCase();
        if ("OPTIONS".equals(method)) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        if (!expected.equals(method)) {
            send(ex, 405, "{\"message\":\"Method not allowed.\"}");
            ex.close();
            return true;
        }
        return false;
    }

    private static String authenticatedUser(HttpExchange ex) {
        String token = bearerToken(ex);
        return token == null ? null : TOKENS.get(token);
    }

    private static String bearerToken(HttpExchange ex) {
        String value = ex.getRequestHeaders().getFirst("Authorization");
        if (value == null || !value.startsWith("Bearer ")) return null;
        return value.substring(7).trim();
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    // Small JSON-string field reader sufficient for this demo's simple login payload.
    private static String jsonString(String json, String field) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) return null;
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r");
    }

    private static String displayName(String username) {
        if ("aditi".equals(username)) return "Aditi Singh";
        if ("student2".equals(username)) return "Student Two";
        return username;
    }

    private static void loadData() throws IOException {
        if (!Files.exists(DATA_FILE)) return;
        Properties props = new Properties();
        try (var in = Files.newInputStream(DATA_FILE)) { props.load(in); }
        for (String username : DEMO_USERS.keySet()) {
            int count = parseInt(props.getProperty(username + ".count"), 0);
            StudentData data = new StudentData(Math.max(0, count));
            String encoded = props.getProperty(username + ".timestamps", "");
            if (!encoded.isEmpty()) {
                for (String item : encoded.split(",")) {
                    try {
                        String decoded = new String(Base64.getDecoder().decode(item), StandardCharsets.UTF_8);
                        if (!decoded.isBlank()) data.timestamps.add(decoded);
                    } catch (IllegalArgumentException ignored) { }
                }
            }
            data.checkins = data.timestamps.size();
            STUDENTS.put(username, data);
        }
    }

    private static void saveData() throws IOException {
        synchronized (SAVE_LOCK) {
            Properties props = new Properties();
            for (var entry : STUDENTS.entrySet()) {
                StudentData data = entry.getValue();
                synchronized (data) {
                    props.setProperty(entry.getKey() + ".count", Integer.toString(data.checkins));
                    List<String> encoded = new ArrayList<>();
                    for (String stamp : data.timestamps) {
                        encoded.add(Base64.getEncoder().encodeToString(stamp.getBytes(StandardCharsets.UTF_8)));
                    }
                    props.setProperty(entry.getKey() + ".timestamps", String.join(",", encoded));
                }
            }
            Path temp = DATA_FILE.resolveSibling(DATA_FILE.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(temp)) { props.store(out, "AICTE Activity Tracker data"); }
            Files.move(temp, DATA_FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ex) { return fallback; }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void addCors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void send(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}
