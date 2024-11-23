package org.antonio;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class SambaManager {
    private String configPath;
    private ArrayList<String[]> globalSettings;
    private ArrayList<String[]> homeSettings;
    private ArrayList<SmbCondBean> shares;
    private ArrayList<String> sambaUsers; // Lista degli utenti Samba

    public SambaManager(String configPath) throws IOException {
        this.configPath = configPath;
        this.globalSettings = new ArrayList<>();
        this.homeSettings = new ArrayList<>();
        this.shares = new ArrayList<>();
        this.sambaUsers = new ArrayList<>();
        if (!Files.exists(Paths.get(configPath))) {
            throw new FileNotFoundException("File di configurazione non trovato: " + configPath);
        }
        loadConfig();
        loadSambaUsers(); // Carica gli utenti Samba
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
                            if (key.equalsIgnoreCase("valid users")) {
                                String[] users = value.split(",");
                                for (String user : users) {
                                    currentShare.addValidUser(user.trim());
                                }
                            } else {
                                currentShare.addProperty(key, value);
                            }
                        }
                    }
                }
            }
            if (currentShare != null) {
                shares.add(currentShare);
            }
        }
    }

    public void loadSambaUsers() throws IOException {
        sambaUsers.clear();
        ProcessBuilder pb = new ProcessBuilder("pdbedit", "-L");
        String output = executeCommandWithOutput(pb, "Errore durante il caricamento degli utenti Samba");

        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length > 0) {
                    sambaUsers.add(parts[0].trim());
                }
            }
        }
    }

    public void addSambaUser(String username, String password) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "smbpasswd", "-a", username);

        try {
            Process process = pb.start();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write(password);
                writer.newLine();
                writer.write(password);
                writer.newLine();
                writer.flush();
            }

            if (process.waitFor() != 0) {
                throw new IOException("Errore durante l'aggiunta dell'utente Samba: " + username);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Errore durante l'aggiunta dell'utente Samba: " + username, e);
        }

        loadSambaUsers();
    }

    public void removeSambaUser(String username) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "smbpasswd", "-x", username);
        executeCommand(pb, "Errore durante la rimozione dell'utente Samba: " + username);
        loadSambaUsers();
    }


    public ArrayList<String> getSambaUsers() {
        return new ArrayList<>(sambaUsers);
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

    public void removeUserFromShare(String shareName, String username) {
        SmbCondBean share = getShare(shareName);
        if (share != null) {
            share.removeValidUser(username);
        } else {
            throw new IllegalArgumentException("Condivisione non trovata: " + shareName);
        }
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

    // Ritorna tutte le condivisioni di un dato utente
    public ArrayList<SmbCondBean> getSharesByUser(String username) {
        ArrayList<SmbCondBean> userShares = new ArrayList<>();
        for (SmbCondBean share : shares) {
            if (share.getValidUsers().contains(username)) {
                userShares.add(share);
            }
        }
        return userShares;
    }

    // Avvia il servizio Samba
    public void startSambaService() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "systemctl", "start", "smbd");
        executeCommand(pb, "Errore durante l'avvio del servizio Samba");
    }

    // Ferma il servizio Samba
    public void stopSambaService() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "systemctl", "stop", "smbd");
        executeCommand(pb, "Errore durante l'arresto del servizio Samba");
    }

    // Metodo helper per eseguire comandi con gestione degli errori
    private void executeCommand(ProcessBuilder pb, String errorMessage) throws IOException {
        try {
            Process process = pb.start();
            if (process.waitFor() != 0) {
                throw new IOException(errorMessage);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(errorMessage, e);
        }
    }

    private String executeCommandWithOutput(ProcessBuilder pb, String errorMessage) throws IOException {
        StringBuilder output = new StringBuilder();
        try {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            if (process.waitFor() != 0) {
                throw new IOException(errorMessage);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(errorMessage, e);
        }

        return output.toString();
    }

}
