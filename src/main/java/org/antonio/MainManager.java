package org.antonio;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;

public class MainManager {

    private SambaManager sambaManager;
    private FtpManager ftpManager;
    private UsersManager usersManager;

    public MainManager() {
        try {
            // Controlla se Samba e vsftpd sono installati
            if (!isPackageInstalled("samba")) {
                showErrorDialog("Errore: Samba non è installato.");
                System.exit(1);
            }
            if (!isPackageInstalled("vsftpd")) {
                showErrorDialog("Errore: vsftpd non è installato.");
                System.exit(1);
            }

            // Controlla se i servizi Samba e vsftpd sono attivi
            if (!isServiceActive("smbd")) {
                showErrorDialog("Errore: il servizio Samba (smbd) non è attivo.");
                System.exit(1);
            }
            if (!isServiceActive("vsftpd")) {
                showErrorDialog("Errore: il servizio vsftpd non è attivo.");
                System.exit(1);
            }

            // Inizializza i manager
            sambaManager = new SambaManager("/etc/samba/smb.conf");
            ftpManager = new FtpManager("/etc/vsftpd.conf", "/etc/vsftpd.userlist");
            usersManager = new UsersManager(sambaManager, ftpManager);

            System.out.println("MainManager inizializzato correttamente.");
        } catch (IOException e) {
            showErrorDialog("Errore durante l'inizializzazione: " + e.getMessage());
            System.exit(1);
        }
    }

    // Mostra un dialogo di errore
    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(
                null,
                message,
                "Errore",
                JOptionPane.ERROR_MESSAGE
        );
        System.err.println(message);
    }

    // Verifica se un pacchetto è installato
    private boolean isPackageInstalled(String packageName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("dpkg", "-l", packageName);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            System.err.println("Errore durante il controllo del pacchetto " + packageName + ": " + e.getMessage());
            return false;
        }
    }

    // Verifica se un servizio è attivo
    private boolean isServiceActive(String serviceName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("systemctl", "is-active", "--quiet", serviceName);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            System.err.println("Errore durante il controllo del servizio " + serviceName + ": " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        new MainManager(); // Auto-istanziazione della classe
    }
}

