package org.example.supabase;

/**
 * SessionManager — singleton holding the current user's in-memory session.
 * Populated after login. Cleared on logout or account delete.
 */
public class SessionManager {

    private static SessionManager instance;

    // Core auth
    private String userId;
    private String accessToken;
    private String refreshToken;

    // Profile
    private String userName;
    private String userEmail;
    private String userTrack;
    private String avatarUrl;

    // Stats (updated after each question)
    private int xp;
    private int streak;
    private int level;

    // UI preferences (in-memory only, no DB)
    private String theme = "light"; // "light" | "dark" | "system"

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) instance = new SessionManager();
        return instance;
    }

    // ════════════════════════════════════════════════════════
    //  Set full session after login
    // ════════════════════════════════════════════════════════
    public void setSession(String userId, String accessToken,
                           String refreshToken, String userName,
                           String track, int xp, int streak, int level) {
        this.userId       = userId;
        this.accessToken  = accessToken;
        this.refreshToken = refreshToken;
        this.userName     = userName;
        this.userTrack    = track;
        this.xp           = xp;
        this.streak       = streak;
        this.level        = level;
        SupabaseClient.getInstance().setAccessToken(accessToken);
    }

    // ════════════════════════════════════════════════════════
    //  Clear on logout / account delete
    // ════════════════════════════════════════════════════════
    public void clearSession() {
        userId = null; accessToken = null; refreshToken = null;
        userName = null; userEmail = null; userTrack = null; avatarUrl = null;
        xp = 0; streak = 0; level = 1; theme = "light";
        SupabaseClient.getInstance().clearSession();
    }

    /** Alias used by SettingsController */
    public void clear() { clearSession(); }

    public boolean isLoggedIn() { return userId != null; }

    // ════════════════════════════════════════════════════════
    //  Getters
    // ════════════════════════════════════════════════════════
    public String getUserId()      { return userId;       }
    public String getAccessToken() { return accessToken;  }
    public String getRefreshToken(){ return refreshToken; }
    public String getUserName()    { return userName;     }
    public String getUserEmail()   { return userEmail;    }
    public String getUserTrack()   { return userTrack;    }
    public String getAvatarUrl()   { return avatarUrl;    }
    public String getTheme()       { return theme;        }
    public int    getXp()          { return xp;           }
    public int    getStreak()      { return streak;       }
    public int    getLevel()       { return level;        }

    // ════════════════════════════════════════════════════════
    //  Setters (live updates from any controller)
    // ════════════════════════════════════════════════════════
    public void setXp(int v)          { xp = v;          }
    public void setStreak(int v)      { streak = v;      }
    public void setLevel(int v)       { level = v;       }
    public void setAvatarUrl(String v){ avatarUrl = v;   }
    public void setUserName(String v) { userName = v;    }
    public void setUserEmail(String v){ userEmail = v;   }
    public void setTheme(String v)    { theme = v;       }

    // ════════════════════════════════════════════════════════
    //  Derived helpers
    // ════════════════════════════════════════════════════════

    /** Two-letter initials from name, e.g. "Alex Johnson" → "AJ" */
    public String getInitials() {
        if (userName == null || userName.isBlank()) return "?";
        String[] parts = userName.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        return userName.substring(0, Math.min(2, userName.length())).toUpperCase();
    }

    /** Short student ID badge: "HSC-" + first 8 chars of userId uppercase */
    public String getStudentId() {
        if (userId == null) return "HSC-00000000";
        return "HSC-" + userId.replace("-", "").substring(0, 8).toUpperCase();
    }
}