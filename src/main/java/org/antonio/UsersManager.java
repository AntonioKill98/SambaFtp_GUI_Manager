package org.antonio;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class UsersManager {
    private static final String FTP_USERS_FILE = "/etc/vsftpd.userlist"; // File lista utenti FTP
    private ArrayList<UserBean> users;

    public UsersManager() throws IOException {
        this.users = new ArrayList<>();
        loadUsers();
    }

    // Carica la lista degli utenti dal sistema e verifica Samba/FTP
    private void loadUsers() throws IOException {
        users.clear();

        // Ottieni lista utenti dal file /etc/passwd
        List<String> passwdLines = Files.readAllLines(Paths.get("/etc/passwd"));

        // Carica gli utenti verificando i permessi per FTP e Samba
        for (String line : passwdLines) {
            String[] parts = line.split(":");
            if (parts.length > 0) {
                String username = parts[0];
                boolean sambaEnabled = isSambaEnabled(username);
                boolean ftpEnabled = isFtpEnabled(username);
                users.add(new UserBean(username, sambaEnabled, ftpEnabled));
            }
        }
    }

    // Verifica se un utente è abilitato in Samba
    private boolean isSambaEnabled(String username) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("pdbedit", "-L", "-u", username);
        Process process = pb.start();
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            throw new IOException("Errore durante la verifica Samba per l'utente " + username, e);
        }
        return exitCode == 0;
    }

    // Verifica se un utente è nella lista utenti di FTP
    private boolean isFtpEnabled(String username) throws IOException {
        Path path = Paths.get(FTP_USERS_FILE);
        if (Files.exists(path)) {
            List<String> ftpUsers = Files.readAllLines(path);
            return ftpUsers.contains(username);
        }
        return false;
    }

    // Ritorna un array con i soli nomi degli utenti
    public String[] getUsernames() {
        return users.stream().map(UserBean::getUsername).toArray(String[]::new);
    }

    // Ritorna la lista completa di utenti (bean)
    public ArrayList<UserBean> getUsers() {
        return new ArrayList<>(users);
    }

    // Aggiunge un nuovo utente al sistema
    public void addUser(String username, String password) throws IOException {
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

    // Rimuove un utente dal sistema
    public void removeUser(String username) throws IOException {
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

    // Abilita l'accesso a Samba
    public void enableSamba(String username, String password) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "smbpasswd", "-a", username);
        Process process = pb.start();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
            writer.write(password);
            writer.newLine();
            writer.write(password);
            writer.newLine();
            writer.flush();
        }
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Errore durante l'abilitazione Samba per l'utente " + username);
            }
        } catch (InterruptedException e) {
            throw new IOException("Errore durante l'abilitazione Samba per l'utente " + username, e);
        }
        loadUsers(); // Ricarica la lista utenti
    }

    // Disabilita l'accesso a Samba
    public void disableSamba(String username) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "smbpasswd", "-x", username);
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Errore durante la disabilitazione Samba per l'utente " + username);
            }
        } catch (InterruptedException e) {
            throw new IOException("Errore durante la disabilitazione Samba per l'utente " + username, e);
        }
        loadUsers(); // Ricarica la lista utenti
    }

    // Abilita l'accesso a FTP
    public void enableFtp(String username) throws IOException {
        Path path = Paths.get(FTP_USERS_FILE);
        List<String> ftpUsers = Files.exists(path) ? Files.readAllLines(path) : new ArrayList<>();
        if (!ftpUsers.contains(username)) {
            ftpUsers.add(username);
            Files.write(path, ftpUsers);
        }
        loadUsers(); // Ricarica la lista utenti
    }

    // Disabilita l'accesso a FTP
    public void disableFtp(String username) throws IOException {
        Path path = Paths.get(FTP_USERS_FILE);
        if (Files.exists(path)) {
            List<String> ftpUsers = Files.readAllLines(path);
            ftpUsers.remove(username);
            Files.write(path, ftpUsers);
        }
        loadUsers(); // Ricarica la lista utenti
    }
}

