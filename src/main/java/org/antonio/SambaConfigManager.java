package org.antonio;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SambaConfigManager {
    private String configPath;
    private ArrayList<String[]> globalSettings;
    private ArrayList<String[]> homeSettings;
    private ArrayList<SmbCondBean> shares;

    public SambaConfigManager(String configPath) throws IOException {
        this.configPath = configPath;
        this.globalSettings = new ArrayList<>();
        this.homeSettings = new ArrayList<>();
        this.shares = new ArrayList<>();
        if (!Files.exists(Paths.get(configPath))) {
            throw new FileNotFoundException("File di configurazione non trovato: " + configPath);
        }
        loadConfig();
    }

    public void loadConfig() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(configPath))) {
            globalSettings.clear();
            homeSettings.clear();
            shares.clear();

            SmbCondBean currentShare = null;
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("[")) {
                    String section = line.substring(1, line.length() - 1);
                    if (section.equalsIgnoreCase("global")) {
                        currentShare = null;
                    } else if (section.equalsIgnoreCase("homes")) {
                        currentShare = null;
                    } else {
                        if (currentShare != null) {
                            shares.add(currentShare);
                        }
                        currentShare = new SmbCondBean(section);
                    }
                } else {
                    String[] keyValue = line.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();

                        if (currentShare == null) {
                            if (line.contains("homes")) {
                                homeSettings.add(new String[]{key, value});
                            } else {
                                globalSettings.add(new String[]{key, value});
                            }
                        } else {
                            currentShare.addProperty(key, value);
                        }
                    }
                }
            }
            if (currentShare != null) {
                shares.add(currentShare);
            }
        }
    }

    public String getFormattedGlobalSettings() {
        StringBuilder builder = new StringBuilder("[global]\n");
        for (String[] setting : globalSettings) {
            builder.append(setting[0]).append(" = ").append(setting[1]).append("\n");
        }
        return builder.toString();
    }

    public String getFormattedHomeSettings() {
        StringBuilder builder = new StringBuilder("[homes]\n");
        for (String[] setting : homeSettings) {
            builder.append(setting[0]).append(" = ").append(setting[1]).append("\n");
        }
        return builder.toString();
    }

    public String getFormattedShares() {
        StringBuilder builder = new StringBuilder();
        for (SmbCondBean share : shares) {
            builder.append(share.toFormattedString()).append("\n");
        }
        return builder.toString();
    }

    public ArrayList<SmbCondBean> getAllShares() {
        return new ArrayList<>(shares);
    }

    public void addGlobalSetting(String key, String value) {
        addOrUpdate(globalSettings, key, value);
    }

    public void addHomeSetting(String key, String value) {
        addOrUpdate(homeSettings, key, value);
    }

    public void removeGlobalSetting(String key) {
        removeSetting(globalSettings, key);
    }

    public void removeHomeSetting(String key) {
        removeSetting(homeSettings, key);
    }

    public void addShare(SmbCondBean share) {
        shares.add(share);
    }

    public void modifyShare(String shareName, SmbCondBean updatedShare) {
        for (int i = 0; i < shares.size(); i++) {
            if (shares.get(i).getName().equalsIgnoreCase(shareName)) {
                shares.set(i, updatedShare);
                return;
            }
        }
        throw new IllegalArgumentException("Condivisione non trovata: " + shareName);
    }

    public void removeShare(String shareName) {
        shares.removeIf(share -> share.getName().equalsIgnoreCase(shareName));
    }

    public SmbCondBean getShare(String shareName) {
        return shares.stream()
                .filter(share -> share.getName().equalsIgnoreCase(shareName))
                .findFirst()
                .orElse(null);
    }

    public void updateConfig() throws IOException {
        Path originalPath = Paths.get(configPath);
        Path backupPath = Paths.get(configPath + ".bak");
        Files.copy(originalPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configPath))) {
            writer.write(getFormattedGlobalSettings());
            writer.write("\n");
            writer.write(getFormattedHomeSettings());
            writer.write("\n");
            writer.write(getFormattedShares());
        }
    }

    private void addOrUpdate(ArrayList<String[]> settings, String key, String value) {
        for (String[] pair : settings) {
            if (pair[0].equalsIgnoreCase(key)) {
                pair[1] = value;
                return;
            }
        }
        settings.add(new String[]{key, value});
    }

    private void removeSetting(ArrayList<String[]> settings, String key) {
        settings.removeIf(pair -> pair[0].equalsIgnoreCase(key));
    }
}

