package org.antonio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.io.*;
import java.awt.*;
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
        checkboxPanel.add(ftpCheckbox);
        checkboxPanel.add(sambaCheckbox);
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

        addShareButton.setPreferredSize(buttonSize);
        addShareButton.setMinimumSize(buttonSize);
        addShareButton.setMaximumSize(buttonSize);

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

            // Aggiorna i bottoni, le liste e le checkbox
            ftpCheckbox.setEnabled(enable);
            sambaCheckbox.setEnabled(enable);
            ftpShareList.setEnabled(enable);
            sambaShareList.setEnabled(enable);
            deleteShareButton.setEnabled(enable);
            addShareButton.setEnabled(enable);
            infoShareButton.setEnabled(enable);

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
                    FtpCondBean ftpShare = ftpManager.getSharesByUser(userDetailPanel.getName()).stream()
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
                    SmbCondBean sambaShare = sambaManager.getSharesByUser(userDetailPanel.getName()).stream()
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

            // Carica le condivisioni ftp associate all'utente
            ArrayList<FtpCondBean> shares = ftpManager.getSharesByUser(username);
            List<String> shareNames = shares.stream()
                    .map(FtpCondBean::getShareName)
                    .toList();
            ftpShareList.setListData(shareNames.toArray(new String[0]));

            // Carica le condivisioni Samba associate all'utente
            ArrayList<SmbCondBean> sambaShares = sambaManager.getSharesByUser(username);
            List<String> sambaShareNames = sambaShares.stream()
                    .map(SmbCondBean::getName) // Ottieni solo i nomi delle condivisioni
                    .toList();
            sambaShareList.setListData(sambaShareNames.toArray(new String[0]));

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

    public static void main(String[] args) {
        new MainManager(); // Auto-istanziazione della classe
    }
}

