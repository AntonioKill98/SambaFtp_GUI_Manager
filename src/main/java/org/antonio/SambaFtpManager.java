package org.antonio;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SambaFtpManager {
    public static void main(String[] args) {
        // Avvia la GUI
        SwingUtilities.invokeLater(SambaFtpManager::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Gestione Server SAMBA e FTP");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);
        frame.setLayout(new BorderLayout());

        // Pannello superiore: Stato servizi
        JPanel statusPanel = new JPanel(new GridLayout(1, 2));
        JLabel sambaStatusLabel = new JLabel();
        JLabel ftpStatusLabel = new JLabel();
        updateServiceStatus(sambaStatusLabel, "smbd");
        updateServiceStatus(ftpStatusLabel, "vsftpd");
        statusPanel.add(sambaStatusLabel);
        statusPanel.add(ftpStatusLabel);

        // Pannello sinistro: Elenco utenti
        JPanel userPanel = new JPanel(new BorderLayout());
        JLabel userLabel = new JLabel("Utenti del sistema:");
        DefaultListModel<String> userListModel = new DefaultListModel<>();
        getSystemUsers().forEach(userListModel::addElement);
        JList<String> userList = new JList<>(userListModel);
        JScrollPane userScrollPane = new JScrollPane(userList);

        // Pulsante "Aggiungi Utente"
        JButton addUserButton = new JButton("Aggiungi Utente");
        addUserButton.addActionListener(e -> addUser(frame, userListModel));

        userPanel.add(userLabel, BorderLayout.NORTH);
        userPanel.add(userScrollPane, BorderLayout.CENTER);
        userPanel.add(addUserButton, BorderLayout.SOUTH);

        // Pannello destro: Gestione permessi e directory
        JPanel permissionPanel = new JPanel();
        permissionPanel.setLayout(new BoxLayout(permissionPanel, BoxLayout.Y_AXIS));
        JLabel permissionLabel = new JLabel();
        JCheckBox sambaCheckBox = new JCheckBox("Abilita a SAMBA");
        JCheckBox ftpCheckBox = new JCheckBox("Abilita a FTP");
        JButton saveButton = new JButton("Salva Modifiche");
        JButton deleteUserButton = new JButton("Elimina Utente");
        deleteUserButton.setForeground(Color.RED);

        // Pannello per gestione directory
        JLabel directoryLabel = new JLabel("Directory associate:");
        DefaultListModel<String> directoryListModel = new DefaultListModel<>();
        JList<String> directoryList = new JList<>(directoryListModel);
        JScrollPane directoryScrollPane = new JScrollPane(directoryList);
        JButton addDirectoryButton = new JButton("Aggiungi Directory");
        JButton removeDirectoryButton = new JButton("Rimuovi Directory");

        permissionPanel.add(permissionLabel);
        permissionPanel.add(sambaCheckBox);
        permissionPanel.add(ftpCheckBox);
        permissionPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        permissionPanel.add(saveButton);
        permissionPanel.add(deleteUserButton);
        permissionPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        permissionPanel.add(directoryLabel);
        permissionPanel.add(directoryScrollPane);
        permissionPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        permissionPanel.add(addDirectoryButton);
        permissionPanel.add(removeDirectoryButton);

        permissionPanel.setVisible(false); // Nascondi inizialmente il pannello dei permessi

        // Listener per selezione utente
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && userList.getSelectedValue() != null) {
                String selectedUser = userList.getSelectedValue();
                permissionLabel.setText("Gestione permessi per: " + selectedUser);

                boolean isSambaEnabledInitially = isSambaEnabled(selectedUser);
                boolean isFtpEnabledInitially = isFtpEnabled(selectedUser);

                sambaCheckBox.setSelected(isSambaEnabledInitially);
                ftpCheckBox.setSelected(isFtpEnabledInitially);

                // Listener per abilitare/disabilitare il pulsante "Salva Modifiche"
                ActionListener updateSaveButtonState = evt -> {
                    boolean hasChanges = (sambaCheckBox.isSelected() != isSambaEnabledInitially) ||
                            (ftpCheckBox.isSelected() != isFtpEnabledInitially);
                    saveButton.setEnabled(hasChanges);
                };

                // Rimuovi i listener precedenti e aggiungi quelli nuovi
                for (ActionListener al : sambaCheckBox.getActionListeners()) {
                    sambaCheckBox.removeActionListener(al);
                }
                for (ActionListener al : ftpCheckBox.getActionListeners()) {
                    ftpCheckBox.removeActionListener(al);
                }
                sambaCheckBox.addActionListener(updateSaveButtonState);
                ftpCheckBox.addActionListener(updateSaveButtonState);

                // Aggiorna la lista delle directory associate
                directoryListModel.clear();
                getDirectoriesForUser(selectedUser).forEach(directoryListModel::addElement);

                saveButton.setEnabled(false); // Disabilita inizialmente il pulsante
                permissionPanel.setVisible(true); // Mostra il pannello
            }
        });

        // Listener per "Salva Modifiche"
        saveButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            if (selectedUser != null) {
                if (sambaCheckBox.isSelected()) enableSamba(selectedUser);
                else disableSamba(selectedUser);

                if (ftpCheckBox.isSelected()) enableFtp(selectedUser);
                else disableFtp(selectedUser);

                JOptionPane.showMessageDialog(frame, "Modifiche salvate per l'utente " + selectedUser,
                        "Informazione", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // Listener per "Elimina Utente"
        deleteUserButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            if (selectedUser != null) {
                int choice = JOptionPane.showConfirmDialog(frame,
                        "Confermi l'eliminazione dell'utente " + selectedUser + "?",
                        "Conferma Eliminazione",
                        JOptionPane.OK_CANCEL_OPTION);
                if (choice == JOptionPane.OK_OPTION) {
                    deleteUser(selectedUser);
                    userListModel.removeElement(selectedUser);
                    permissionPanel.setVisible(false);
                    JOptionPane.showMessageDialog(frame, "Utente " + selectedUser + " eliminato con successo.",
                            "Informazione", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        // Listener per "Aggiungi Directory"
        addDirectoryButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            if (selectedUser == null) {
                JOptionPane.showMessageDialog(frame, "Seleziona un utente prima di aggiungere una directory.",
                        "Errore", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int option = fileChooser.showOpenDialog(frame);
            if (option == JFileChooser.APPROVE_OPTION) {
                String directoryPath = fileChooser.getSelectedFile().getAbsolutePath();
                addDirectoryWithDetails(frame, selectedUser, directoryPath, directoryListModel);
            }
        });

        // Listener per "Rimuovi Directory"
        removeDirectoryButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            String selectedDirectory = directoryList.getSelectedValue();
            if (selectedUser == null || selectedDirectory == null) {
                JOptionPane.showMessageDialog(frame, "Seleziona un utente e una directory prima di procedere.",
                        "Errore", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(frame,
                    "Sei sicuro di voler rimuovere la directory " + selectedDirectory + " per l'utente " + selectedUser + "?",
                    "Conferma Rimozione",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                removeDirectoryForUser(selectedUser, selectedDirectory);
                directoryListModel.removeElement(selectedDirectory);
            }
        });

        // Aggiungi i pannelli al frame
        frame.add(statusPanel, BorderLayout.NORTH);
        frame.add(userPanel, BorderLayout.WEST);
        frame.add(permissionPanel, BorderLayout.CENTER);

        frame.setVisible(true);
    }

    private static void addDirectoryWithDetails(JFrame frame, String username, String directoryPath, DefaultListModel<String> directoryListModel) {
        // Prompt per nome condivisione, commento e permessi
        JTextField shareNameField = new JTextField();
        JTextField commentField = new JTextField();
        JCheckBox writableCheckBox = new JCheckBox("Writable", true);
        JCheckBox browsableCheckBox = new JCheckBox("Browsable", true);
        Object[] message = {
                "Nome condivisione:", shareNameField,
                "Commento:", commentField,
                writableCheckBox,
                browsableCheckBox
        };

        int confirm = JOptionPane.showConfirmDialog(frame, message, "Configura Condivisione", JOptionPane.OK_CANCEL_OPTION);
        if (confirm == JOptionPane.OK_OPTION) {
            String shareName = shareNameField.getText().trim();
            String comment = commentField.getText().trim();
            boolean writable = writableCheckBox.isSelected();
            boolean browsable = browsableCheckBox.isSelected();

            if (!shareName.isEmpty()) {
                addDirectoryForUser(username, directoryPath, shareName, comment, writable, browsable);
                directoryListModel.addElement(directoryPath); // Aggiorna la lista
            } else {
                JOptionPane.showMessageDialog(frame, "Nome condivisione non valido.",
                        "Errore", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    //Lista delle directory condivise per un determinato utente
    private static List<String> getDirectoriesForUser(String username) {
        List<String> directories = new ArrayList<>();

        // Leggi le directory dal file smb.conf
        try (BufferedReader reader = new BufferedReader(new FileReader("/etc/samba/smb.conf"))) {
            String line;
            boolean isUserBlock = false;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[") && line.endsWith("]")) {
                    isUserBlock = line.contains(username); // Trova il blocco per l'utente
                } else if (isUserBlock && line.trim().startsWith("path = ")) {
                    directories.add(line.trim().replace("path = ", ""));
                    isUserBlock = false; // Chiudi il blocco dopo aver trovato il path
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Errore durante la lettura delle directory condivise in Samba: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }

        // Aggiungi eventuali bind mount trovati nella home dell'utente
        File userHome = new File("/home/" + username);
        if (userHome.exists() && userHome.isDirectory()) {
            File[] files = userHome.listFiles();
            if (files != null) {
                for (File file : files) {
                    try {
                        if (isBindMount(file.toPath())) { // Verifica se è un bind mount
                            directories.add(file.getAbsolutePath());
                        }
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(null, "Errore durante il controllo dei bind mount: " + e.getMessage(),
                                "Errore", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }

        return directories;
    }

    //Verifica se un File è un BindMount
    private static boolean isBindMount(Path path) {
        try {
            // Leggi /proc/self/mountinfo per trovare i bind mount
            List<String> mountInfo = Files.readAllLines(Path.of("/proc/self/mountinfo"));
            for (String line : mountInfo) {
                if (line.contains(path.toAbsolutePath().toString())) {
                    return true;
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Errore durante la verifica dei bind mount: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    // Funzione per aggiungere una directory a Samba e FTP
    private static void addDirectoryForUser(String username, String directoryPath, String shareName, String comment, boolean writable, boolean browsable) {
        try {
            // Controlla se la directory è già condivisa in Samba
            boolean isAlreadyShared = false;
            String existingShareName = null;

            try (BufferedReader reader = new BufferedReader(new FileReader("/etc/samba/smb.conf"))) {
                String line;
                boolean insideBlock = false;

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("[") && line.endsWith("]")) {
                        insideBlock = false; // Uscita da un blocco precedente
                        existingShareName = line.substring(1, line.length() - 1); // Nome della condivisione
                    }
                    if (line.trim().equals("path = " + directoryPath)) {
                        isAlreadyShared = true;
                        insideBlock = true;
                    }
                    if (insideBlock && line.trim().startsWith("valid users =")) {
                        // Aggiungi l'utente alla lista
                        if (!line.contains(username)) {
                            String updatedLine = line.trim() + " " + username;
                            modifySambaConfig(existingShareName, line, updatedLine);
                        }
                        break; // Utente aggiunto, esci dal ciclo
                    }
                }
            }

            // Se la directory non è ancora condivisa, configura una nuova condivisione
            if (!isAlreadyShared) {
                configureSambaForDirectory(shareName, directoryPath, comment, writable, browsable, username);
            }

            // Crea un nuovo bind mount per l'utente
            createBindMountForUser(username, directoryPath);

            JOptionPane.showMessageDialog(null, "Directory aggiunta con successo.",
                    "Informazione", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante l'aggiunta della directory: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void modifySambaConfig(String shareName, String oldLine, String newLine) throws IOException, InterruptedException {
        File smbConf = new File("/etc/samba/smb.conf");
        File tempFile = new File("/etc/samba/smb.conf.tmp");

        try (BufferedReader reader = new BufferedReader(new FileReader(smbConf));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {

            String line;
            boolean insideBlock = false;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("[") && line.endsWith("]")) {
                    insideBlock = line.substring(1, line.length() - 1).equals(shareName);
                }

                if (insideBlock && line.trim().equals(oldLine.trim())) {
                    writer.write(newLine);
                    writer.newLine();
                } else {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }

        // Sovrascrivi il file originale
        if (!tempFile.renameTo(smbConf)) {
            throw new IOException("Errore nel sovrascrivere il file smb.conf.");
        }

        // Riavvia Samba per applicare la configurazione aggiornata
        Runtime.getRuntime().exec("sudo systemctl restart smbd").waitFor();
    }


    // Funzione per configurare Samba
    private static void configureSambaForDirectory(String shareName, String directoryPath, String comment, boolean writable, boolean browsable, String username) throws IOException, InterruptedException {
        String sambaConfig = String.format(
                "[%s]\ncomment = %s\npath = %s\nbrowsable = %s\nwritable = %s\nguest ok = no\ncreate mask = 0664\ndirectory mask = 0775\nvalid users = %s\n",
                shareName, comment, directoryPath, browsable ? "yes" : "no", writable ? "yes" : "no", username);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("/etc/samba/smb.conf", true))) {
            writer.write(sambaConfig);
            writer.newLine();
        }

        Runtime.getRuntime().exec("sudo systemctl restart smbd").waitFor();
    }




    // Funzione per creare un bind mount per FTP
    private static void createBindMountForUser(String username, String directoryPath) throws IOException {
        String userHome = "/home/" + username;
        String bindMountPath = userHome + "/" + new File(directoryPath).getName();

        try {
            // Crea la directory di destinazione se non esiste
            File bindMountDir = new File(bindMountPath);
            if (!bindMountDir.exists()) {
                if (!bindMountDir.mkdir()) {
                    throw new IOException("Impossibile creare la directory per il bind mount: " + bindMountPath);
                }
            }

            // Esegui il bind mount
            String command = String.format("sudo mount --bind %s %s", directoryPath, bindMountPath);
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Errore durante il bind mount. Comando: " + command);
            }

            // Aggiungi il bind mount a /etc/fstab
            String fstabEntry = String.format("%s %s none bind 0 0\n", directoryPath, bindMountPath);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("/etc/fstab", true))) {
                writer.write(fstabEntry);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operazione di bind mount interrotta", e);
        }
    }


    // Funzione per rimuovere directory da Samba e FTP
    private static void removeDirectoryForUser(String username, String directoryPath) {
        try {
            // Rimuovi dal file smb.conf
            removeSambaConfiguration(directoryPath);

            // Rimuovi il bind mount
            removeBindMountForUser(username, directoryPath);

            JOptionPane.showMessageDialog(null, "Directory rimossa con successo per l'utente " + username,
                    "Informazione", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante la rimozione della directory: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }


    // Funzione per rimuovere BindMount per FTP
    private static void removeBindMountForUser(String username, String directoryPath) throws IOException {
        String userHome = "/home/" + username;
        String bindMountPath = userHome + "/" + new File(directoryPath).getName();

        try {
            // Smonta il bind mount
            String command = String.format("sudo umount %s", bindMountPath);
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Errore durante lo smontaggio del bind mount. Comando: " + command);
            }

            // Rimuovi l'entry da /etc/fstab
            File fstabFile = new File("/etc/fstab");
            File tempFile = new File("/etc/fstab.tmp");
            try (BufferedReader reader = new BufferedReader(new FileReader(fstabFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith(directoryPath + " ")) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }

            if (!tempFile.renameTo(fstabFile)) {
                throw new IOException("Errore nel sovrascrivere il file /etc/fstab.");
            }

            // Elimina la directory vuota
            File bindMountDir = new File(bindMountPath);
            if (bindMountDir.exists() && !bindMountDir.delete()) {
                throw new IOException("Impossibile eliminare la directory del bind mount: " + bindMountPath);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operazione di smontaggio del bind mount interrotta", e);
        }
    }

    private static void removeSambaConfiguration(String directoryPath) throws IOException, InterruptedException {
        File smbConf = new File("/etc/samba/smb.conf");
        File tempFile = new File("/etc/samba/smb.conf.tmp");

        try (BufferedReader reader = new BufferedReader(new FileReader(smbConf));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {

            String line;
            boolean insideBlock = false; // Traccia se sei all'interno di un blocco
            boolean skipBlock = false;  // Indica se saltare il blocco corrente

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Controlla se è l'inizio di un nuovo blocco
                if (line.startsWith("[") && line.endsWith("]")) {
                    if (insideBlock && !skipBlock) {
                        // Scrivi una riga vuota per separare i blocchi
                        writer.newLine();
                    }
                    insideBlock = true;

                    // Verifica se il blocco corrisponde a quello da rimuovere
                    String blockName = line.substring(1, line.length() - 1);
                    if (blockName.equalsIgnoreCase(directoryPath)) {
                        skipBlock = true;
                    } else {
                        skipBlock = false;
                        writer.write(line);
                        writer.newLine();
                    }
                } else if (insideBlock && skipBlock) {
                    // Non scrivere righe all'interno di un blocco da rimuovere
                    continue;
                } else if (insideBlock) {
                    // Scrivi righe all'interno di un blocco non da rimuovere
                    writer.write(line);
                    writer.newLine();
                }
            }
        }

        // Sovrascrivi il file originale con il temporaneo
        if (!tempFile.renameTo(smbConf)) {
            throw new IOException("Errore nel sovrascrivere il file smb.conf.");
        }

        // Riavvia Samba
        Process process = Runtime.getRuntime().exec("sudo systemctl restart smbd");
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Errore durante il riavvio di Samba.");
        }
    }


    private static boolean isBlockForDirectory(BufferedReader reader, String directoryPath) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().startsWith("path = ")) {
                String configuredPath = line.trim().replace("path = ", "");
                return configuredPath.equals(directoryPath);
            }
            if (line.trim().isEmpty()) {
                break; // Fine del blocco
            }
        }
        return false;
    }

    // Funzione per aggiungere un utente
    private static void addUser(JFrame frame, DefaultListModel<String> userListModel) {
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        Object[] message = {
                "Nome utente:", usernameField,
                "Password:", passwordField
        };

        int option = JOptionPane.showConfirmDialog(frame, message, "Aggiungi Utente", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();

            if (!username.isEmpty() && !password.isEmpty()) {
                addUserToUnix(username, password);
                userListModel.addElement(username);
                JOptionPane.showMessageDialog(frame, "Aggiunto utente " + username + " con password: " + password,
                        "Informazione", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Nome utente o password non validi.",
                        "Errore", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Funzione per aggiungere un utente a Unix
    private static void addUserToUnix(String username, String password) {
        try {
            // Aggiunge l'utente al sistema
            ProcessBuilder addUserBuilder = new ProcessBuilder("sudo", "useradd", "-m", username);
            int addUserExitCode = addUserBuilder.start().waitFor();

            if (addUserExitCode == 0) {
                // Imposta la password dell'utente
                ProcessBuilder passwdBuilder = new ProcessBuilder("sudo", "passwd", username);
                Process passwdProcess = passwdBuilder.start();

                // Scrive la password al processo passwd
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(passwdProcess.getOutputStream()))) {
                    writer.write(password + "\n"); // Prima riga: password
                    writer.write(password + "\n"); // Seconda riga: ripeti password
                    writer.flush();
                }

                int passwdExitCode = passwdProcess.waitFor();

                if (passwdExitCode != 0) {
                    throw new Exception("Errore durante l'impostazione della password.");
                }
            } else {
                throw new Exception("Errore durante la creazione dell'utente.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante l'aggiunta dell'utente: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }


    // Funzione per eliminare un utente da Unix
    private static void deleteUser(String username) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sudo", "userdel", "-r", username);
            processBuilder.start().waitFor();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante l'eliminazione dell'utente: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Aggiorna lo stato dei servizi
    private static void updateServiceStatus(JLabel label, String serviceName) {
        boolean isRunning = isServiceRunning(serviceName);
        label.setText(serviceName.toUpperCase() + ": " + (isRunning ? "ATTIVO" : "INATTIVO"));
        label.setForeground(isRunning ? Color.GREEN : Color.RED);
    }


    // Controlla se un servizio è in esecuzione
    private static boolean isServiceRunning(String serviceName) {
        try {
            ProcessBuilder builder = new ProcessBuilder("systemctl", "is-active", "--quiet", serviceName);
            Process process = builder.start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    // Ottiene gli utenti di sistema
    private static List<String> getSystemUsers() {
        List<String> users = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ProcessBuilder("awk", "-F:", "$3 >= 1000 && $3 < 65534 {print $1}", "/etc/passwd").start().getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                users.add(line);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante il recupero degli utenti di sistema: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
        return users;
    }


    // Controlla se un utente è abilitato a SAMBA
    private static boolean isSambaEnabled(String username) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("pdbedit", "-L");
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(username + ":")) {
                    return true; // Utente trovato
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante la verifica dell'utente SAMBA: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
        return false; // Utente non trovato
    }


    // Controlla se un utente è abilitato a FTP (placeholder)
    private static boolean isFtpEnabled(String username) {
        File userlistFile = new File("/etc/vsftpd.userlist");

        if (!userlistFile.exists()) {
            return false; // Se il file non esiste, nessun utente è abilitato
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(userlistFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals(username)) {
                    return true; // Utente trovato
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante la verifica dell'utente FTP: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
        return false; // Utente non trovato
    }


    // Abilita un utente a SAMBA
    private static void enableSamba(String username) {
        try {
            // Chiedi la password all'utente tramite un dialogo
            String password = askForPassword(username);
            if (password == null || password.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Operazione annullata. Nessuna password fornita.",
                        "Informazione", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Comando per aggiungere l'utente a SAMBA
            ProcessBuilder processBuilder = new ProcessBuilder("sudo", "smbpasswd", "-a", username);
            Process process = processBuilder.start();

            // Fornisci la password direttamente al processo
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write(password + "\n"); // Prima riga: password
                writer.write(password + "\n"); // Seconda riga: ripeti password
                writer.flush();
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                JOptionPane.showMessageDialog(null, "Utente " + username + " abilitato a SAMBA con successo.\n" +
                                "Con password: " + password,
                        "Informazione", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null, "Errore durante l'abilitazione a SAMBA per l'utente " + username + ".",
                        "Errore", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante l'abilitazione a SAMBA: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Metodo per chiedere la password all'utente
    private static String askForPassword(String username) {
        JPasswordField passwordField = new JPasswordField();
        Object[] message = {
                "Inserisci la password per l'utente: " + username,
                passwordField
        };

        int option = JOptionPane.showConfirmDialog(null, message, "Imposta Password", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            return new String(passwordField.getPassword());
        }
        return null; // Se l'utente annulla
    }


    // Disabilita un utente da SAMBA
    private static void disableSamba(String username) {
        try {
            // Comando per disabilitare l'utente da SAMBA usando `smbpasswd -x`
            ProcessBuilder processBuilder = new ProcessBuilder("smbpasswd", "-x", username);
            processBuilder.inheritIO(); // Mostra eventuali messaggi nel terminale per debugging
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            // Controlla l'esito del comando
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(null, "Utente " + username + " rimosso da SAMBA con successo.",
                        "Informazione", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(null, "Errore durante la rimozione dell'utente " + username + " da SAMBA.",
                        "Errore", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante la disabilitazione da SAMBA: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }


    // Abilita un utente a FTP
    private static void enableFtp(String username) {
        File userlistFile = new File("/etc/vsftpd.userlist");

        try {
            // Verifica che il file esista
            if (!userlistFile.exists()) {
                throw new Exception("Il file vsftpd.userlist non esiste. Verifica la configurazione di FTP.");
            }

            // Controlla se l'utente è già abilitato
            if (isFtpEnabled(username)) {
                JOptionPane.showMessageDialog(null, "L'utente " + username + " è già abilitato a FTP.",
                        "Informazione", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Aggiungi l'utente al file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(userlistFile, true))) {
                writer.write(username);
                writer.newLine();
            }

            JOptionPane.showMessageDialog(null, "Utente " + username + " abilitato a FTP con successo.",
                    "Informazione", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante l'abilitazione a FTP: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Disabilita un utente da FTP
    private static void disableFtp(String username) {
        File userlistFile = new File("/etc/vsftpd.userlist");
        File tempFile = new File("/etc/vsftpd.userlist.tmp");

        try {
            // Verifica che il file esista
            if (!userlistFile.exists()) {
                throw new Exception("Il file vsftpd.userlist non esiste. Verifica la configurazione di FTP.");
            }

            // Rimuovi l'utente dal file creando un file temporaneo
            try (BufferedReader reader = new BufferedReader(new FileReader(userlistFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().equals(username)) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }

            // Sovrascrivi il file originale con il file temporaneo
            if (!tempFile.renameTo(userlistFile)) {
                throw new Exception("Errore durante la modifica del file vsftpd.userlist.");
            }

            JOptionPane.showMessageDialog(null, "Utente " + username + " disabilitato da FTP con successo.",
                    "Informazione", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Errore durante la disabilitazione da FTP: " + e.getMessage(),
                    "Errore", JOptionPane.ERROR_MESSAGE);
        } finally {
            // Elimina il file temporaneo se esiste ancora
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
