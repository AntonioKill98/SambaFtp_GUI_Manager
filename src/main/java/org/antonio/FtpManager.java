package org.antonio;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FtpManager {
    private String vsftpdConfPath; // Percorso del file di configurazione
    private String ftpUsersFilePath; // Percorso del file lista utenti FTP

    private ArrayList<String[]> config;
    private ArrayList<FtpCondBean> ftpShares, ftpSharesCopy;
    private ArrayList<String> ftpUsers;

    public FtpManager(String vsftpdConfPath, String ftpUsersFilePath) throws IOException {
        this.vsftpdConfPath = vsftpdConfPath;
        this.ftpUsersFilePath = ftpUsersFilePath;
        this.config = new ArrayList<>();
        this.ftpShares = new ArrayList<>();
        this.ftpSharesCopy = new ArrayList<>();
        this.ftpUsers = new ArrayList<>();
        loadConfig();
        loadFtpUsers();
        loadFtpShares();
    }

    // Carica il file di configurazione di vsftpd
    private void loadConfig() throws IOException {
        config.clear();
        Path configPath = Paths.get(vsftpdConfPath);
        if (!Files.exists(configPath)) {
            throw new FileNotFoundException("File di configurazione non trovato: " + vsftpdConfPath);
        }
        List<String> lines = Files.readAllLines(configPath);
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
    public void updateConfig() throws IOException, InterruptedException {
        Path configPath = Paths.get(vsftpdConfPath);
        Path backupPath = Paths.get(vsftpdConfPath + ".bak");
        Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configPath.toFile()))) {
            for (String[] pair : config) {
                writer.write(pair[0] + "=" + pair[1]);
                writer.newLine();
            }
        }

        Thread.sleep(1000);

        loadConfig();
    }

    // Carica la lista utenti FTP
    private void loadFtpUsers() throws IOException {
        ftpUsers.clear();
        Path path = Paths.get(ftpUsersFilePath);
        if (Files.exists(path)) {
            ftpUsers.addAll(Files.readAllLines(path));
        }
    }

    // Ritorna la lista degli utenti FTP come ArrayList
    public ArrayList<String> getFtpUsers() {
        return new ArrayList<>(ftpUsers);
    }

    // Aggiunge un nuovo utente alla lista FTP
    public void addFtpUser(String username) throws IOException {
        if (!ftpUsers.contains(username)) {
            ftpUsers.add(username);
            Files.write(Paths.get(ftpUsersFilePath), ftpUsers);
        }
    }

    // Rimuove un utente dalla lista FTP e dai bind mount
    public void removeFtpUser(String username) throws IOException, InterruptedException {
        Path path = Paths.get(ftpUsersFilePath);
        if (Files.exists(path)) {
            ftpUsers.remove(username);
            Files.write(path, ftpUsers);
        }

        // Rimuovi tutte le condivisioni legate all'utente
        ftpShares.removeIf(share -> share.getUsername().equalsIgnoreCase(username));
        removeUserShares(username);
        saveSharesOnDisk();
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

    // Ritorna tutte le condivisioni FTP di un dato utente
    public ArrayList<FtpCondBean> getSharesByUser(String username) {
        ArrayList<FtpCondBean> userShares = new ArrayList<>();
        for (FtpCondBean share : ftpShares) {
            if (share.getUsername().equalsIgnoreCase(username)) {
                userShares.add(share);
            }
        }
        return userShares;
    }

    // Metodo per aggiungere una share FTP
    public void addShare(String username, String shareName, String path) {
        ftpShares.add(new FtpCondBean(username, shareName, path)); // Solo nella lista temporanea
    }

    /// Metodo per eliminare una share FTP
    public void removeShare(FtpCondBean share) {
        ftpShares.remove(share); // Rimuove solo dalla lista temporanea
    }

    public void saveSharesOnDisk() throws IOException, InterruptedException {
        System.out.println("DEBUG: Contenuto attuale di ftpShares: " + ftpShares.size());
        System.out.println("DEBUG: Contenuto attuale di ftpSharesCopy: " + ftpSharesCopy.size());

        // Rimuovi le condivisioni che non sono più presenti nella lista principale
        for (FtpCondBean share : new ArrayList<>(ftpSharesCopy)) { // Copia per iterazione sicura
            if (!ftpShares.contains(share)) {
                Path sharePath = Paths.get("/home", share.getUsername(), share.getShareName());
                System.out.println("DEBUG: Rimuovo bind mount non più presente: " + sharePath);
                if (isBindMount(sharePath)) {
                    deleteBindMount(sharePath.toString());
                }
                ftpSharesCopy.remove(share); // Rimuove anche dalla copia
            }
        }

        // Aggiungi le nuove condivisioni presenti nella lista principale
        for (FtpCondBean share : ftpShares) {
            Path userHome = Paths.get("/home", share.getUsername());
            Path sharePath = userHome.resolve(share.getShareName());
            Path targetPath = Paths.get(share.getPath());

            if (!isBindMount(sharePath)) {
                System.out.println("DEBUG: Creazione bind mount per " + sharePath);
                createBindMount(targetPath.toString(), sharePath.toString());
                if (!ftpSharesCopy.contains(share)) {
                    ftpSharesCopy.add(share); // Aggiorna la copia
                }
            } else {
                System.out.println("DEBUG: Bind mount già esistente per " + sharePath);
            }
        }

        // Ricarica la lista
        System.out.println("DEBUG: Ricarico la lista delle condivisioni FTP.");
        Thread.sleep(1000);
        loadFtpShares(); // Ricarica entrambe le liste
    }

    // Metodo per verificare se un percorso è un bind mount
    private boolean isBindMount(Path path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/mountinfo"))) {
            String line;
            //System.out.println("DEBUG: Contenuto di /proc/self/mountinfo:");
            while ((line = reader.readLine()) != null) {
                //System.out.println(line);
                if (line.contains(path.toString())) {
                    System.out.println("DEBUG: Trovato bind mount per " + path);
                    return true; // Il percorso è un mount point
                }
            }
        }
        System.out.println("DEBUG: Nessun bind mount trovato per " + path);
        return false; // Non trovato, quindi non è un mount point
    }


    private void createBindMount(String sourcePath, String targetPath) throws IOException, InterruptedException {
        Path targetDir = Paths.get(targetPath);

        // Crea la directory di destinazione se non esiste
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        // Esegui il bind mount
        ProcessBuilder mountPb = new ProcessBuilder("sudo", "mount", "--bind", sourcePath, targetPath);
        executeCommand(mountPb, "Errore nel creare il bind mount per " + targetPath);

        // Aggiungi al fstab
        String fstabEntry = sourcePath + " " + targetPath + " none bind 0 0\n";
        Path fstabPath = Paths.get("/etc/fstab");
        List<String> currentFstab = Files.readAllLines(fstabPath);
        if (currentFstab.stream().noneMatch(line -> line.equals(fstabEntry.trim()))) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fstabPath.toFile(), true))) {
                writer.write(fstabEntry);
            }
        }
    }

    private void deleteBindMount(String targetPath) throws IOException, InterruptedException {
        Path targetDir = Paths.get(targetPath);

        // Smonta il bind mount se esiste
        if (Files.exists(targetDir)) {
            ProcessBuilder umountPb = new ProcessBuilder("sudo", "umount", targetPath);
            try {
                executeCommand(umountPb, "Errore nello smontare il bind mount per " + targetPath);
            } catch (IOException e) {
                System.err.println("Errore durante lo smontaggio: " + e.getMessage());
            }

            // Rimuovi la directory
            Files.deleteIfExists(targetDir);
        }

        // Rimuovi l'entry da /etc/fstab
        Path fstabPath = Paths.get("/etc/fstab");
        List<String> currentFstab = Files.readAllLines(fstabPath);
        String targetEntry = " " + targetPath + " none bind 0 0";
        currentFstab.removeIf(line -> line.contains(targetEntry.trim()));
        Files.write(fstabPath, currentFstab);
    }

    // Metodo per caricare tutte le Share FTP
    private void loadFtpShares() throws IOException {
        ftpShares.clear();
        ftpSharesCopy.clear();
        Path homeDir = Paths.get("/home");

        if (Files.exists(homeDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(homeDir)) {
                for (Path userDir : stream) {
                    if (Files.isDirectory(userDir)) {
                        try (DirectoryStream<Path> shareStream = Files.newDirectoryStream(userDir)) {
                            for (Path share : shareStream) {
                                if (Files.isDirectory(share) && isMountPointUsingProc(share)) { // Solo bind mount
                                    ftpShares.add(new FtpCondBean(
                                            userDir.getFileName().toString(),
                                            share.getFileName().toString(),
                                            share.toString()
                                    ));
                                    ftpSharesCopy.add(new FtpCondBean(
                                            userDir.getFileName().toString(),
                                            share.getFileName().toString(),
                                            share.toString()
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Metodo per verificare se una directory è un mount point usando /proc/self/mountinfo
    private boolean isMountPointUsingProc(Path path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/mountinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(path.toString())) {
                    return true; // Il percorso è un mount point
                }
            }
        }
        return false; // Non trovato, quindi non è un mount point
    }

    // Avvia il servizio FTP
    public void startFtpService() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "systemctl", "start", "vsftpd");
        executeCommand(pb, "Errore durante l'avvio del servizio FTP");
    }

    // Ferma il servizio FTP
    public void stopFtpService() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "systemctl", "stop", "vsftpd");
        executeCommand(pb, "Errore durante l'arresto del servizio FTP");
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

}