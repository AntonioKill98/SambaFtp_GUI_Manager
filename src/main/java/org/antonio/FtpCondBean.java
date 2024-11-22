package org.antonio;

public class FtpCondBean {
    private String username;
    private String shareName; // Nome della condivisione (es. nome logico del bind mount)
    private String path;      // Percorso della condivisione

    public FtpCondBean(String username, String shareName, String path) {
        this.username = username;
        this.shareName = shareName;
        this.path = path;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getShareName() {
        return shareName;
    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}

