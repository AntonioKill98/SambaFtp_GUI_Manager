package org.antonio;

public class UserBean {
    private String username;
    private boolean sambaEnabled;
    private boolean ftpEnabled;

    public UserBean(String username, boolean sambaEnabled, boolean ftpEnabled) {
        this.username = username;
        this.sambaEnabled = sambaEnabled;
        this.ftpEnabled = ftpEnabled;
    }

    public String getUsername() {
        return username;
    }

    public boolean isSambaEnabled() {
        return sambaEnabled;
    }

    public void setSambaEnabled(boolean sambaEnabled) {
        this.sambaEnabled = sambaEnabled;
    }

    public boolean isFtpEnabled() {
        return ftpEnabled;
    }

    public void setFtpEnabled(boolean ftpEnabled) {
        this.ftpEnabled = ftpEnabled;
    }
}

