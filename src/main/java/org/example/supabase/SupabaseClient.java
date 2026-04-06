package org.example.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class SupabaseClient {

    private static SupabaseClient instance;

    private final String       baseUrl;
    private final String       anonKey;
    private final HttpClient   http;
    private final ObjectMapper mapper = new ObjectMapper();

    private final ExecutorService pool =
            Executors.newFixedThreadPool(6, r -> {
                Thread t = new Thread(r, "supabase-fetch");
                t.setDaemon(true);
                return t;
            });

    private SupabaseClient() {
        Properties props = loadConfig();
        this.baseUrl = props.getProperty("SUPABASE_URL", "");
        this.anonKey = props.getProperty("SUPABASE_ANON_KEY", "");
        this.http    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(pool)
                .build();
    }

    public static synchronized SupabaseClient getInstance() {
        if (instance == null) instance = new SupabaseClient();
        return instance;
    }

    private Properties loadConfig() {
        Properties p = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in != null) p.load(in);
        } catch (IOException e) {
            System.err.println("[SupabaseClient] Config load error: " + e.getMessage());
        }
        return p;
    }

    // ── Session ───────────────────────────────────────────────
    private String accessToken;
    public void   setAccessToken(String t) { this.accessToken = t; }
    public String getAccessToken()         { return accessToken; }
    public boolean isLoggedIn()            { return accessToken != null; }
    public void   clearSession()           { this.accessToken = null; }

    private String authHeader() {
        return accessToken != null ? "Bearer " + accessToken : "Bearer " + anonKey;
    }

    // ════════════════════════════════════════════════════════════
    //  AUTH
    // ════════════════════════════════════════════════════════════
    public JsonNode signIn(String email, String password)
            throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("email", email);
        body.put("password", password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/v1/token?grant_type=password"))
                .header("Content-Type", "application/json")
                .header("apikey", anonKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode json = mapper.readTree(res.body());
        if (res.statusCode() == 200 && json.has("access_token"))
            setAccessToken(json.get("access_token").asText());
        return json;
    }

    public JsonNode signUp(String email, String password, String name)
            throws IOException, InterruptedException {
        ObjectNode meta = mapper.createObjectNode();
        meta.put("name", name);
        ObjectNode body = mapper.createObjectNode();
        body.put("email", email); body.put("password", password);
        body.set("data", meta);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/v1/signup"))
                .header("Content-Type", "application/json")
                .header("apikey", anonKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return mapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
    }

    public void signOut() throws IOException, InterruptedException {
        http.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/v1/logout"))
                .header("Authorization", authHeader())
                .header("apikey", anonKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.discarding());
        clearSession();
    }

    // ════════════════════════════════════════════════════════════
    //  PARALLEL BATCH FETCH
    //  Fires all queries simultaneously, returns when all done.
    //
    //  Usage:
    //    var client = SupabaseClient.getInstance();
    //    var results = client.fetchAll(Map.of(
    //        "subjects",  client.from("subjects").select("id,name"),
    //        "progress",  client.from("user_progress").select("*").eq("user_id", uid),
    //        "goals",     client.from("daily_goals").select("*").eq("user_id", uid)
    //    ));
    //    JsonNode subjects = results.get("subjects");
    //    JsonNode progress = results.get("progress");
    // ════════════════════════════════════════════════════════════
    public Map<String, JsonNode> fetchAll(Map<String, QueryBuilder> queries)
            throws InterruptedException {

        Map<String, CompletableFuture<JsonNode>> futures = new LinkedHashMap<>();
        for (var entry : queries.entrySet()) {
            String       key = entry.getKey();
            QueryBuilder qb  = entry.getValue();
            futures.put(key, CompletableFuture.supplyAsync(() -> {
                try { return qb.execute(); }
                catch (Exception e) {
                    System.err.println("[fetchAll:" + key + "] " + e.getMessage());
                    return mapper.createArrayNode();
                }
            }, pool));
        }

        // Wait for all
        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();

        Map<String, JsonNode> results = new LinkedHashMap<>();
        for (var entry : futures.entrySet()) {
            try { results.put(entry.getKey(), entry.getValue().get()); }
            catch (ExecutionException e) {
                results.put(entry.getKey(), mapper.createArrayNode());
            }
        }
        return results;
    }

    // ════════════════════════════════════════════════════════════
    //  QUERY BUILDER
    // ════════════════════════════════════════════════════════════
    public QueryBuilder from(String table) { return new QueryBuilder(table); }

    public class QueryBuilder {
        private final String table;
        private String selectCols = "*";
        private final StringBuilder filters = new StringBuilder();
        private String  orderCol;
        private boolean orderAsc = true;
        private Integer limitVal;

        QueryBuilder(String t) { this.table = t; }

        public QueryBuilder select(String cols) { this.selectCols = cols; return this; }
        public QueryBuilder eq(String col, String val) {
            filters.append("&").append(col).append("=eq.").append(enc(val)); return this; }
        public QueryBuilder eq(String col, int val) {
            filters.append("&").append(col).append("=eq.").append(val); return this; }
        public QueryBuilder neq(String col, String val) {
            filters.append("&").append(col).append("=neq.").append(enc(val)); return this; }
        public QueryBuilder gte(String col, String val) {
            filters.append("&").append(col).append("=gte.").append(enc(val)); return this; }
        public QueryBuilder lte(String col, String val) {
            filters.append("&").append(col).append("=lte.").append(enc(val)); return this; }
        public QueryBuilder ilike(String col, String pattern) {
            filters.append("&").append(col).append("=ilike.").append(enc(pattern)); return this; }
        public QueryBuilder order(String col, boolean asc) {
            this.orderCol = col; this.orderAsc = asc; return this; }
        public QueryBuilder limit(int n) { this.limitVal = n; return this; }

        public JsonNode execute() throws IOException, InterruptedException {
            StringBuilder url = new StringBuilder(baseUrl)
                    .append("/rest/v1/").append(table)
                    .append("?select=").append(selectCols)
                    .append(filters);
            if (orderCol != null)
                url.append("&order=").append(orderCol).append(".").append(orderAsc ? "asc" : "desc");
            if (limitVal != null)
                url.append("&limit=").append(limitVal);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .header("Authorization", authHeader())
                    .header("apikey", anonKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();

            return mapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        }

        public JsonNode insert(String body) throws IOException, InterruptedException {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/v1/" + table))
                    .header("Authorization", authHeader())
                    .header("apikey", anonKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15)).build();
            return mapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        }

        public JsonNode upsert(String body) throws IOException, InterruptedException {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/v1/" + table))
                    .header("Authorization", authHeader())
                    .header("apikey", anonKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation,resolution=merge-duplicates")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15)).build();
            return mapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        }

        public JsonNode update(String body) throws IOException, InterruptedException {
            String fs  = filters.toString().replaceFirst("^&", "");
            String url = baseUrl + "/rest/v1/" + table + (fs.isBlank() ? "" : "?" + fs);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", authHeader())
                    .header("apikey", anonKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15)).build();
            return mapper.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body());
        }

        public void delete() throws IOException, InterruptedException {
            String fs  = filters.toString().replaceFirst("^&", "");
            String url = baseUrl + "/rest/v1/" + table + (fs.isBlank() ? "" : "?" + fs);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", authHeader())
                    .header("apikey", anonKey)
                    .method("DELETE", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(15)).build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        }

        private String enc(String s) {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
        }
    }
}