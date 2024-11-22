package org.antonio;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FtpManager {
    private static final String VSFTPD_CONF = "/etc/vsftpd.conf";
    private static final String FTP_USERS_FILE = "/etc/vsftpd.userlist";

    private ArrayList<String[]> config;
    private ArrayList<FtpCondBean> ftpShares;
    private ArrayList<String> ftpUsers;

    public FtpManager() throws IOException {
        this.config = new ArrayList<>();
        this.ftpShares = new ArrayList<>();
        this.ftpUsers = new ArrayList<>();
        loadConfig();
        loadFtpUsers();
        loadFtpShares();
    }

    // Carica il file di configurazione di vsftpd
    private void loadConfig() throws IOException {
        config.clear();
        List<String> lines = Files.readAllLines(Paths.get(VSFTPD_CONF));
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    config.add(new String[]{parts[0].trim(), parts[1].trim()});
                }
            }
        }
    }

    // Aggiunge o aggiorna una configurazione
    public void addOrUpdateConfig(String key, String value) {
        for (String[] pair : config) {
            if (pair[0].equalsIgnoreCase(key)) {
                pair[1] = value;
                return;
            }
        }
        config.add(new String[]{key, value});
    }

    // Elimina una configurazione
    public void removeConfig(String key) {
        config.removeIf(pair -> pair[0].equalsIgnoreCase(key));
    }

    // Aggiorna il file di configurazione salvando un backup
    public void updateConfig() throws IOException {
        Path configPath = Paths.get(VSFTPD_CONF);
        Path backupPath = Paths.get(VSFTPD_CONF + ".bak");
        Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(VSFTPD_CONF))) {
            for (String[] pair : config) {
                writer.write(pair[0] + "=" + pair[1]);
                writer.newLine();
            }
        }
    }

    // Carica la lista utenti FTP
    private void loadFtpUsers() throws IOException {
        ftpUsers.clear();
        Path path = Paths.get(FTP_USERS_FILE);
        if (Files.exists(path)) {
            ftpUsers.addAll(Files.readAllLines(path));
        }
    }

    // Ritorna gli utenti FTP come array
    public String[] getFtpUsers() {
        return ftpUsers.toArray(new String[0]);
    }

    // Aggiunge un nuovo utente alla lista FTP
    public void addFtpUser(String username) throws IOException {
        if (!ftpUsers.contains(username)) {
            ftpUsers.add(username);
            Files.write(Paths.get(FTP_USERS_FILE), ftpUsers);
        }
    }

    // Rimuove un utente dalla lista FTP e dai bind mount
    public void removeFtpUser(String username) throws IOException {
        ftpUsers.remove(username);
        Files.write(Paths.get(FTP_USERS_FILE), ftpUsers);

        // Rimuovi tutte le condivisioni legate all'utente
        ftpShares.removeIf(share -> share.getUsername().equalsIgnoreCase(username));
        removeUserShares(username);
    }

    // Rimuove i bind mount associati a un utente
    private void removeUserShares(String username) throws IOException {
        List<FtpCondBean> userShares = getSharesByUser(username);
        for (FtpCondBean share : userShares) {
            removeShare(share);
        }
    }

    // Ritorna tutte le condivisioni FTP
    public ArrayList<FtpCondBean> getFtpShares() {
        return new ArrayList<>(ftpShares);
    }

    // Ritorna le condivisioni di un utente
    public List<FtpCondBean> getSharesByUser(String username) {
        ArrayList<FtpCondBean> userShares = new ArrayList<>();
        for (FtpCondBean share : ftpShares) {
            if (share.getUsername().equalsIgnoreCase(username)) {
                userShares.add(share);
            }
        }
        return userShares;
    }

    public void addShare(String username, String shareName, String path) throws IOException {
        Path userHome = Paths.get("/home", username);
        Path sharePath = userHome.resolve(shareName);
        Path targetPath = Paths.get(path);

        // Creare il bind mount
        if (!Files.exists(sharePath.getParent())) {
            Files.createDirectories(sharePath.getParent());
        }
        ProcessBuilder pb = new ProcessBuilder("sudo", "mount", "--bind", targetPath.toString(), sharePath.toString());
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Errore nel creare il bind mount per " + shareName);
            }
        } catch (InterruptedException e) {
            throw new IOException("Errore nel creare il bind mount per " + shareName, e);
        }

        // Aggiungere il bind mount in fstab
        String fstabEntry = targetPath + " " + sharePath + " none bind 0 0";
        Files.write(Paths.get("/etc/fstab"), Collections.singletonList(fstabEntry), StandardOpenOption.APPEND);

        ftpShares.add(new FtpCondBean(username, shareName, path));
    }

    // Modifica nel metodo loadFtpShares
    private void loadFtpShares() throws IOException {
        ftpShares.clear();
        Path homeDir = Paths.get("/home");
        if (Files.exists(homeDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(homeDir)) {
                for (Path userDir : stream) {
                    if (Files.isDirectory(userDir)) {
                        try (DirectoryStream<Path> shareStream = Files.newDirectoryStream(userDir)) {
                            for (Path share : shareStream) {
                                if (Files.isDirectory(share)) { // Solo directory condivise
                                    ftpShares.add(new FtpCondBean(userDir.getFileName().toString(),
                                            share.getFileName().toString(), share.toString()));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modifica nel metodo removeShare
    public void removeShare(FtpCondBean share) throws IOException {
        Path sharePath = Paths.get("/home", share.getUsername(), share.getShareName());

        // Smontare il bind mount
        ProcessBuilder pb = new ProcessBuilder("sudo", "umount", sharePath.toString());
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Errore nello smontare il bind mount per " + share.getShareName());
            }
        } catch (InterruptedException e) {
            throw new IOException("Errore nello smontare il bind mount per " + share.getShareName(), e);
        }

        // Rimuovere la directory
        Files.deleteIfExists(sharePath);
        ftpShares.remove(share);

        // Rimuovere l'entry da fstab
        Path fstabPath = Paths.get("/etc/fstab");
        List<String> fstabLines = Files.readAllLines(fstabPath);
        String mountEntry = share.getPath() + " " + sharePath + " none bind 0 0";
        fstabLines.removeIf(line -> line.equals(mountEntry));
        Files.write(fstabPath, fstabLines);
    }
}