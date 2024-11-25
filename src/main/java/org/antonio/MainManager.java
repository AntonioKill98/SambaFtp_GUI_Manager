package org.antonio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.io.*;
import java.awt.*;
import java.lang.reflect.Array;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class MainManager {

    private SambaManager sambaManager;
    private FtpManager ftpManager;
    private UsersManager usersManager;
    private JFrame mainFrame;
    private JList<String> userList;
    private JCheckBox ftpCheckbox, sambaCheckbox;
    private JList<String> ftpShareList, sambaShareList;
    private JButton manageUserButton, deleteShareButton, addShareButton, sambaButton, ftpButton, infoShareButton;
    private JLabel sambaStatusLabel, ftpStatusLabel;
    private JPanel userDetailPanel, configButtonPanel, mainPanel, statusPanel;

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
            boolean isSambaActive = isServiceActive("smbd");
            boolean isFtpActive = isServiceActive("vsftpd");

            // Inizializza i manager
            sambaManager = new SambaManager("/etc/samba/smb.conf");
            ftpManager = new FtpManager("/etc/vsftpd.conf", "/etc/vsftpd.userlist");
            usersManager = new UsersManager(sambaManager, ftpManager);

            //Sezione di Debug
            debugShares(sambaManager, ftpManager);

            // Crea la GUI
            initializeGUI(isSambaActive, isFtpActive);

            // Avvia il timer per l'aggiornamento dello stato
            startStatusUpdateTimer();

            System.out.println("MainManager inizializzato correttamente.");
        } catch (IOException e) {
            showErrorDialog("Errore durante l'inizializzazione: " + e.getMessage());
            System.exit(1);
        }
    }

    private void debugShares(SambaManager sambaManager, FtpManager ftpManager) {
        System.out.println("=== DEBUG: Samba Shares ===");
        ArrayList<SmbCondBean> sambaTemp = sambaManager.getAllShares();
        int i = 0;
        for (SmbCondBean singleSmb : sambaTemp) {
            System.out.println("SambaShare n" + i++);
            System.out.println("Name: " + singleSmb.getName());

            System.out.println("Properties:");
            for (String[] property : singleSmb.getProperties()) {
                System.out.println("  Key: " + property[0] + ", Value: " + property[1]);
            }

            System.out.println("Valid Users: " + String.join(", ", singleSmb.getValidUsers()));
            System.out.println("---");
        }

        System.out.println("=== DEBUG: FTP Shares ===");
        ArrayList<FtpCondBean> ftpTemp = ftpManager.getFtpShares();
        i = 0;
        for (FtpCondBean singleFTP : ftpTemp) {
            System.out.println("FTPShare n" + i++);
            System.out.println("Username: " + singleFTP.getUsername());
            System.out.println("Share Name: " + singleFTP.getShareName());
            System.out.println("Path: " + singleFTP.getPath());
            System.out.println("---");
        }
    }

    // Avvia un timer per aggiornare periodicamente lo stato dei servizi
    private void startStatusUpdateTimer() {
        int delay = 5000; // Intervallo di aggiornamento in millisecondi (5 secondi)
        Timer timer = new Timer(delay, e -> updateServiceStatus());
        timer.start();
    }

    // Aggiorna lo stato dei servizi e la GUI
    private void updateServiceStatus() {
        try {
            // Controlla lo stato di Samba
            if (isServiceActive("smbd")) {
                sambaStatusLabel.setText("Samba: Attivo");
                sambaStatusLabel.setBackground(Color.GREEN);
                sambaButton.setText("Stop SAMBA");
            } else {
                sambaStatusLabel.setText("Samba: Inattivo");
                sambaStatusLabel.setBackground(Color.RED);
                sambaButton.setText("Start SAMBA");
            }

            // Controlla lo stato di FTP
            if (isServiceActive("vsftpd")) {
                ftpStatusLabel.setText("vsftpd: Attivo");
                ftpStatusLabel.setBackground(Color.GREEN);
                ftpButton.setText("Stop FTP");
            } else {
                ftpStatusLabel.setText("vsftpd: Inattivo");
                ftpStatusLabel.setBackground(Color.RED);
                ftpButton.setText("Start FTP");
            }
        } catch (Exception ex) {
            System.err.println("Errore durante l'aggiornamento dello stato dei servizi: " + ex.getMessage());
        }
    }

    // Metodo per inizializzare la GUI
    private void initializeGUI(boolean isSambaActive, boolean isFtpActive) {
        mainFrame = new JFrame("Gestione Utenti e Servizi");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1000, 600);
        mainFrame.setLayout(new BorderLayout(10, 10));

        // Pannello superiore per lo stato dei servizi e i pulsanti di controllo
        statusPanel = new JPanel(new GridLayout(2, 2, 10, 10)); // Due righe: una per i label, una per i pulsanti
        sambaStatusLabel = createStatusLabel("Samba", isSambaActive);
        ftpStatusLabel = createStatusLabel("vsftpd", isFtpActive);
        sambaButton = new JButton(isSambaActive ? "Stop SAMBA" : "Start SAMBA");
        ftpButton = new JButton(isFtpActive ? "Stop FTP" : "Start FTP");
        sambaButton.addActionListener(e -> {
            try {
                if (isServiceActive("smbd")) {
                    sambaManager.stopSambaService();
                } else {
                    sambaManager.startSambaService();
                }
                // Lo stato verrà aggiornato automaticamente dal timer
            } catch (IOException ex) {
                showErrorDialog("Errore durante la gestione del servizio Samba: " + ex.getMessage());
            }
        });
        ftpButton.addActionListener(e -> {
            try {
                if (isServiceActive("vsftpd")) {
                    ftpManager.stopFtpService();
                } else {
                    ftpManager.startFtpService();
                }
                // Lo stato verrà aggiornato automaticamente dal timer
            } catch (IOException ex) {
                showErrorDialog("Errore durante la gestione del servizio FTP: " + ex.getMessage());
            }
        });
        statusPanel.add(sambaStatusLabel);
        statusPanel.add(ftpStatusLabel);
        statusPanel.add(sambaButton);
        statusPanel.add(ftpButton);
        mainFrame.add(statusPanel, BorderLayout.NORTH);

        // Pannello principale con lista utenti e dettagli
        mainPanel = new JPanel(new BorderLayout(10, 0));

        // Lista utenti a sinistra
        userList = new JList<>(usersManager.getUsernames()); // GET dei nomi utente dal User Manager
        JScrollPane userListScrollPane = new JScrollPane(userList);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBorder(BorderFactory.createTitledBorder("Utenti"));
        userList.setPreferredSize(new Dimension(200, 0)); // Allarga la lista utenti
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // Evita doppie notifiche
                String selectedUser = userList.getSelectedValue();
                if (selectedUser != null) {
                    populateUserPanel(selectedUser);
                }
            }
        });
        mainPanel.add(userListScrollPane, BorderLayout.WEST);

        // Dettagli utente a destra
        userDetailPanel = new JPanel(new BorderLayout(10, 10));
        userDetailPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        userDetailPanel.add(createUserDetailPanel(), BorderLayout.CENTER);
        userDetailPanel.setVisible(false);
        mainPanel.add(userDetailPanel, BorderLayout.CENTER);

        // Pannello inferiore per Config SAMBA e Config FTP
        configButtonPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        JButton configSambaButton = new JButton("Config SAMBA");
        JButton configFtpButton = new JButton("Config FTP");
        configButtonPanel.setBorder(BorderFactory.createTitledBorder("Configurazioni Globali"));
        configButtonPanel.add(configSambaButton);
        configButtonPanel.add(configFtpButton);
        mainFrame.add(configButtonPanel, BorderLayout.SOUTH);

        mainFrame.add(mainPanel, BorderLayout.CENTER);
        mainFrame.setVisible(true);
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

    private void showInformationDialog(String message) {
        JOptionPane.showMessageDialog(
                null,
                message,
                "Informazione",
                JOptionPane.INFORMATION_MESSAGE
        );
        System.out.println(message);
    }

    // Crea un'etichetta di stato
    private JLabel createStatusLabel(String serviceName, boolean isActive) {
        JLabel label = new JLabel(serviceName + ": " + (isActive ? "Attivo" : "Inattivo"));
        label.setOpaque(true);
        label.setBackground(isActive ? Color.GREEN : Color.RED);
        label.setForeground(Color.WHITE);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new EmptyBorder(5, 5, 5, 5));
        return label;
    }

    // Crea il pannello per i dettagli utente
    private JPanel createUserDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Pannello superiore per checkbox
        JPanel checkboxPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        ftpCheckbox = new JCheckBox("Abilitazione FTP");
        ftpCheckbox.setEnabled(false);
        sambaCheckbox = new JCheckBox("Abilitazione Samba");
        sambaCheckbox.setEnabled(false);
        checkboxPanel.add(sambaCheckbox);
        checkboxPanel.add(ftpCheckbox);
        panel.add(checkboxPanel, BorderLayout.NORTH);

        // Lista condivisioni ftp
        ftpShareList = new JList<>(new String[]{}); // Mock iniziale vuoto
        ftpShareList.setBorder(BorderFactory.createTitledBorder("Condivisioni FTP"));
        ftpShareList.setEnabled(false);
        // Listener per la lista delle condivisioni FTP
        ftpShareList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // Evita doppie notifiche
                if (ftpShareList.getSelectedIndex() != -1) { // Qualcosa è selezionato
                    sambaShareList.clearSelection(); // Deseleziona la lista Samba
                }
            }
        });

        // Lista condivisioni samba
        sambaShareList = new JList<>(new String[]{}); // Mock iniziale vuoto
        sambaShareList.setBorder(BorderFactory.createTitledBorder("Condivisioni SAMBA"));
        sambaShareList.setEnabled(false);
        // Listener per la lista delle condivisioni Samba
        sambaShareList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // Evita doppie notifiche
                if (sambaShareList.getSelectedIndex() != -1) { // Qualcosa è selezionato
                    ftpShareList.clearSelection(); // Deseleziona la lista FTP
                }
            }
        });

        // Pannello per le liste di condivisioni
        JPanel shareListsPanel = new JPanel(new GridLayout(1, 2, 10, 10)); // Layout con due colonne
        shareListsPanel.add(new JScrollPane(sambaShareList));
        shareListsPanel.add(new JScrollPane(ftpShareList));
        panel.add(shareListsPanel, BorderLayout.CENTER);

        // Pannello per i pulsanti "Elimina Condivisione", "Aggiungi Condivisione" e "Info Condivisione"
        JPanel shareButtonPanel = new JPanel();
        shareButtonPanel.setLayout(new BoxLayout(shareButtonPanel, BoxLayout.X_AXIS)); // Layout orizzontale
        deleteShareButton = new JButton("Elimina Condivisione");
        addShareButton = new JButton("Aggiungi Condivisione");
        infoShareButton = new JButton("Info Condivisione"); // Nuovo bottone

        // Forza dimensione uniforme per i pulsanti
        Dimension buttonSize = new Dimension(200, 30); // Dimensione uniforme per tutti i pulsanti
        deleteShareButton.setPreferredSize(buttonSize);
        deleteShareButton.setMinimumSize(buttonSize);
        deleteShareButton.setMaximumSize(buttonSize);
        deleteShareButton.addActionListener(e -> handleDeleteShare());

        addShareButton.setPreferredSize(buttonSize);
        addShareButton.setMinimumSize(buttonSize);
        addShareButton.setMaximumSize(buttonSize);
        addShareButton.addActionListener(e -> openAddShareDialog());

        infoShareButton.setPreferredSize(buttonSize);
        infoShareButton.setMinimumSize(buttonSize);
        infoShareButton.setMaximumSize(buttonSize);

        deleteShareButton.setEnabled(false);
        addShareButton.setEnabled(false);
        infoShareButton.setEnabled(false); // Disabilitato inizialmente

        // Aggiungi pulsanti con spazi tra di loro
        shareButtonPanel.add(deleteShareButton);
        shareButtonPanel.add(Box.createRigidArea(new Dimension(10, 0))); // Spazio orizzontale
        shareButtonPanel.add(addShareButton);
        shareButtonPanel.add(Box.createRigidArea(new Dimension(10, 0))); // Spazio orizzontale
        shareButtonPanel.add(infoShareButton);

        // Pannello per il pulsante "Gestisci Utente"
        JPanel manageButtonPanel = new JPanel();
        manageButtonPanel.setLayout(new BoxLayout(manageButtonPanel, BoxLayout.Y_AXIS)); // Layout verticale
        manageUserButton = new JButton("Gestisci Utente");
        manageUserButton.setEnabled(true);
        manageUserButton.setPreferredSize(new Dimension(200, 30)); // Larghezza e altezza uguali agli altri bottoni
        manageUserButton.setMinimumSize(new Dimension(200, 30));
        manageUserButton.setMaximumSize(new Dimension(200, 30));
        manageUserButton.addActionListener(e -> {
            boolean isEditable = ftpCheckbox.isEnabled(); // Controlla se già abilitato
            boolean enable = !isEditable; // Inverte lo stato (toggle)

            if (!enable) { // Quando si clicca "Salva Modifiche"
                saveUserChanges();
            }

            // Aggiorna i bottoni, le liste e le checkbox
            ftpCheckbox.setEnabled(enable);
            sambaCheckbox.setEnabled(enable);

            // Gestisci lo stato delle liste basandoti sull'abilitazione dei protocolli
            if (ftpCheckbox.isSelected() && enable) {
                ftpShareList.setEnabled(true); // Abilita se FTP è selezionato e in modifica
            } else {
                ftpShareList.setEnabled(false); // Disabilita altrimenti
            }

            if (sambaCheckbox.isSelected() && enable) {
                sambaShareList.setEnabled(true); // Abilita se Samba è selezionato e in modifica
            } else {
                sambaShareList.setEnabled(false); // Disabilita altrimenti
            }

            deleteShareButton.setEnabled(enable);
            addShareButton.setEnabled(enable);
            infoShareButton.setEnabled(enable);

            // Disabilita/abilita la lista utenti
            userList.setEnabled(!enable);

            // Cambia il testo del bottone in base allo stato
            manageUserButton.setText(enable ? "Salva Modifiche" : "Gestisci Utente");
        });

        // Centra "Gestisci Utente" e aggiungi spazio sopra
        manageUserButton.setAlignmentX(Component.CENTER_ALIGNMENT); // Centra
        manageButtonPanel.add(shareButtonPanel);
        manageButtonPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Spazio verticale
        manageButtonPanel.add(manageUserButton);

        // Listener per il pulsante "Info Condivisione"
        infoShareButton.addActionListener(e -> {
            try {
                // Determina quale lista ha un elemento selezionato
                String selectedFtpShare = ftpShareList.getSelectedValue();
                String selectedSambaShare = sambaShareList.getSelectedValue();

                if (selectedFtpShare != null) {
                    // Ottieni i dettagli della condivisione FTP dal bean
                    FtpCondBean ftpShare = ftpManager.getSharesByUser(userList.getSelectedValue()).stream()
                            .filter(share -> share.getShareName().equals(selectedFtpShare))
                            .findFirst()
                            .orElse(null);

                    if (ftpShare != null) {
                        showInfoDialog(ftpShare.toFormattedString());
                    } else {
                        showErrorDialog("Impossibile trovare i dettagli della condivisione FTP.");
                    }
                } else if (selectedSambaShare != null) {
                    // Ottieni i dettagli della condivisione Samba dal bean
                    SmbCondBean sambaShare = sambaManager.getSharesByUser(userList.getSelectedValue()).stream()
                            .filter(share -> share.getName().equals(selectedSambaShare))
                            .findFirst()
                            .orElse(null);

                    if (sambaShare != null) {
                        showInfoDialog(sambaShare.toFormattedString());
                    } else {
                        showErrorDialog("Impossibile trovare i dettagli della condivisione Samba.");
                    }
                } else {
                    showErrorDialog("Nessuna condivisione selezionata!");
                }
            } catch (Exception ex) {
                showErrorDialog("Errore durante il caricamento delle informazioni: " + ex.getMessage());
            }
        });

        // Aggiungi il pannello inferiore
        panel.add(manageButtonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void showInfoDialog(String infoText) {
        // Crea una text area non modificabile
        JTextArea textArea = new JTextArea(infoText);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        // Inseriscila in uno scroll pane
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        // Mostra il dialog
        JOptionPane.showMessageDialog(
                mainFrame,
                scrollPane,
                "Informazioni Condivisione",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void populateUserPanel(String username) {
        try {
            // Ottieni il bean dell'utente selezionato
            UserBean user = usersManager.getUsers().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst()
                    .orElse(null);

            if (user == null) {
                showErrorDialog("Utente non trovato: " + username);
                userDetailPanel.setVisible(false); // Nascondi il pannello in caso di errore
                return;
            }

            // Aggiorna checkbox per Samba e FTP
            ftpCheckbox.setSelected(user.isFtpEnabled());
            sambaCheckbox.setSelected(user.isSambaEnabled());

            // Deseleziona entrambe le liste prima di popolare
            ftpShareList.clearSelection();
            sambaShareList.clearSelection();

            // Gestione Samba
            if (user.isSambaEnabled()) {
                // Carica le condivisioni Samba associate all'utente
                ArrayList<SmbCondBean> sambaShares = sambaManager.getSharesByUser(username);
                List<String> sambaShareNames = sambaShares.stream()
                        .map(SmbCondBean::getName) // Ottieni solo i nomi delle condivisioni
                        .toList();
                sambaShareList.setListData(sambaShareNames.toArray(new String[0]));
                //sambaShareList.setEnabled(true); // Sblocca la lista
            } else {
                // Svuota e disabilita la lista
                sambaShareList.setListData(new String[0]); // Svuota
                //sambaShareList.setEnabled(false); // Disabilita
            }

            // Gestione FTP
            if (user.isFtpEnabled()) {
                // Carica le condivisioni FTP associate all'utente
                ArrayList<FtpCondBean> ftpShares = ftpManager.getSharesByUser(username);
                List<String> ftpShareNames = ftpShares.stream()
                        .map(FtpCondBean::getShareName) // Ottieni solo i nomi delle condivisioni
                        .toList();
                ftpShareList.setListData(ftpShareNames.toArray(new String[0]));
                //ftpShareList.setEnabled(true); // Sblocca la lista
            } else {
                // Svuota e disabilita la lista
                ftpShareList.setListData(new String[0]); // Svuota
                //ftpShareList.setEnabled(false); // Disabilita
            }

            // Mostra il pannello per la gestione dell'utente
            userDetailPanel.setVisible(true);

        } catch (Exception e) {
            showErrorDialog("Errore durante il caricamento dei dati per l'utente: " + e.getMessage());
            userDetailPanel.setVisible(false); // Nascondi il pannello in caso di errore
        }
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

    private void openAddShareDialog() {
        //Username
        String selectedUser = userList.getSelectedValue();

        // Crea la finestra
        JDialog addShareDialog = new JDialog(mainFrame, "Aggiungi Condivisione per: " + selectedUser, true);
        addShareDialog.setLayout(new BorderLayout(10, 10));
        addShareDialog.setSize(500, 450);
        addShareDialog.setLocationRelativeTo(mainFrame);

        // Checkbox per FTP e Samba
        JCheckBox ftpCheckBox = new JCheckBox("FTP");
        JCheckBox sambaCheckBox = new JCheckBox("Samba");
        // Controlla se l'utente è abilitato a Samba o FTP
        boolean isSambaEnabled = usersManager.getUsers().stream()
                .anyMatch(user -> user.getUsername().equalsIgnoreCase(selectedUser) && user.isSambaEnabled());
        boolean isFtpEnabled = usersManager.getUsers().stream()
                .anyMatch(user -> user.getUsername().equalsIgnoreCase(selectedUser) && user.isFtpEnabled());
        // Disabilita le checkbox se l'utente non è abilitato
        sambaCheckBox.setEnabled(isSambaEnabled);
        ftpCheckBox.setEnabled(isFtpEnabled);

        // Pannello per i campi di input
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 5, 5));

        // Campi comuni per FTP e Samba
        JLabel shareNameLabel = new JLabel("Nome Condivisione:");
        JTextField shareNameField = new JTextField();
        JLabel pathLabel = new JLabel("Percorso:");
        JTextField pathField = new JTextField();
        JButton pathButton = new JButton("Seleziona Cartella");

        // Campi specifici per Samba
        JLabel sambaCommentLabel = new JLabel("Commento:");
        JTextField sambaCommentField = new JTextField();
        JLabel sambaBrowsableLabel = new JLabel("Browsable:");
        JTextField sambaBrowsableField = new JTextField("yes"); // Default
        JLabel sambaWritableLabel = new JLabel("Writable:");
        JTextField sambaWritableField = new JTextField("yes"); // Default
        JLabel sambaGuestOkLabel = new JLabel("Guest OK:");
        JTextField sambaGuestOkField = new JTextField("no"); // Default
        JLabel sambaCreateMaskLabel = new JLabel("Create Mask:");
        JTextField sambaCreateMaskField = new JTextField("0664"); // Default
        JLabel sambaDirectoryMaskLabel = new JLabel("Directory Mask:");
        JTextField sambaDirectoryMaskField = new JTextField("0775"); // Default

        // Disabilita i campi Samba inizialmente
        sambaCommentField.setEnabled(false);
        sambaBrowsableField.setEnabled(false);
        sambaWritableField.setEnabled(false);
        sambaGuestOkField.setEnabled(false);
        sambaCreateMaskField.setEnabled(false);
        sambaDirectoryMaskField.setEnabled(false);

        // Abilita i campi in base alla checkbox Samba
        sambaCheckBox.addActionListener(e -> {
            boolean enabled = sambaCheckBox.isSelected();
            sambaCommentField.setEnabled(enabled);
            sambaBrowsableField.setEnabled(enabled);
            sambaWritableField.setEnabled(enabled);
            sambaGuestOkField.setEnabled(enabled);
            sambaCreateMaskField.setEnabled(enabled);
            sambaDirectoryMaskField.setEnabled(enabled);
        });

        // Aggiungi il listener al pulsante di selezione della cartella
        pathButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = fileChooser.showOpenDialog(addShareDialog);
            if (result == JFileChooser.APPROVE_OPTION) {
                pathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        // Aggiungi i campi al pannello di input
        inputPanel.add(ftpCheckBox);
        inputPanel.add(sambaCheckBox);
        inputPanel.add(shareNameLabel);
        inputPanel.add(shareNameField);
        inputPanel.add(pathLabel);
        JPanel pathPanel = new JPanel(new BorderLayout());
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(pathButton, BorderLayout.EAST);
        inputPanel.add(pathPanel);
        inputPanel.add(sambaCommentLabel);
        inputPanel.add(sambaCommentField);
        inputPanel.add(sambaBrowsableLabel);
        inputPanel.add(sambaBrowsableField);
        inputPanel.add(sambaWritableLabel);
        inputPanel.add(sambaWritableField);
        inputPanel.add(sambaGuestOkLabel);
        inputPanel.add(sambaGuestOkField);
        inputPanel.add(sambaCreateMaskLabel);
        inputPanel.add(sambaCreateMaskField);
        inputPanel.add(sambaDirectoryMaskLabel);
        inputPanel.add(sambaDirectoryMaskField);

        // Pulsanti
        JButton confirmButton = new JButton("Conferma");
        JButton cancelButton = new JButton("Annulla");

        confirmButton.addActionListener(e -> {
            try {
                // Controlla i dati comuni
                if (shareNameField.getText().isEmpty() || pathField.getText().isEmpty()) {
                    showErrorDialog("Nome Condivisione e Percorso sono obbligatori.");
                    return;
                }

                String shareName = shareNameField.getText();
                String path = pathField.getText();

                if (selectedUser == null) {
                    showErrorDialog("Seleziona un utente prima di aggiungere una condivisione.");
                    return;
                }

                // Gestione FTP
                if (ftpCheckBox.isSelected()) {
                    // Controlla duplicati
                    boolean ftpDuplicate = ftpManager.getSharesByUser(selectedUser).stream()
                            .anyMatch(share -> share.getShareName().equals(shareName) || share.getPath().equals(path));

                    if (ftpDuplicate) {
                        showErrorDialog("Esiste già una condivisione FTP con lo stesso nome o percorso per l'utente selezionato.");
                        return;
                    }

                    // Usa addShare del manager
                    ftpManager.addShare(selectedUser, shareName, path);

                    //Aggiorna la lista delle condivisioni FTP visivamente
                    ftpShareList.setListData(ftpManager.getSharesByUser(selectedUser)
                            .stream()
                            .map(FtpCondBean::getShareName)
                            .toArray(String[]::new));
                }

                // Gestione Samba
                if (sambaCheckBox.isSelected()) {
                    // Controllo per nome duplicato con percorso diverso
                    boolean nameConflict = sambaManager.getAllShares().stream()
                            .anyMatch(share -> share.getName().equalsIgnoreCase(shareName) &&
                                    share.getProperties().stream()
                                            .noneMatch(property -> property[0].equalsIgnoreCase("path") &&
                                                    property[1].equals(path)));

                    if (nameConflict) {
                        showErrorDialog("Esiste già una condivisione Samba con lo stesso nome ma un percorso diverso. Modifica i dettagli.");
                        return;
                    }

                    // Controllo per percorso già condiviso
                    SmbCondBean existingShare = sambaManager.getAllShares().stream()
                            .filter(share -> share.getProperties().stream()
                                    .anyMatch(property -> property[0].equalsIgnoreCase("path") && property[1].equals(path)))
                            .findFirst()
                            .orElse(null);

                    if (existingShare != null) {
                        // Aggiungi l'utente ai valid users
                        existingShare.addValidUser(selectedUser);
                        sambaManager.modifyShare(existingShare.getName(), existingShare);

                        // Mostra dialog informativo
                        showInformationDialog("Il percorso è già condiviso con il nome '" + existingShare.getName() +
                                "'. L'utente è stato aggiunto ai valid users.");
                    } else {
                        // Crea una nuova condivisione
                        SmbCondBean sambaShare = new SmbCondBean(shareName);
                        sambaShare.addProperty("path", path);
                        sambaShare.addProperty("comment", sambaCommentField.getText());
                        sambaShare.addProperty("browsable", sambaBrowsableField.getText());
                        sambaShare.addProperty("writable", sambaWritableField.getText());
                        sambaShare.addProperty("guest ok", sambaGuestOkField.getText());
                        sambaShare.addProperty("create mask", sambaCreateMaskField.getText());
                        sambaShare.addProperty("directory mask", sambaDirectoryMaskField.getText());
                        sambaShare.addValidUser(selectedUser);

                        sambaManager.addShare(sambaShare);
                    }

                    // Aggiorna la lista delle condivisioni Samba visivamente
                    sambaShareList.setListData(sambaManager.getSharesByUser(selectedUser)
                            .stream()
                            .map(SmbCondBean::getName)
                            .toArray(String[]::new));
                }

                addShareDialog.dispose();
            } catch (Exception ex) {
                showErrorDialog("Errore durante l'aggiunta della condivisione: " + ex.getMessage());
            }
        });

        cancelButton.addActionListener(e -> addShareDialog.dispose());

        // Pannello per i pulsanti
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(confirmButton);
        buttonPanel.add(cancelButton);

        // Aggiungi tutto alla finestra
        addShareDialog.add(inputPanel, BorderLayout.CENTER);
        addShareDialog.add(buttonPanel, BorderLayout.SOUTH);

        addShareDialog.setVisible(true);
    }

    private void handleDeleteShare() {
        try {
            String selectedFtpShare = ftpShareList.getSelectedValue();
            String selectedSambaShare = sambaShareList.getSelectedValue();
            String selectedUser = userList.getSelectedValue();

            if (selectedUser == null) {
                showErrorDialog("Seleziona un utente prima di eliminare una condivisione.");
                return;
            }

            if (selectedFtpShare == null && selectedSambaShare == null) {
                showErrorDialog("Seleziona una condivisione da eliminare.");
                return;
            }

            String shareName = selectedFtpShare != null ? selectedFtpShare : selectedSambaShare;
            String protocol = selectedFtpShare != null ? "FTP" : "Samba";

            // Chiede conferma all'utente
            int confirm = JOptionPane.showConfirmDialog(
                    null,
                    "Sei sicuro di voler rimuovere la condivisione " + shareName + ", " + protocol + "?",
                    "Conferma Eliminazione",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm != JOptionPane.YES_OPTION) {
                return; // L'utente ha annullato
            }

            if (selectedFtpShare != null) {
                // Rimuove la condivisione FTP
                FtpCondBean ftpShare = ftpManager.getSharesByUser(selectedUser).stream()
                        .filter(share -> share.getShareName().equals(selectedFtpShare))
                        .findFirst()
                        .orElse(null);

                if (ftpShare != null) {
                    ftpManager.removeShare(ftpShare); // Rimuove solo dalla lista temporanea
                    ftpShareList.setListData(ftpManager.getSharesByUser(selectedUser)
                            .stream()
                            .map(FtpCondBean::getShareName)
                            .toArray(String[]::new)); // Aggiorna la lista visivamente
                } else {
                    showErrorDialog("Condivisione FTP non trovata.");
                }
            } else if (selectedSambaShare != null) {
                // Rimuove l'utente dai valid users di una condivisione Samba
                SmbCondBean sambaShare = sambaManager.getSharesByUser(selectedUser).stream()
                        .filter(share -> share.getName().equals(selectedSambaShare))
                        .findFirst()
                        .orElse(null);

                if (sambaShare != null) {
                    sambaShare.removeValidUser(selectedUser);
                    sambaManager.modifyShare(sambaShare.getName(), sambaShare); // Usa il modifyShare aggiornato
                    sambaShareList.setListData(sambaManager.getSharesByUser(selectedUser)
                            .stream()
                            .map(SmbCondBean::getName)
                            .toArray(String[]::new)); // Aggiorna la lista visivamente
                } else {
                    showErrorDialog("Condivisione Samba non trovata.");
                }
            }
        } catch (Exception ex) {
            showErrorDialog("Errore durante l'eliminazione della condivisione: " + ex.getMessage());
        }
    }

    private void saveUserChanges() {
        // Chiedi conferma all'utente
        int confirm = JOptionPane.showConfirmDialog(
                null,
                "Sei sicuro di voler salvare le modifiche? I servizi FTP e Samba saranno riavviati.",
                "Conferma Salvataggio",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return; // L'utente ha annullato l'operazione
        }

        try {
            // Disattiva i servizi Samba e FTP
            sambaManager.stopSambaService();
            ftpManager.stopFtpService();

            // Aggiorna le configurazioni
            ftpManager.saveSharesOnDisk();
            sambaManager.updateConfig();

            // Riattiva i servizi Samba e FTP
            sambaManager.startSambaService();
            ftpManager.startFtpService();

            // Mostra messaggio di successo
            showInformationDialog("Modifiche salvate con successo e servizi riavviati.");
        } catch (Exception ex) {
            // Gestione degli errori
            showErrorDialog("Errore durante il salvataggio delle modifiche: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        new MainManager(); // Auto-istanziazione della classe
    }
}

