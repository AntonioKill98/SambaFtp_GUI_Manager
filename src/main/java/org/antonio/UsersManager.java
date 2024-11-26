package org.antonio;
import java.io.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.GroupPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UsersManager {
    private ArrayList<UserBean> users;
    private SambaManager sambaManager;
    private FtpManager ftpManager;
    private boolean debugEnabled;

    public UsersManager(SambaManager sambaManager, FtpManager ftpManager) throws IOException {
        this.sambaManager = sambaManager;
        this.ftpManager = ftpManager;
        this.users = new ArrayList<>();
        loadUsers();
    }

    // Metodo per attivare/disattivare il debug
    public void toggleDebug() {
        debugEnabled = !debugEnabled;
        System.out.println("USERMANAGER_DEBUG: Debug " + (debugEnabled ? "abilitato" : "disabilitato"));
    }

    // Metodo per stampare messaggi di debug
    private void printDebug(String message) {
        if (debugEnabled) {
            // Codice ANSI per il colore rosso chiaro
            final String PINK_BRIGHT = "\033[95m";
            final String RESET = "\033[0m"; // Resetta il colore al valore predefinito

            // Stampa il messaggio con il prefisso colorato
            System.out.println(PINK_BRIGHT + "USERMANAGER_DEBUG: " + RESET + message);
        }
    }

    // Carica la lista degli utenti dal sistema e imposta i permessi FTP e Samba
    private void loadUsers() throws IOException {
        printDebug("Inizio caricamento degli utenti dal file /etc/passwd.");
        users.clear();
        printDebug("Lista utenti interna svuotata.");

        // Ottieni lista utenti dal file /etc/passwd
        List<String> passwdLines = Files.readAllLines(Paths.get("/etc/passwd"));
        printDebug("Numero di righe lette da /etc/passwd: " + passwdLines.size());

        for (String line : passwdLines) {
            String[] parts = line.split(":");
            if (parts.length > 6) { // Controlla che ci siano abbastanza campi
                String username = parts[0];
                String shell = parts[6]; // Ultimo campo: shell
                printDebug("Utente trovato: " + username + ", Shell: " + shell);

                // Include solo utenti con shell valida per il login
                if (!shell.equals("/usr/sbin/nologin") && !shell.equals("/bin/false")) {
                    printDebug("Utente valido per il login: " + username);
                    boolean sambaEnabled = sambaManager.getSambaUsers().contains(username);
                    boolean ftpEnabled = ftpManager.getFtpUsers().contains(username);

                    printDebug("Permessi Samba per " + username + ": " + sambaEnabled);
                    printDebug("Permessi FTP per " + username + ": " + ftpEnabled);

                    users.add(new UserBean(username, sambaEnabled, ftpEnabled));
                    printDebug("Utente aggiunto alla lista interna: " + username);
                } else {
                    printDebug("Utente ignorato (shell non valida): " + username);
                }
            } else {
                printDebug("Riga ignorata (formato non valido): " + line);
            }
        }

        printDebug("Caricamento utenti completato. Numero totale di utenti caricati: " + users.size());
    }

    // Ritorna un array con i soli nomi degli utenti
    public String[] getUsernames() {
        printDebug("Recupero dei nomi utenti dalla lista interna.");
        String[] usernames = users.stream().map(UserBean::getUsername).toArray(String[]::new);
        printDebug("Numero totale di nomi utenti recuperati: " + usernames.length);
        return usernames;
    }

    // Ritorna la lista completa di utenti (bean)
    public ArrayList<UserBean> getUsers() {
        printDebug("Recupero della lista completa degli utenti.");
        printDebug("Numero totale di utenti nella lista: " + users.size());
        return new ArrayList<>(users);
    }

    // Aggiunge un nuovo utente al sistema, abilitandolo opzionalmente a FTP e Samba
    public void addUser(String username, String password, boolean enableFtp, boolean enableSamba) throws IOException {
        printDebug("Inizio aggiunta dell'utente al sistema: " + username);

        // Nome del gruppo condiviso
        String sharedGroupName = "shareGroup";

        // Aggiunge l'utente al sistema
        ProcessBuilder pb = new ProcessBuilder("sudo", "useradd", "-m", username);
        printDebug("Esecuzione del comando per aggiungere l'utente: " + String.join(" ", pb.command()));
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                printDebug("Errore durante l'aggiunta dell'utente " + username);
                throw new IOException("Errore durante l'aggiunta dell'utente " + username);
            }
            printDebug("Utente aggiunto con successo: " + username);
        } catch (InterruptedException e) {
            printDebug("Comando interrotto durante l'aggiunta dell'utente: " + username);
            Thread.currentThread().interrupt();
            throw new IOException("Errore durante l'aggiunta dell'utente " + username, e);
        }

        // Aggiunge l'utente al gruppo condiviso
        printDebug("Aggiunta dell'utente " + username + " al gruppo condiviso " + sharedGroupName);
        ProcessBuilder groupAddPb = new ProcessBuilder("sudo", "usermod", "-aG", sharedGroupName, username);
        printDebug("Esecuzione del comando per aggiungere l'utente al gruppo: " + String.join(" ", groupAddPb.command()));
        Process groupAddProcess = groupAddPb.start();
        try {
            if (groupAddProcess.waitFor() != 0) {
                printDebug("Errore durante l'aggiunta dell'utente " + username + " al gruppo " + sharedGroupName);
                throw new IOException("Errore durante l'aggiunta dell'utente " + username + " al gruppo " + sharedGroupName);
            }
            printDebug("Utente aggiunto con successo al gruppo condiviso: " + sharedGroupName);
        } catch (InterruptedException e) {
            printDebug("Comando interrotto durante l'aggiunta dell'utente al gruppo: " + username);
            Thread.currentThread().interrupt();
            throw new IOException("Errore durante l'aggiunta dell'utente " + username + " al gruppo " + sharedGroupName, e);
        }

        // Imposta la password
        printDebug("Impostazione della password per l'utente: " + username);
        setPassword(username, password);
        printDebug("Password impostata con successo per l'utente: " + username);

        // Abilita Samba e FTP se richiesto
        if (enableSamba) {
            printDebug("Abilitazione Samba per l'utente: " + username);
            sambaManager.addSambaUser(username, password);
            printDebug("Utente abilitato a Samba: " + username);
        }
        if (enableFtp) {
            printDebug("Abilitazione FTP per l'utente: " + username);
            ftpManager.addFtpUser(username);
            printDebug("Utente abilitato a FTP: " + username);
        }

        printDebug("Ricaricamento della lista utenti.");
        loadUsers(); // Ricarica la lista utenti
        printDebug("Lista utenti ricaricata con successo.");
    }

    // Imposta la password di un utente
    private void setPassword(String username, String password) throws IOException {
        printDebug("Inizio impostazione della password per l'utente: " + username);
        ProcessBuilder pb = new ProcessBuilder("sudo", "chpasswd");
        printDebug("Esecuzione del comando per impostare la password: " + String.join(" ", pb.command()));
        Process process = pb.start();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
            writer.write(username + ":" + password);
            writer.newLine();
            writer.flush();
            printDebug("Password inviata al comando chpasswd per l'utente: " + username);
        }
        try {
            if (process.waitFor() != 0) {
                printDebug("Errore durante l'impostazione della password per l'utente: " + username);
                throw new IOException("Errore durante l'impostazione della password per l'utente " + username);
            }
            printDebug("Password impostata con successo per l'utente: " + username);
        } catch (InterruptedException e) {
            printDebug("Comando interrotto durante l'impostazione della password per l'utente: " + username);
            Thread.currentThread().interrupt();
            throw new IOException("Errore durante l'impostazione della password per l'utente " + username, e);
        }
    }

    // Rimuove un utente dal sistema e disabilita Samba e FTP
    public void removeUser(String username) throws IOException, InterruptedException {
        printDebug("Inizio rimozione dell'utente dal sistema: " + username);

        // Rimuovi le condivisioni e disabilita gli accessi
        printDebug("Rimozione dell'utente da Samba.");
        sambaManager.removeSambaUser(username);
        printDebug("Utente rimosso da Samba: " + username);

        printDebug("Rimozione dell'utente da FTP.");
        ftpManager.removeFtpUser(username);
        printDebug("Utente rimosso da FTP: " + username);

        // Elimina l'utente dal sistema
        ProcessBuilder pb = new ProcessBuilder("sudo", "userdel", "-r", username);
        printDebug("Esecuzione del comando per rimuovere l'utente: " + String.join(" ", pb.command()));
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                printDebug("Errore durante la rimozione dell'utente: " + username);
                throw new IOException("Errore durante la rimozione dell'utente " + username);
            }
            printDebug("Utente rimosso con successo dal sistema: " + username);
        } catch (InterruptedException e) {
            printDebug("Comando interrotto durante la rimozione dell'utente: " + username);
            Thread.currentThread().interrupt();
            throw new IOException("Errore durante la rimozione dell'utente " + username, e);
        }

        printDebug("Ricaricamento della lista utenti.");
        loadUsers(); // Ricarica la lista utenti
        printDebug("Lista utenti ricaricata con successo.");
    }

    public void enableSamba(String username, String password) throws IOException {
        printDebug("Abilitazione Samba per l'utente: " + username);

        // Abilita l'utente in Samba
        sambaManager.addSambaUser(username, password);
        printDebug("Utente abilitato a Samba: " + username);

        // Aggiorna lo stato nel bean
        UserBean user = users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);

        if (user != null) {
            user.setSambaEnabled(true);
            printDebug("Aggiornato lo stato Samba per l'utente: " + username + " nel bean.");
        } else {
            printDebug("Utente non trovato nella lista: " + username);
        }
    }

    public void disableSamba(String username) throws IOException, InterruptedException {
        printDebug("Disabilitazione Samba per l'utente: " + username);

        // Disabilita l'utente in Samba
        sambaManager.removeSambaUser(username);
        printDebug("Utente disabilitato da Samba: " + username);

        // Aggiorna lo stato nel bean
        UserBean user = users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);

        if (user != null) {
            user.setSambaEnabled(false);
            printDebug("Aggiornato lo stato Samba per l'utente: " + username + " nel bean.");
        } else {
            printDebug("Utente non trovato nella lista: " + username);
        }
    }

    public void enableFtp(String username) throws IOException {
        printDebug("Abilitazione FTP per l'utente: " + username);

        // Abilita l'utente in FTP
        ftpManager.addFtpUser(username);
        printDebug("Utente abilitato a FTP: " + username);

        // Aggiorna lo stato nel bean
        UserBean user = users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);

        if (user != null) {
            user.setFtpEnabled(true);
            printDebug("Aggiornato lo stato FTP per l'utente: " + username + " nel bean.");
        } else {
            printDebug("Utente non trovato nella lista: " + username);
        }
    }

    public void disableFtp(String username) throws IOException, InterruptedException {
        printDebug("Disabilitazione FTP per l'utente: " + username);

        // Disabilita l'utente in FTP
        ftpManager.removeFtpUser(username);
        printDebug("Utente disabilitato da FTP: " + username);

        // Aggiorna lo stato nel bean
        UserBean user = users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);

        if (user != null) {
            user.setFtpEnabled(false);
            printDebug("Aggiornato lo stato FTP per l'utente: " + username + " nel bean.");
        } else {
            printDebug("Utente non trovato nella lista: " + username);
        }
    }

    public void setPermissionForUser(String username) throws IOException {
        printDebug("Inizio verifica e impostazione dei permessi per l'utente: " + username);

        // Nome del gruppo condiviso
        String sharedGroupName = "shareGroup";

        // Ottieni tutti i percorsi associati all'utente (FTP e Samba)
        List<String> userPaths = new ArrayList<>();

        // Percorsi FTP
        List<String> ftpPaths = ftpManager.getSharesByUser(username).stream()
                .map(FtpCondBean::getPath)
                .collect(Collectors.toList());
        userPaths.addAll(ftpPaths);
        printDebug("Percorsi FTP trovati per l'utente: " + ftpPaths);

        // Percorsi Samba
        List<String> sambaPaths = sambaManager.getSharesByUser(username).stream()
                .flatMap(share -> share.getProperties().stream()
                        .filter(property -> property[0].equalsIgnoreCase("path"))
                        .map(property -> property[1]))
                .collect(Collectors.toList());
        userPaths.addAll(sambaPaths);
        printDebug("Percorsi Samba trovati per l'utente: " + sambaPaths);

        // Elimina duplicati
        userPaths = userPaths.stream().distinct().collect(Collectors.toList());
        printDebug("Elenco completo e univoco dei percorsi per l'utente: " + userPaths);

        // Ottieni il gruppo condiviso
        UserPrincipalLookupService lookupService = FileSystems.getDefault().getUserPrincipalLookupService();
        GroupPrincipal sharedGroup = lookupService.lookupPrincipalByGroupName(sharedGroupName);

        // Imposta i permessi su ogni directory
        for (String path : userPaths) {
            Path dirPath = Paths.get(path);

            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                printDebug("Percorso non valido o inesistente, ignorato: " + path);
                continue;
            }

            printDebug("Verifica dei permessi per il percorso: " + path);
            PosixFileAttributeView fileAttributeView = Files.getFileAttributeView(dirPath, PosixFileAttributeView.class);
            if (fileAttributeView == null) {
                printDebug("Il percorso non supporta i permessi POSIX, ignorato: " + path);
                continue;
            }

            // Ottieni il gruppo attuale della directory
            GroupPrincipal currentGroup = fileAttributeView.readAttributes().group();
            printDebug("Gruppo attuale per il percorso " + path + ": " + currentGroup.getName());

            // Imposta il gruppo della directory a "shareGroup" se necessario
            if (!currentGroup.equals(sharedGroup)) {
                printDebug("Impostazione del gruppo condiviso '" + sharedGroupName + "' per il percorso: " + path);
                fileAttributeView.setGroup(sharedGroup);
            } else {
                printDebug("Il gruppo condiviso è già impostato per il percorso: " + path);
            }

            // Verifica e aggiorna i permessi
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(dirPath);
            Set<PosixFilePermission> requiredPermissions = EnumSet.of(
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE
            );

            if (!permissions.containsAll(requiredPermissions)) {
                printDebug("Permessi insufficienti per il gruppo '" + sharedGroupName + "' sul percorso: " + path);
                permissions.addAll(requiredPermissions);
                Files.setPosixFilePermissions(dirPath, permissions);
                printDebug("Permessi aggiornati con successo per il gruppo '" + sharedGroupName + "' sul percorso: " + path);
            } else {
                printDebug("Permessi già completi per il gruppo '" + sharedGroupName + "' sul percorso: " + path);
            }
        }

        printDebug("Impostazione permessi completata per l'utente: " + username);
    }

}
