package org.antonio;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class UsersManager {
    private ArrayList<UserBean> users;
    private SambaManager sambaManager;
    private FtpManager ftpManager;

    public UsersManager(SambaManager sambaManager, FtpManager ftpManager) throws IOException {
        this.sambaManager = sambaManager;
        this.ftpManager = ftpManager;
        this.users = new ArrayList<>();
        loadUsers();
    }

    // Carica la lista degli utenti dal sistema e imposta i permessi FTP e Samba
    private void loadUsers() throws IOException {
        users.clear();

        // Ottieni lista utenti dal file /etc/passwd
        List<String> passwdLines = Files.readAllLines(Paths.get("/etc/passwd"));

        for (String line : passwdLines) {
            String[] parts = line.split(":");
            if (parts.length > 6) { // Controlla che ci siano abbastanza campi
                String username = parts[0];
                String shell = parts[6]; // Ultimo campo: shell

                // Include solo utenti con shell valida per il login
                if (!shell.equals("/usr/sbin/nologin") && !shell.equals("/bin/false")) {
                    boolean sambaEnabled = sambaManager.getSambaUsers().contains(username);
                    boolean ftpEnabled = ftpManager.getFtpUsers().contains(username);
                    users.add(new UserBean(username, sambaEnabled, ftpEnabled));
                }
            }
        }
    }

    // Ritorna un array con i soli nomi degli utenti
    public String[] getUsernames() {
        return users.stream().map(UserBean::getUsername).toArray(String[]::new);
    }

    // Ritorna la lista completa di utenti (bean)
    public ArrayList<UserBean> getUsers() {
        return new ArrayList<>(users);
    }

    // Aggiunge un nuovo utente al sistema, abilitandolo opzionalmente a FTP e Samba
    public void addUser(String username, String password, boolean enableFtp, boolean enableSamba) throws IOException {
        // Aggiunge l'utente al sistema
        ProcessBuilder pb = new ProcessBuilder("sudo", "useradd", "-m", username);
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Errore durante l'aggiunta dell'utente " + username);
            }
        } catch (InterruptedException e) {
            throw new IOException("Errore durante l'aggiunta dell'utente " + username, e);
        }

        // Imposta la password
        setPassword(username, password);

        // Abilita Samba e FTP se richiesto
        if (enableSamba) {
            sambaManager.addSambaUser(username, password);
        }
        if (enableFtp) {
            ftpManager.addFtpUser(username);
        }

        loadUsers(); // Ricarica la lista utenti
    }

    // Imposta la password di un utente
    private void setPassword(String username, String password) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "passwd", "--stdin", username);
        Process process = pb.start();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
            writer.write(password);
            writer.newLine();
            writer.flush();
        }
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Errore durante l'impostazione della password per l'utente " + username);
            }
        } catch (InterruptedException e) {
            throw new IOException("Errore durante l'impostazione della password per l'utente " + username, e);
        }
    }

    // Rimuove un utente dal sistema e disabilita Samba e FTP
    public void removeUser(String username) throws IOException {
        // Rimuovi le condivisioni e disabilita gli accessi
        ArrayList<SmbCondBean> sambaShares = sambaManager.getSharesByUser(username);
        for (SmbCondBean share : sambaShares) {
            sambaManager.removeUserFromShare(share.getName(), username);
        }
        sambaManager.removeSambaUser(username);

        ArrayList<FtpCondBean> ftpShares = ftpManager.getSharesByUser(username);
        for (FtpCondBean share : ftpShares) {
            ftpManager.removeShare(share);
        }
        ftpManager.removeFtpUser(username);

        // Elimina l'utente dal sistema
        ProcessBuilder pb = new ProcessBuilder("sudo", "userdel", "-r", username);
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Errore durante la rimozione dell'utente " + username);
            }
        } catch (InterruptedException e) {
            throw new IOException("Errore durante la rimozione dell'utente " + username, e);
        }

        loadUsers(); // Ricarica la lista utenti
    }

    // Aggiorna lo stato degli utenti, abilitando/disabilitando Samba e FTP in base ai booleani
    public void updateUsers(String password) throws IOException {
        for (UserBean user : users) {
            String username = user.getUsername();
            boolean sambaEnabled = sambaManager.getSambaUsers().contains(username);
            boolean ftpEnabled = ftpManager.getFtpUsers().contains(username);

            // Gestione di Samba
            if (user.isSambaEnabled() && !sambaEnabled) {
                // Aggiunge l'utente a Samba se abilitato ma non presente
                if (password != null && !password.isEmpty()) {
                    sambaManager.addSambaUser(username, password);
                } else {
                    throw new IllegalArgumentException("Password necessaria per abilitare Samba per l'utente " + username);
                }
            } else if (!user.isSambaEnabled() && sambaEnabled) {
                // Rimuove l'utente da Samba se disabilitato ma presente
                sambaManager.removeSambaUser(username);
            }

            // Gestione di FTP
            if (user.isFtpEnabled() && !ftpEnabled) {
                // Aggiunge l'utente a FTP se abilitato ma non presente
                ftpManager.addFtpUser(username);
            } else if (!user.isFtpEnabled() && ftpEnabled) {
                // Rimuove l'utente da FTP se disabilitato ma presente
                ftpManager.removeFtpUser(username);
            }
        }
    }
}
