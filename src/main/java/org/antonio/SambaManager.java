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
    private boolean debugEnabled; // Flag per il debug

    public SambaManager(String configPath) throws IOException {
        this.configPath = configPath;
        this.globalSettings = new ArrayList<>();
        this.homeSettings = new ArrayList<>();
        this.shares = new ArrayList<>();
        this.sambaUsers = new ArrayList<>();
        this.debugEnabled = false; // Debug disabilitato di default
        if (!Files.exists(Paths.get(configPath))) {
            throw new FileNotFoundException("File di configurazione non trovato: " + configPath);
        }
        loadConfig();
        loadSambaUsers(); // Carica gli utenti Samba
    }

    // Metodo per attivare/disattivare il debug
    public void toggleDebug() {
        debugEnabled = !debugEnabled;
        System.out.println("SAMBAMANAGER_DEBUG: Debug " + (debugEnabled ? "abilitato" : "disabilitato"));
    }

    // Metodo per stampare messaggi di debug
    private void printDebug(String message) {
        if (debugEnabled) {
            // Codice ANSI per il colore rosso chiaro
            final String RED_BRIGHT = "\033[91m";
            final String RESET = "\033[0m"; // Resetta il colore al valore predefinito

            // Stampa il messaggio con il prefisso colorato
            System.out.println(RED_BRIGHT + "SAMBAMANAGER_DEBUG: " + RESET + message);
        }
    }

    public void loadConfig() throws IOException {
        printDebug("Caricamento configurazione Samba...");
        try (BufferedReader reader = new BufferedReader(new FileReader(configPath))) {
            globalSettings.clear();
            homeSettings.clear();
            shares.clear();

            SmbCondBean currentShare = null;
            String currentSection = null;
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("[")) {
                    // Gestione cambio sezione
                    String section = line.substring(1, line.length() - 1);
                    currentSection = section.toLowerCase();

                    if (currentShare != null) {
                        shares.add(currentShare);
                        printDebug("Condivisione aggiunta: " + currentShare.getName());
                    }

                    if (section.equalsIgnoreCase("global")) {
                        currentShare = null;
                        printDebug("Sezione Global individuata.");
                    } else if (section.equalsIgnoreCase("homes")) {
                        currentShare = null;
                        printDebug("Sezione Homes individuata.");
                    } else {
                        currentShare = new SmbCondBean(section);
                        printDebug("Inizio nuova condivisione: " + section);
                    }
                } else {
                    String[] keyValue = line.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();

                        if ("global".equals(currentSection)) {
                            globalSettings.add(new String[]{key, value});
                            printDebug("Aggiunta impostazione Global: " + key + " = " + value);
                        } else if ("homes".equals(currentSection)) {
                            homeSettings.add(new String[]{key, value});
                            printDebug("Aggiunta impostazione Home: " + key + " = " + value);
                        } else if (currentShare != null) {
                            if (key.equalsIgnoreCase("valid users")) {
                                String[] users = value.split(",");
                                for (String user : users) {
                                    currentShare.addValidUser(user.trim());
                                    printDebug("Aggiunto valid user: " + user.trim());
                                }
                            } else {
                                currentShare.addProperty(key, value);
                                printDebug("Aggiunta proprietà alla condivisione: " + key + " = " + value);
                            }
                        }
                    }
                }
            }

            if (currentShare != null) {
                shares.add(currentShare);
                printDebug("Condivisione finale aggiunta: " + currentShare.getName());
            }
        }
        printDebug("Configurazione Samba caricata con successo.");
    }

    private void loadSambaUsers() throws IOException {
        printDebug("Caricamento utenti Samba...");
        sambaUsers.clear();
        ProcessBuilder pb = new ProcessBuilder("pdbedit", "-L");
        String output = executeCommandWithOutput(pb, "Errore durante il caricamento degli utenti Samba");

        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length > 0) {
                    sambaUsers.add(parts[0].trim());
                    printDebug("Utente Samba aggiunto: " + parts[0].trim());
                }
            }
        }
        printDebug("Utenti Samba caricati con successo.");
    }

    public void addSambaUser(String username, String password) throws IOException {
        printDebug("Aggiunta utente Samba: " + username);
        ProcessBuilder pb = new ProcessBuilder("smbpasswd", "-a", username);

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
        printDebug("Utente Samba aggiunto con successo: " + username);
    }

    public void removeSambaUser(String username) throws IOException, InterruptedException {
        printDebug("Rimozione utente Samba: " + username);
        for (SmbCondBean share : new ArrayList<>(shares)) {
            if (share.getValidUsers().contains(username)) {
                printDebug("Rimuovo l'utente dalla condivisione: " + share.getName());
                share.removeValidUser(username);
                modifyShare(share.getName(), share);
            }
        }

        updateConfig();

        ProcessBuilder pb = new ProcessBuilder("smbpasswd", "-x", username);
        executeCommand(pb, "Errore durante la rimozione dell'utente Samba: " + username);

        loadSambaUsers();
        printDebug("Utente Samba rimosso con successo: " + username);
    }

    public ArrayList<String> getSambaUsers() {
        printDebug("Restituzione lista utenti Samba.");
        return new ArrayList<>(sambaUsers);
    }

    public String getFormattedGlobalSettings() {
        printDebug("Restituzione configurazione Global.");
        StringBuilder builder = new StringBuilder("[global]\n");
        for (String[] setting : globalSettings) {
            builder.append(setting[0]).append(" = ").append(setting[1]).append("\n");
        }
        return builder.toString();
    }

    public String getFormattedHomeSettings() {
        printDebug("Restituzione configurazione Home.");
        StringBuilder builder = new StringBuilder("[homes]\n");
        for (String[] setting : homeSettings) {
            builder.append(setting[0]).append(" = ").append(setting[1]).append("\n");
        }
        return builder.toString();
    }

    public String getFormattedShares() {
        printDebug("Restituzione configurazioni condivisioni Samba.");
        StringBuilder builder = new StringBuilder();
        for (SmbCondBean share : shares) {
            builder.append(share.toFormattedString()).append("\n");
        }
        return builder.toString();
    }

    public ArrayList<SmbCondBean> getAllShares() {
        printDebug("Restituzione lista completa delle condivisioni Samba.");
        return new ArrayList<>(shares);
    }

    public void updateConfig() throws IOException, InterruptedException {
        printDebug("Aggiornamento file di configurazione Samba...");
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

        Thread.sleep(1000);

        loadConfig();
        printDebug("File di configurazione Samba aggiornato.");
    }

    public void addShare(SmbCondBean share) {
        printDebug("Aggiunta condivisione Samba: " + share.getName());
        shares.add(share);
    }

    public void modifyShare(String shareName, SmbCondBean updatedShare) {
        printDebug("Modifica condivisione Samba: " + shareName);
        for (int i = 0; i < shares.size(); i++) {
            if (shares.get(i).getName().equalsIgnoreCase(shareName)) {
                if (updatedShare.getValidUsers().isEmpty()) {
                    printDebug("Condivisione senza utenti validi, la rimuovo: " + shareName);
                    shares.remove(i);
                    return;
                }
                shares.set(i, updatedShare);
                printDebug("Condivisione aggiornata con successo: " + shareName);
                return;
            }
        }
        throw new IllegalArgumentException("Condivisione non trovata: " + shareName);
    }

    public void removeShare(String shareName) {
        printDebug("Rimozione condivisione Samba: " + shareName);
        boolean removed = shares.removeIf(share -> share.getName().equalsIgnoreCase(shareName));
        if (removed) {
            printDebug("Condivisione rimossa con successo: " + shareName);
        } else {
            printDebug("Condivisione non trovata per la rimozione: " + shareName);
        }
    }

    public SmbCondBean getShare(String shareName) {
        printDebug("Ricerca condivisione Samba: " + shareName);
        SmbCondBean share = shares.stream()
                .filter(s -> s.getName().equalsIgnoreCase(shareName))
                .findFirst()
                .orElse(null);
        if (share != null) {
            printDebug("Condivisione trovata: " + shareName);
        } else {
            printDebug("Condivisione non trovata: " + shareName);
        }
        return share;
    }

    public ArrayList<SmbCondBean> getSharesByUser(String username) {
        printDebug("Inizio ricerca delle condivisioni per l'utente: " + username);

        ArrayList<SmbCondBean> userShares = new ArrayList<>();
        printDebug("Numero totale di condivisioni configurate: " + shares.size());

        for (SmbCondBean share : shares) {
            printDebug("Controllo condivisione: " + share.getName());
            if (share.getValidUsers().contains(username)) {
                printDebug("L'utente " + username + " ha accesso alla condivisione: " + share.getName());
                userShares.add(share);
            } else {
                printDebug("L'utente " + username + " non ha accesso alla condivisione: " + share.getName());
            }
        }

        printDebug("Ricerca completata. Numero di condivisioni trovate per l'utente " + username + ": " + userShares.size());
        return userShares;
    }

    private void addOrUpdate(ArrayList<String[]> settings, String key, String value) {
        printDebug("Aggiunta/Aggiornamento impostazione: " + key + " = " + value);
        for (String[] pair : settings) {
            if (pair[0].equalsIgnoreCase(key)) {
                pair[1] = value;
                printDebug("Impostazione aggiornata: " + key + " = " + value);
                return;
            }
        }
        settings.add(new String[]{key, value});
        printDebug("Nuova impostazione aggiunta: " + key + " = " + value);
    }

    private void removeSetting(ArrayList<String[]> settings, String key) {
        printDebug("Rimozione impostazione: " + key);
        boolean removed = settings.removeIf(pair -> pair[0].equalsIgnoreCase(key));
        if (removed) {
            printDebug("Impostazione rimossa: " + key);
        } else {
            printDebug("Impostazione non trovata: " + key);
        }
    }

    // Metodo helper per eseguire comandi con gestione degli errori
    private void executeCommand(ProcessBuilder pb, String errorMessage) throws IOException {
        printDebug("Esecuzione comando: " + String.join(" ", pb.command()));
        try {
            Process process = pb.start();
            if (process.waitFor() != 0) {
                throw new IOException(errorMessage);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(errorMessage, e);
        }
        printDebug("Comando eseguito con successo: " + String.join(" ", pb.command()));
    }

    private String executeCommandWithOutput(ProcessBuilder pb, String errorMessage) throws IOException {
        printDebug("Esecuzione comando con output: " + String.join(" ", pb.command()));
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

        printDebug("Comando eseguito con successo con output: " + output);
        return output.toString();
    }

    public void addGlobalSetting(String key, String value) {
        printDebug("Aggiunta nuovo GlobalSetting: " + key + " = " + value);
        addOrUpdate(globalSettings, key, value);
    }
    public void addHomeSetting(String key, String value) {
        printDebug("Aggiunta nuovo HomeSetting: " + key + " = " + value);
        addOrUpdate(homeSettings, key, value);
    }
    public void removeGlobalSetting(String key) {
        printDebug("Rimozione GlobalSetting: " + key);
        removeSetting(globalSettings, key);
    }
    public void removeHomeSetting(String key) {
        printDebug("Rimozione HomeSetting: " + key);
        removeSetting(homeSettings, key);
    }

    public void startSambaService() throws IOException {
        printDebug("Tentativo di avvio del servizio Samba...");
        ProcessBuilder pb = new ProcessBuilder("systemctl", "start", "smbd");
        printDebug("Comando costruito: " + String.join(" ", pb.command()));

        try {
            executeCommand(pb, "Errore durante l'avvio del servizio Samba");
            printDebug("Servizio Samba avviato con successo.");
        } catch (IOException e) {
            printDebug("Errore durante l'avvio del servizio Samba: " + e.getMessage());
            throw e;
        }
    }

    public void stopSambaService() throws IOException {
        printDebug("Tentativo di arresto del servizio Samba...");
        ProcessBuilder pb = new ProcessBuilder("systemctl", "stop", "smbd");
        printDebug("Comando costruito: " + String.join(" ", pb.command()));

        try {
            executeCommand(pb, "Errore durante l'arresto del servizio Samba");
            printDebug("Servizio Samba arrestato con successo.");
        } catch (IOException e) {
            printDebug("Errore durante l'arresto del servizio Samba: " + e.getMessage());
            throw e;
        }
    }

    public void readConfigFromText(String configText) throws IOException {
        printDebug("Inizio aggiornamento della configurazione interna da testo.");

        globalSettings.clear();
        homeSettings.clear();
        shares.clear();

        BufferedReader reader = new BufferedReader(new StringReader(configText));
        SmbCondBean currentShare = null;
        String currentSection = null;
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            if (line.startsWith("[")) {
                // Gestione cambio sezione
                String section = line.substring(1, line.length() - 1);
                currentSection = section.toLowerCase();

                if (currentShare != null) {
                    shares.add(currentShare);
                    printDebug("Condivisione aggiunta: " + currentShare.getName());
                }

                if (section.equalsIgnoreCase("global")) {
                    currentShare = null;
                    printDebug("Sezione Global individuata.");
                } else if (section.equalsIgnoreCase("homes")) {
                    currentShare = null;
                    printDebug("Sezione Homes individuata.");
                } else {
                    currentShare = new SmbCondBean(section);
                    printDebug("Inizio nuova condivisione: " + section);
                }
            } else {
                String[] keyValue = line.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();

                    if ("global".equals(currentSection)) {
                        globalSettings.add(new String[]{key, value});
                        printDebug("Aggiunta impostazione Global: " + key + " = " + value);
                    } else if ("homes".equals(currentSection)) {
                        homeSettings.add(new String[]{key, value});
                        printDebug("Aggiunta impostazione Home: " + key + " = " + value);
                    } else if (currentShare != null) {
                        if (key.equalsIgnoreCase("valid users")) {
                            String[] users = value.split(",");
                            for (String user : users) {
                                currentShare.addValidUser(user.trim());
                                printDebug("Aggiunto valid user: " + user.trim());
                            }
                        } else {
                            currentShare.addProperty(key, value);
                            printDebug("Aggiunta proprietà alla condivisione: " + key + " = " + value);
                        }
                    }
                }
            }
        }

        if (currentShare != null) {
            shares.add(currentShare);
            printDebug("Condivisione finale aggiunta: " + currentShare.getName());
        }

        printDebug("Aggiornamento configurazione interna completato.");
    }

}
