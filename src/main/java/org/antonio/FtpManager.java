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
    private boolean debugEnabled; // Flag per il debug

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

    // Metodo per attivare/disattivare il debug
    public void toggleDebug() {
        debugEnabled = !debugEnabled;
        System.out.println("FTPMANAGER_DEBUG: Debug " + (debugEnabled ? "abilitato" : "disabilitato"));
    }

    // Metodo per stampare messaggi di debug
    private void printDebug(String message) {
        if (debugEnabled) {
            // Codice ANSI per il colore rosso chiaro
            final String YELLOW_BRIGHT = "\033[93m";
            final String RESET = "\033[0m"; // Resetta il colore al valore predefinito

            // Stampa il messaggio con il prefisso colorato
            System.out.println(YELLOW_BRIGHT + "FTPMANAGER_DEBUG: " + RESET + message);
        }
    }

    private void loadConfig() throws IOException {
        printDebug("Inizio caricamento della configurazione da: " + vsftpdConfPath);
        config.clear();
        Path configPath = Paths.get(vsftpdConfPath);

        if (!Files.exists(configPath)) {
            printDebug("File di configurazione non trovato: " + vsftpdConfPath);
            throw new FileNotFoundException("File di configurazione non trovato: " + vsftpdConfPath);
        }

        List<String> lines = Files.readAllLines(configPath);
        printDebug("Numero di righe lette: " + lines.size());

        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    config.add(new String[]{parts[0].trim(), parts[1].trim()});
                    printDebug("Aggiunta configurazione: " + parts[0].trim() + " = " + parts[1].trim());
                } else {
                    printDebug("Riga ignorata (non valida): " + line);
                }
            } else {
                printDebug("Riga ignorata (vuota o commento): " + line);
            }
        }
        printDebug("Configurazione caricata con successo.");
    }

    public void addOrUpdateConfig(String key, String value) {
        printDebug("Tentativo di aggiungere o aggiornare la configurazione: " + key + " = " + value);

        for (String[] pair : config) {
            if (pair[0].equalsIgnoreCase(key)) {
                printDebug("Configurazione esistente trovata. Aggiornamento valore da: " + pair[1] + " a: " + value);
                pair[1] = value;
                return;
            }
        }

        config.add(new String[]{key, value});
        printDebug("Configurazione aggiunta: " + key + " = " + value);
    }

    public void removeConfig(String key) {
        printDebug("Tentativo di rimuovere la configurazione con chiave: " + key);
        boolean removed = config.removeIf(pair -> pair[0].equalsIgnoreCase(key));

        if (removed) {
            printDebug("Configurazione rimossa: " + key);
        } else {
            printDebug("Nessuna configurazione trovata con chiave: " + key);
        }
    }

    public void updateConfig() throws IOException, InterruptedException {
        printDebug("Inizio aggiornamento del file di configurazione: " + vsftpdConfPath);
        Path configPath = Paths.get(vsftpdConfPath);
        Path backupPath = Paths.get(vsftpdConfPath + ".bak");

        printDebug("Creazione backup del file di configurazione in: " + backupPath);
        Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        printDebug("Backup completato con successo.");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configPath.toFile()))) {
            printDebug("Scrittura delle configurazioni aggiornate nel file.");
            for (String[] pair : config) {
                writer.write(pair[0] + "=" + pair[1]);
                writer.newLine();
                printDebug("Configurazione scritta: " + pair[0] + " = " + pair[1]);
            }
        }
        printDebug("Scrittura completata. Attesa di 1 secondo prima di ricaricare la configurazione.");
        Thread.sleep(1000);

        loadConfig();
        printDebug("Configurazione aggiornata e ricaricata con successo.");
    }

    public String getFormattedConfig() {
        printDebug("Inizio formattazione della configurazione FTP.");
        StringBuilder builder = new StringBuilder("=== FTP Configuration ===\n");
        printDebug("Aggiunto titolo alla configurazione formattata.");

        for (String[] pair : config) {
            builder.append(pair[0]).append(" = ").append(pair[1]).append("\n");
            printDebug("Aggiunta configurazione: " + pair[0] + " = " + pair[1]);
        }

        printDebug("Formattazione completata.");
        return builder.toString();
    }

    private void loadFtpUsers() throws IOException {
        printDebug("Inizio caricamento degli utenti FTP dal file: " + ftpUsersFilePath);
        ftpUsers.clear();
        Path path = Paths.get(ftpUsersFilePath);

        if (Files.exists(path)) {
            List<String> users = Files.readAllLines(path);
            ftpUsers.addAll(users);
            printDebug("Utenti FTP caricati: " + users);
        } else {
            printDebug("Il file utenti FTP non esiste: " + ftpUsersFilePath);
        }
        printDebug("Caricamento utenti FTP completato. Numero totale di utenti: " + ftpUsers.size());
    }

    public ArrayList<String> getFtpUsers() {
        printDebug("Recupero della lista degli utenti FTP.");
        return new ArrayList<>(ftpUsers);
    }

    public void addFtpUser(String username) throws IOException {
        printDebug("Tentativo di aggiungere l'utente FTP: " + username);

        if (!ftpUsers.contains(username)) {
            ftpUsers.add(username);
            Files.write(Paths.get(ftpUsersFilePath), ftpUsers);
            printDebug("Utente FTP aggiunto con successo: " + username);
        } else {
            printDebug("L'utente FTP esiste già: " + username);
        }
    }

    public void removeFtpUser(String username) throws IOException, InterruptedException {
        printDebug("Tentativo di rimuovere l'utente FTP: " + username);
        Path path = Paths.get(ftpUsersFilePath);

        if (Files.exists(path)) {
            boolean removed = ftpUsers.remove(username);
            if (removed) {
                Files.write(path, ftpUsers);
                printDebug("Utente FTP rimosso con successo: " + username);
            } else {
                printDebug("Utente FTP non trovato nella lista: " + username);
            }
        } else {
            printDebug("Il file utenti FTP non esiste: " + ftpUsersFilePath);
        }

        printDebug("Rimozione delle condivisioni associate all'utente FTP: " + username);
        removeUserShares(username);
        printDebug("Salvataggio delle condivisioni aggiornate su disco.");
        saveSharesOnDisk();
        printDebug("Ricaricamento della lista degli utenti FTP.");
        loadFtpUsers();
    }

    private void removeUserShares(String username) throws IOException {
        printDebug("Inizio rimozione delle condivisioni per l'utente FTP: " + username);
        List<FtpCondBean> userShares = getSharesByUser(username);

        if (userShares.isEmpty()) {
            printDebug("Nessuna condivisione trovata per l'utente FTP: " + username);
        } else {
            for (FtpCondBean share : userShares) {
                printDebug("Rimozione condivisione: " + share.getShareName());
                removeShare(share);
            }
        }
        printDebug("Rimozione delle condivisioni completata per l'utente FTP: " + username);
    }

    public ArrayList<FtpCondBean> getFtpShares() {
        printDebug("Recupero della lista di tutte le condivisioni FTP.");
        printDebug("Numero totale di condivisioni FTP: " + ftpShares.size());
        return new ArrayList<>(ftpShares);
    }

    public ArrayList<FtpCondBean> getSharesByUser(String username) {
        printDebug("Ricerca delle condivisioni FTP per l'utente: " + username);
        ArrayList<FtpCondBean> userShares = new ArrayList<>();
        for (FtpCondBean share : ftpShares) {
            printDebug("Verifica condivisione: " + share.getShareName() + " (utente: " + share.getUsername() + ")");
            if (share.getUsername().equalsIgnoreCase(username)) {
                printDebug("Condivisione trovata per l'utente " + username + ": " + share.getShareName());
                userShares.add(share);
            }
        }
        printDebug("Numero totale di condivisioni trovate per l'utente " + username + ": " + userShares.size());
        return userShares;
    }

    public void addShare(String username, String shareName, String path) {
        printDebug("Aggiunta di una nuova condivisione FTP.");
        printDebug("Dettagli condivisione: Utente = " + username + ", Nome = " + shareName + ", Percorso = " + path);
        ftpShares.add(new FtpCondBean(username, shareName, path)); // Solo nella lista temporanea
        printDebug("Condivisione FTP aggiunta con successo.");
    }

    public void removeShare(FtpCondBean share) {
        printDebug("Rimozione della condivisione FTP: " + share.getShareName() + " (utente: " + share.getUsername() + ")");
        if (ftpShares.remove(share)) {
            printDebug("Condivisione FTP rimossa con successo: " + share.getShareName());
        } else {
            printDebug("Condivisione FTP non trovata: " + share.getShareName());
        }
    }

    public void saveSharesOnDisk() throws IOException, InterruptedException {
        printDebug("Salvataggio delle condivisioni FTP su disco iniziato.");
        printDebug("Contenuto attuale di ftpShares: " + ftpShares.size());
        printDebug("Contenuto attuale di ftpSharesCopy: " + ftpSharesCopy.size());

        // Rimuovi le condivisioni che non sono più presenti nella lista principale
        for (FtpCondBean share : new ArrayList<>(ftpSharesCopy)) { // Copia per iterazione sicura
            if (!ftpShares.contains(share)) {
                Path sharePath = Paths.get("/home", share.getUsername(), share.getShareName());
                printDebug("Rimuovo bind mount non più presente: " + sharePath);
                if (isBindMount(sharePath)) {
                    printDebug("Bind mount trovato, procedo con la rimozione: " + sharePath);
                    deleteBindMount(sharePath.toString());
                } else {
                    printDebug("Nessun bind mount trovato per: " + sharePath);
                }
                ftpSharesCopy.remove(share); // Rimuove anche dalla copia
                printDebug("Condivisione rimossa dalla copia locale: " + share.getShareName());
            }
        }

        // Aggiungi le nuove condivisioni presenti nella lista principale
        for (FtpCondBean share : ftpShares) {
            Path userHome = Paths.get("/home", share.getUsername());
            Path sharePath = userHome.resolve(share.getShareName());
            Path targetPath = Paths.get(share.getPath());

            printDebug("Controllo bind mount per: " + sharePath);
            if (!isBindMount(sharePath)) {
                printDebug("Creazione bind mount per " + sharePath + " -> " + targetPath);
                createBindMount(targetPath.toString(), sharePath.toString());
                if (!ftpSharesCopy.contains(share)) {
                    ftpSharesCopy.add(share); // Aggiorna la copia
                    printDebug("Condivisione aggiunta alla copia locale: " + share.getShareName());
                }
            } else {
                printDebug("Bind mount già esistente per " + sharePath);
            }
        }

        // Ricarica la lista
        printDebug("Ricarico la lista delle condivisioni FTP.");
        Thread.sleep(1000);
        loadFtpShares(); // Ricarica entrambe le liste
        printDebug("Lista delle condivisioni FTP ricaricata con successo.");
    }

    private boolean isBindMount(Path path) throws IOException {
        printDebug("Verifica se il percorso è un bind mount: " + path);
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/mountinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(path.toString())) {
                    printDebug("Bind mount trovato per il percorso: " + path);
                    return true; // Il percorso è un mount point
                }
            }
        }
        printDebug("Nessun bind mount trovato per il percorso: " + path);
        return false; // Non trovato, quindi non è un mount point
    }

    private void createBindMount(String sourcePath, String targetPath) throws IOException, InterruptedException {
        printDebug("Inizio creazione bind mount.");
        printDebug("Percorso sorgente: " + sourcePath);
        printDebug("Percorso destinazione: " + targetPath);

        Path targetDir = Paths.get(targetPath);

        // Crea la directory di destinazione se non esiste
        if (!Files.exists(targetDir)) {
            printDebug("La directory di destinazione non esiste. Creazione della directory: " + targetPath);
            Files.createDirectories(targetDir);
            printDebug("Directory creata con successo: " + targetPath);
        } else {
            printDebug("La directory di destinazione esiste già: " + targetPath);
        }

        // Esegui il bind mount
        ProcessBuilder mountPb = new ProcessBuilder("sudo", "mount", "--bind", sourcePath, targetPath);
        printDebug("Esecuzione del comando mount: " + String.join(" ", mountPb.command()));
        executeCommand(mountPb, "Errore nel creare il bind mount per " + targetPath);
        printDebug("Bind mount creato con successo da " + sourcePath + " a " + targetPath);

        // Aggiungi al fstab
        String fstabEntry = sourcePath + " " + targetPath + " none bind 0 0\n";
        Path fstabPath = Paths.get("/etc/fstab");
        List<String> currentFstab = Files.readAllLines(fstabPath);
        if (currentFstab.stream().noneMatch(line -> line.equals(fstabEntry.trim()))) {
            printDebug("Aggiunta del bind mount a /etc/fstab: " + fstabEntry.trim());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fstabPath.toFile(), true))) {
                writer.write(fstabEntry);
            }
            printDebug("Entry aggiunta a /etc/fstab con successo.");
        } else {
            printDebug("Entry già presente in /etc/fstab: " + fstabEntry.trim());
        }
    }

    private void deleteBindMount(String targetPath) throws IOException, InterruptedException {
        printDebug("Inizio rimozione bind mount.");
        printDebug("Percorso destinazione: " + targetPath);

        Path targetDir = Paths.get(targetPath);

        // Smonta il bind mount se esiste
        if (Files.exists(targetDir)) {
            printDebug("Il percorso di destinazione esiste. Tentativo di smontaggio: " + targetPath);
            ProcessBuilder umountPb = new ProcessBuilder("sudo", "umount", targetPath);
            try {
                printDebug("Esecuzione del comando umount: " + String.join(" ", umountPb.command()));
                executeCommand(umountPb, "Errore nello smontare il bind mount per " + targetPath);
                printDebug("Bind mount smontato con successo: " + targetPath);
            } catch (IOException e) {
                printDebug("Errore durante lo smontaggio: " + e.getMessage());
            }

            // Rimuovi la directory
            printDebug("Tentativo di rimuovere la directory: " + targetPath);
            Files.deleteIfExists(targetDir);
            printDebug("Directory rimossa con successo: " + targetPath);
        } else {
            printDebug("Il percorso di destinazione non esiste: " + targetPath);
        }

        // Rimuovi l'entry da /etc/fstab
        Path fstabPath = Paths.get("/etc/fstab");
        List<String> currentFstab = Files.readAllLines(fstabPath);
        String targetEntry = " " + targetPath + " none bind 0 0";
        printDebug("Rimozione dell'entry da /etc/fstab contenente: " + targetEntry.trim());
        currentFstab.removeIf(line -> line.contains(targetEntry.trim()));
        Files.write(fstabPath, currentFstab);
        printDebug("Entry rimossa da /etc/fstab con successo.");
    }

    private void loadFtpShares() throws IOException {
        printDebug("Inizio caricamento delle condivisioni FTP.");
        ftpShares.clear();
        ftpSharesCopy.clear();
        printDebug("Liste `ftpShares` e `ftpSharesCopy` svuotate.");

        Path homeDir = Paths.get("/home");
        printDebug("Controllo esistenza della directory home: " + homeDir);

        if (Files.exists(homeDir)) {
            printDebug("Directory home trovata. Avvio scansione delle sottodirectory degli utenti.");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(homeDir)) {
                for (Path userDir : stream) {
                    printDebug("Trovata directory utente: " + userDir);

                    if (Files.isDirectory(userDir)) {
                        printDebug("La directory è valida. Avvio scansione delle condivisioni in: " + userDir);

                        try (DirectoryStream<Path> shareStream = Files.newDirectoryStream(userDir)) {
                            for (Path share : shareStream) {
                                printDebug("Trovata potenziale condivisione: " + share);

                                if (Files.isDirectory(share) && isMountPointUsingProc(share)) { // Solo bind mount
                                    printDebug("Condivisione valida trovata (bind mount): " + share);
                                    FtpCondBean ftpShare = new FtpCondBean(
                                            userDir.getFileName().toString(),
                                            share.getFileName().toString(),
                                            share.toString()
                                    );
                                    ftpShares.add(ftpShare);
                                    ftpSharesCopy.add(ftpShare);
                                    printDebug("Condivisione aggiunta a `ftpShares` e `ftpSharesCopy`: " + ftpShare.toFormattedString());
                                } else {
                                    printDebug("Condivisione ignorata (non è un bind mount): " + share);
                                }
                            }
                        }
                    } else {
                        printDebug("Ignorato: " + userDir + " (non è una directory valida).");
                    }
                }
            }
        } else {
            printDebug("Directory home non trovata: " + homeDir);
        }

        printDebug("Caricamento delle condivisioni FTP completato.");
        printDebug("Numero totale di condivisioni caricate: " + ftpShares.size());
    }

    private boolean isMountPointUsingProc(Path path) throws IOException {
        printDebug("Verifica se il percorso è un mount point utilizzando /proc/self/mountinfo: " + path);
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/mountinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(path.toString())) {
                    printDebug("Il percorso è un mount point: " + path);
                    return true; // Il percorso è un mount point
                }
            }
        }
        printDebug("Il percorso non è un mount point: " + path);
        return false; // Non trovato, quindi non è un mount point
    }

    public void startFtpService() throws IOException {
        printDebug("Tentativo di avvio del servizio FTP.");
        ProcessBuilder pb = new ProcessBuilder("sudo", "systemctl", "start", "vsftpd");
        printDebug("Comando costruito: " + String.join(" ", pb.command()));
        executeCommand(pb, "Errore durante l'avvio del servizio FTP");
        printDebug("Servizio FTP avviato con successo.");
    }

    public void stopFtpService() throws IOException {
        printDebug("Tentativo di arresto del servizio FTP.");
        ProcessBuilder pb = new ProcessBuilder("sudo", "systemctl", "stop", "vsftpd");
        printDebug("Comando costruito: " + String.join(" ", pb.command()));
        executeCommand(pb, "Errore durante l'arresto del servizio FTP");
        printDebug("Servizio FTP arrestato con successo.");
    }

    private void executeCommand(ProcessBuilder pb, String errorMessage) throws IOException {
        printDebug("Esecuzione del comando: " + String.join(" ", pb.command()));
        try {
            Process process = pb.start();
            if (process.waitFor() != 0) {
                printDebug("Errore durante l'esecuzione del comando: " + String.join(" ", pb.command()));
                throw new IOException(errorMessage);
            }
            printDebug("Comando eseguito con successo: " + String.join(" ", pb.command()));
        } catch (InterruptedException e) {
            printDebug("Comando interrotto: " + String.join(" ", pb.command()));
            Thread.currentThread().interrupt();
            throw new IOException(errorMessage, e);
        }
    }
}