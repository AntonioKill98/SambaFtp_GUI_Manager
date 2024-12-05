package org.antonio;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.io.*;
import java.awt.*;
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
    private JButton manageUserButton, deleteShareButton, addShareButton, sambaButton, ftpButton, infoShareButton, addUserButton, deleteUserButton, configSambaButton, configFtpButton;
    private JLabel sambaStatusLabel, ftpStatusLabel;
    private JPanel userDetailPanel, configButtonPanel, mainPanel, statusPanel, userButtonsPanel;
    private boolean debugEnabled; // Flag per il debug

    // Metodo per attivare/disattivare il debug
    public void toggleDebug() {
        debugEnabled = !debugEnabled;
        System.out.println("SAMBAMANAGER_DEBUG: Debug " + (debugEnabled ? "abilitato" : "disabilitato"));
        sambaManager.toggleDebug();
        ftpManager.toggleDebug();
        usersManager.toggleDebug();
    }

    // Metodo per stampare messaggi di debug
    private void printDebug(String message) {
        if (debugEnabled) {
            // Codice ANSI per il colore rosso chiaro
            final String LIGHT_BLUE = "\033[94m";
            final String RESET = "\033[0m"; // Resetta il colore al valore predefinito

            // Stampa il messaggio con il prefisso colorato
            System.out.println(LIGHT_BLUE + "MAINMANAGER_DEBUG: " + RESET + message);
        }
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(
                null,
                message,
                "Errore",
                JOptionPane.ERROR_MESSAGE
        );
        System.err.println(message);
    }

    private void showInfoDialog(String message) {
        JOptionPane.showMessageDialog(
                null,
                message,
                "Informazione",
                JOptionPane.INFORMATION_MESSAGE
        );
        System.out.println(message);
    }

    private boolean checkRoot() {
        String user = System.getProperty("user.name");
        return "root".equals(user);
    }

    public MainManager() {
        try {

            // Controlla se il programma è eseguito da root
            if (!checkRoot()) {
                showErrorDialog("Errore: il programma deve essere eseguito come root.");
                printDebug("Il programma deve essere eseguito come root");
                System.exit(1);
            }

            printDebug("Inizio inizializzazione di MainManager.");

            // Controlla se Samba e vsftpd sono installati
            printDebug("Controllo se il pacchetto Samba è installato.");
            if (!isPackageInstalled("samba")) {
                printDebug("Errore: Samba non è installato.");
                showErrorDialog("Errore: Samba non è installato.");
                System.exit(1);
            }
            printDebug("Samba è installato.");

            printDebug("Controllo se il pacchetto vsftpd è installato.");
            if (!isPackageInstalled("vsftpd")) {
                printDebug("Errore: vsftpd non è installato.");
                showErrorDialog("Errore: vsftpd non è installato.");
                System.exit(1);
            }
            printDebug("vsftpd è installato.");

            // Controlla se i servizi Samba e vsftpd sono attivi
            printDebug("Controllo se il servizio Samba (smbd) è attivo.");
            boolean isSambaActive = isServiceActive("smbd");
            printDebug("Stato del servizio Samba (smbd): " + (isSambaActive ? "Attivo" : "Inattivo"));

            printDebug("Controllo se il servizio vsftpd è attivo.");
            boolean isFtpActive = isServiceActive("vsftpd");
            printDebug("Stato del servizio vsftpd: " + (isFtpActive ? "Attivo" : "Inattivo"));

            // Inizializza i manager
            printDebug("Inizializzazione di SambaManager.");
            sambaManager = new SambaManager("/etc/samba/smb.conf");
            printDebug("Inizializzazione di FtpManager.");
            ftpManager = new FtpManager("/etc/vsftpd.conf", "/etc/vsftpd.userlist");
            printDebug("Inizializzazione di UsersManager.");
            usersManager = new UsersManager(sambaManager, ftpManager);

            // Sezione di Debug
            debugSystem(sambaManager, ftpManager, usersManager);

            // Crea la GUI
            printDebug("Inizializzazione della GUI.");
            initializeGUI(isSambaActive, isFtpActive);

            // Avvia il timer per l'aggiornamento dello stato
            printDebug("Avvio del timer per l'aggiornamento dello stato.");
            startStatusUpdateTimer();

            printDebug("MainManager inizializzato correttamente.");
        } catch (IOException e) {
            printDebug("Errore durante l'inizializzazione di MainManager: " + e.getMessage());
            showErrorDialog("Errore durante l'inizializzazione: " + e.getMessage());
            System.exit(1);
        }
    }

    private void debugSystem(SambaManager sambaManager, FtpManager ftpManager, UsersManager usersManager) {
        System.out.println("=========================================");
        System.out.println("========== DEBUG CONFIGURATION ==========");
        System.out.println("=========================================");

        // Configurazione Samba
        System.out.println("=== DEBUG: Samba Configuration ===");
        System.out.println(sambaManager.getFormattedGlobalSettings());
        System.out.println(sambaManager.getFormattedHomeSettings());
        System.out.println("-----------------------------------------");

        // Condivisioni Samba
        System.out.println("=== DEBUG: Samba Shares ===");
        ArrayList<SmbCondBean> sambaShares = sambaManager.getAllShares();
        for (int i = 0; i < sambaShares.size(); i++) {
            SmbCondBean share = sambaShares.get(i);
            System.out.println("SambaShare n" + (i + 1));
            System.out.println("Name: " + share.getName());
            System.out.println("Properties:");
            for (String[] property : share.getProperties()) {
                System.out.println("  Key: " + property[0] + ", Value: " + property[1]);
            }
            System.out.println("Valid Users: " + String.join(", ", share.getValidUsers()));
            System.out.println("-----------------------------------------");
        }

        // Utenti abilitati a Samba
        System.out.println("=== DEBUG: Samba Users ===");
        ArrayList<String> sambaUsers = sambaManager.getSambaUsers();
        if (sambaUsers.isEmpty()) {
            System.out.println("Nessun utente abilitato a Samba.");
        } else {
            for (String user : sambaUsers) {
                System.out.println("- " + user);
            }
        }
        System.out.println("-----------------------------------------");

        // Configurazione FTP
        System.out.println("=== DEBUG: FTP Configuration ===");
        System.out.println(ftpManager.getFormattedConfig());
        System.out.println("-----------------------------------------");

        // Condivisioni FTP
        System.out.println("=== DEBUG: FTP Shares ===");
        ArrayList<FtpCondBean> ftpShares = ftpManager.getFtpShares();
        for (int i = 0; i < ftpShares.size(); i++) {
            FtpCondBean share = ftpShares.get(i);
            System.out.println("FTPShare n" + (i + 1));
            System.out.println("Username: " + share.getUsername());
            System.out.println("Share Name: " + share.getShareName());
            System.out.println("Path: " + share.getPath());
            System.out.println("-----------------------------------------");
        }

        // Utenti abilitati a FTP
        System.out.println("=== DEBUG: FTP Users ===");
        ArrayList<String> ftpUsers = ftpManager.getFtpUsers();
        if (ftpUsers.isEmpty()) {
            System.out.println("Nessun utente abilitato a FTP.");
        } else {
            for (String user : ftpUsers) {
                System.out.println("- " + user);
            }
        }
        System.out.println("-----------------------------------------");

        // Utenti di sistema e stato dei servizi
        System.out.println("=== DEBUG: System Users ===");
        ArrayList<UserBean> systemUsers = usersManager.getUsers();
        for (UserBean user : systemUsers) {
            System.out.println("Username: " + user.getUsername());
            System.out.println("  Samba Enabled: " + user.isSambaEnabled());
            System.out.println("  FTP Enabled: " + user.isFtpEnabled());
            System.out.println("-----------------------------------------");
        }

        System.out.println("DEBUG COMPLETO TERMINATO.");
        System.out.println("=========================================");
    }

    // Avvia un timer per aggiornare periodicamente lo stato dei servizi
    private void startStatusUpdateTimer() {
        //printDebug("Avvio del timer per l'aggiornamento periodico dello stato dei servizi.");
        int delay = 5000; // Intervallo di aggiornamento in millisecondi (5 secondi)
        Timer timer = new Timer(delay, e -> {
            //printDebug("Timer attivato: aggiornamento dello stato dei servizi.");
            updateServiceStatus();
        });
        timer.start();
        //printDebug("Timer avviato con intervallo di aggiornamento: " + delay + " millisecondi.");
    }

    // Aggiorna lo stato dei servizi e la GUI
    private void updateServiceStatus() {
        try {
            //printDebug("Inizio aggiornamento dello stato dei servizi.");

            // Controlla lo stato di Samba
            //printDebug("Controllo stato del servizio Samba (smbd).");
            if (isServiceActive("smbd")) {
                //printDebug("Samba è attivo.");
                sambaStatusLabel.setText("Samba: Attivo");
                sambaStatusLabel.setBackground(Color.GREEN);
                sambaButton.setText("Stop SAMBA");
            } else {
                //printDebug("Samba è inattivo.");
                sambaStatusLabel.setText("Samba: Inattivo");
                sambaStatusLabel.setBackground(Color.RED);
                sambaButton.setText("Start SAMBA");
            }

            // Controlla lo stato di FTP
            //printDebug("Controllo stato del servizio FTP (vsftpd).");
            if (isServiceActive("vsftpd")) {
                //printDebug("vsftpd è attivo.");
                ftpStatusLabel.setText("vsftpd: Attivo");
                ftpStatusLabel.setBackground(Color.GREEN);
                ftpButton.setText("Stop FTP");
            } else {
                //printDebug("vsftpd è inattivo.");
                ftpStatusLabel.setText("vsftpd: Inattivo");
                ftpStatusLabel.setBackground(Color.RED);
                ftpButton.setText("Start FTP");
            }

            //printDebug("Aggiornamento dello stato dei servizi completato.");
        } catch (Exception ex) {
            printDebug("Errore durante l'aggiornamento dello stato dei servizi: " + ex.getMessage());
            System.err.println("Errore durante l'aggiornamento dello stato dei servizi: " + ex.getMessage());
        }
    }

    // Metodo per inizializzare la GUI
    private void initializeGUI(boolean isSambaActive, boolean isFtpActive) {
        printDebug("Inizio inizializzazione della GUI.");
        mainFrame = new JFrame("Gestione Utenti e Servizi");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1000, 600);
        mainFrame.setLayout(new BorderLayout(10, 10));
        printDebug("Main frame creato con dimensioni 1000x600 e layout BorderLayout.");

        // Pannello superiore per lo stato dei servizi e i pulsanti di controllo
        printDebug("Creazione del pannello superiore per lo stato dei servizi.");
        statusPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        sambaStatusLabel = createStatusLabel("Samba", isSambaActive);
        printDebug("Stato iniziale Samba: " + (isSambaActive ? "Attivo" : "Inattivo"));
        ftpStatusLabel = createStatusLabel("vsftpd", isFtpActive);
        printDebug("Stato iniziale FTP: " + (isFtpActive ? "Attivo" : "Inattivo"));

        sambaButton = new JButton(isSambaActive ? "Stop SAMBA" : "Start SAMBA");
        printDebug("Bottone Samba creato con testo iniziale: " + sambaButton.getText());
        ftpButton = new JButton(isFtpActive ? "Stop FTP" : "Start FTP");
        printDebug("Bottone FTP creato con testo iniziale: " + ftpButton.getText());

        sambaButton.addActionListener(e -> {
            printDebug("Azione sul bottone Samba.");
            try {
                if (isServiceActive("smbd")) {
                    printDebug("Servizio Samba attivo. Tentativo di arresto.");
                    sambaManager.stopSambaService();
                } else {
                    printDebug("Servizio Samba inattivo. Tentativo di avvio.");
                    sambaManager.startSambaService();
                }
            } catch (IOException ex) {
                printDebug("Errore durante la gestione del servizio Samba: " + ex.getMessage());
                showErrorDialog("Errore durante la gestione del servizio Samba: " + ex.getMessage());
            }
        });

        ftpButton.addActionListener(e -> {
            printDebug("Azione sul bottone FTP.");
            try {
                if (isServiceActive("vsftpd")) {
                    printDebug("Servizio FTP attivo. Tentativo di arresto.");
                    ftpManager.stopFtpService();
                } else {
                    printDebug("Servizio FTP inattivo. Tentativo di avvio.");
                    ftpManager.startFtpService();
                }
            } catch (IOException ex) {
                printDebug("Errore durante la gestione del servizio FTP: " + ex.getMessage());
                showErrorDialog("Errore durante la gestione del servizio FTP: " + ex.getMessage());
            }
        });

        statusPanel.add(sambaStatusLabel);
        statusPanel.add(ftpStatusLabel);
        statusPanel.add(sambaButton);
        statusPanel.add(ftpButton);
        printDebug("Pannello superiore completato e aggiunto al frame principale.");
        mainFrame.add(statusPanel, BorderLayout.NORTH);

        // Pannello principale con lista utenti e dettagli
        printDebug("Creazione del pannello principale con lista utenti e dettagli.");
        mainPanel = new JPanel(new BorderLayout(10, 0));

        // Pannello per lista utenti e bottoni combinati
        printDebug("Creazione del pannello per lista utenti con bottoni sottostanti.");
        JPanel userPanel = new JPanel(new BorderLayout()); // Pannello principale per lista + bottoni

        // Lista utenti a sinistra
        printDebug("Creazione della lista utenti.");
        userList = new JList<>(usersManager.getUsernames());
        JScrollPane userListScrollPane = new JScrollPane(userList);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBorder(BorderFactory.createTitledBorder("Utenti"));
        userList.setPreferredSize(new Dimension(200, 0));
        printDebug("Lista utenti configurata con dimensione preferita 200x0.");

        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedUser = userList.getSelectedValue();
                printDebug("Utente selezionato: " + selectedUser);
                if (selectedUser != null) {
                    populateUserPanel(selectedUser);
                }
            }
        });

        // Pannello per i bottoni sotto la lista utenti
        printDebug("Creazione del pannello per i bottoni sotto la lista utenti.");
        userButtonsPanel = new JPanel(new GridLayout(2, 1, 0, 5)); // Due righe, spaziatura verticale 5px
        userButtonsPanel.setPreferredSize(new Dimension(200, 70)); // Larghezza 200px per allineamento alla lista

        // Bottone "Aggiungi Utente"
        addUserButton = new JButton("Aggiungi Utente");
        addUserButton.setPreferredSize(new Dimension(200, 35)); // Altezza metà del pannello
        addUserButton.addActionListener(e -> {
            printDebug("Bottone 'Aggiungi Utente' cliccato.");
            openAddUserDialog(); // Metodo che gestisce l'aggiunta di un utente
        });
        userButtonsPanel.add(addUserButton);

        // Bottone "Elimina Utente"
        deleteUserButton = new JButton("Elimina Utente");
        deleteUserButton.setPreferredSize(new Dimension(200, 35)); // Altezza metà del pannello
        deleteUserButton.addActionListener(e -> {
            String selectedUser = userList.getSelectedValue();
            printDebug("Bottone 'Elimina Utente' cliccato. Utente selezionato: " + selectedUser);
            if (selectedUser == null) {
                showErrorDialog("Seleziona un utente prima di eliminarlo.");
                return;
            }
            deleteUser(selectedUser); // Metodo che gestisce l'eliminazione di un utente
        });
        userButtonsPanel.add(deleteUserButton);

        // Aggiungi lista utenti e bottoni al pannello combinato
        userPanel.add(userListScrollPane, BorderLayout.CENTER); // Lista utenti nella parte centrale
        userPanel.add(userButtonsPanel, BorderLayout.SOUTH); // Bottoni sotto la lista

        // Aggiungi il pannello combinato al pannello principale
        mainPanel.add(userPanel, BorderLayout.WEST);
        printDebug("Pannello utenti con bottoni aggiunto al pannello principale.");

        // Dettagli utente a destra
        printDebug("Creazione del pannello dei dettagli utente.");
        userDetailPanel = new JPanel(new BorderLayout(10, 10));
        userDetailPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        userDetailPanel.add(createUserDetailPanel(), BorderLayout.CENTER);
        userDetailPanel.setVisible(false);
        mainPanel.add(userDetailPanel, BorderLayout.CENTER);
        printDebug("Pannello principale completato.");

        // Pannello inferiore per Config SAMBA e Config FTP
        printDebug("Creazione del pannello inferiore per le configurazioni.");
        configButtonPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        configSambaButton = new JButton("Config SAMBA");
        configFtpButton = new JButton("Config FTP");
        configSambaButton.addActionListener(e -> handleSambaConfigButton());
        configFtpButton.addActionListener(e -> handleFtpConfigButton());
        configButtonPanel.setBorder(BorderFactory.createTitledBorder("Configurazioni Globali"));
        configButtonPanel.add(configSambaButton);
        configButtonPanel.add(configFtpButton);
        mainFrame.add(configButtonPanel, BorderLayout.SOUTH);

        mainFrame.add(mainPanel, BorderLayout.CENTER);
        printDebug("Aggiunto il pannello principale al frame.");

        mainFrame.setVisible(true);
        printDebug("GUI inizializzata e frame reso visibile.");
    }

    // Crea un'etichetta di stato
    private JLabel createStatusLabel(String serviceName, boolean isActive) {
        printDebug("Creazione dell'etichetta di stato per il servizio: " + serviceName);
        JLabel label = new JLabel(serviceName + ": " + (isActive ? "Attivo" : "Inattivo"));
        printDebug("Impostazione del testo dell'etichetta: " + label.getText());

        label.setOpaque(true);
        label.setBackground(isActive ? Color.GREEN : Color.RED);
        printDebug("Impostazione del colore di sfondo: " + (isActive ? "Verde (Attivo)" : "Rosso (Inattivo)"));

        label.setForeground(Color.WHITE);
        printDebug("Impostazione del colore del testo: Bianco");

        label.setHorizontalAlignment(SwingConstants.CENTER);
        printDebug("Allineamento del testo: Centro");

        label.setBorder(new EmptyBorder(5, 5, 5, 5));
        printDebug("Impostazione del bordo dell'etichetta con margine di 5 pixel.");

        printDebug("Etichetta di stato creata con successo per il servizio: " + serviceName);
        return label;
    }

    // Crea il pannello per i dettagli utente
    private JPanel createUserDetailPanel() {
        printDebug("Inizio creazione del pannello per i dettagli utente.");

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        printDebug("Pannello principale creato con layout BorderLayout.");

        // Pannello superiore per checkbox
        JPanel checkboxPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        printDebug("Pannello superiore per le checkbox creato con layout GridLayout.");
        ftpCheckbox = new JCheckBox("Abilitazione FTP");
        ftpCheckbox.setEnabled(false);
        printDebug("Checkbox FTP creata e inizialmente disabilitata.");
        sambaCheckbox = new JCheckBox("Abilitazione Samba");
        sambaCheckbox.setEnabled(false);
        printDebug("Checkbox Samba creata e inizialmente disabilitata.");
        checkboxPanel.add(sambaCheckbox);
        checkboxPanel.add(ftpCheckbox);
        panel.add(checkboxPanel, BorderLayout.NORTH);
        printDebug("Checkbox aggiunte al pannello superiore.");

        // Lista condivisioni FTP
        printDebug("Creazione della lista condivisioni FTP.");
        ftpShareList = new JList<>(new String[]{}); // Mock iniziale vuoto
        ftpShareList.setBorder(BorderFactory.createTitledBorder("Condivisioni FTP"));
        ftpShareList.setEnabled(false);
        ftpShareList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (ftpShareList.getSelectedIndex() != -1) {
                    printDebug("Elemento selezionato nella lista FTP. Deselezione della lista Samba.");
                    sambaShareList.clearSelection();
                }
            }
        });
        printDebug("Lista condivisioni FTP creata e listener aggiunto.");

        // Lista condivisioni Samba
        printDebug("Creazione della lista condivisioni Samba.");
        sambaShareList = new JList<>(new String[]{}); // Mock iniziale vuoto
        sambaShareList.setBorder(BorderFactory.createTitledBorder("Condivisioni SAMBA"));
        sambaShareList.setEnabled(false);
        sambaShareList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                if (sambaShareList.getSelectedIndex() != -1) {
                    printDebug("Elemento selezionato nella lista Samba. Deselezione della lista FTP.");
                    ftpShareList.clearSelection();
                }
            }
        });
        printDebug("Lista condivisioni Samba creata e listener aggiunto.");

        // Pannello per le liste di condivisioni
        printDebug("Creazione del pannello per le liste di condivisioni.");
        JPanel shareListsPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        shareListsPanel.add(new JScrollPane(sambaShareList));
        shareListsPanel.add(new JScrollPane(ftpShareList));
        panel.add(shareListsPanel, BorderLayout.CENTER);
        printDebug("Pannello per le liste di condivisioni aggiunto al pannello principale.");

        // Pannello per i pulsanti "Elimina Condivisione", "Aggiungi Condivisione" e "Info Condivisione"
        printDebug("Creazione del pannello per i pulsanti di gestione delle condivisioni.");
        JPanel shareButtonPanel = new JPanel();
        shareButtonPanel.setLayout(new BoxLayout(shareButtonPanel, BoxLayout.X_AXIS)); // Layout orizzontale
        deleteShareButton = new JButton("Elimina Condivisione");
        addShareButton = new JButton("Aggiungi Condivisione");
        infoShareButton = new JButton("Info Condivisione");

        // Configurazione pulsanti
        printDebug("Configurazione pulsanti di gestione delle condivisioni.");
        Dimension buttonSize = new Dimension(200, 30);
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
        infoShareButton.setEnabled(false);
        printDebug("Pulsanti inizializzati e disabilitati.");

        // Listener per pulsanti
        deleteShareButton.addActionListener(e -> handleDeleteShare());
        addShareButton.addActionListener(e -> handleAddShare());
        infoShareButton.addActionListener(e -> handleInfoShare());
        printDebug("Listener aggiunti ai pulsanti.");

        // Aggiunta pulsanti al pannello
        shareButtonPanel.add(deleteShareButton);
        shareButtonPanel.add(Box.createRigidArea(new Dimension(10, 0))); // Spazio orizzontale
        shareButtonPanel.add(addShareButton);
        shareButtonPanel.add(Box.createRigidArea(new Dimension(10, 0))); // Spazio orizzontale
        shareButtonPanel.add(infoShareButton);

        // Pannello per il pulsante "Gestisci Utente"
        printDebug("Creazione del pannello per il pulsante 'Gestisci Utente'.");
        JPanel manageButtonPanel = new JPanel();
        manageButtonPanel.setLayout(new BoxLayout(manageButtonPanel, BoxLayout.Y_AXIS));
        manageUserButton = new JButton("Gestisci Utente");
        manageUserButton.setEnabled(true);
        manageUserButton.setPreferredSize(buttonSize);
        manageUserButton.setMinimumSize(buttonSize);
        manageUserButton.setMaximumSize(buttonSize);
        manageUserButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        manageUserButton.addActionListener(e -> toggleUserManagement());
        manageButtonPanel.add(shareButtonPanel);
        manageButtonPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Spazio verticale
        manageButtonPanel.add(manageUserButton);
        printDebug("Pulsante 'Gestisci Utente' creato e aggiunto al pannello.");

        // Aggiungi il pannello inferiore
        panel.add(manageButtonPanel, BorderLayout.SOUTH);
        printDebug("Pannello per i dettagli utente completato.");
        return panel;
    }

    private void handleInfoShare() {
        printDebug("Inizio gestione delle informazioni sulla condivisione.");

        try {
            // Determina quale lista ha un elemento selezionato
            String selectedFtpShare = ftpShareList.getSelectedValue();
            String selectedSambaShare = sambaShareList.getSelectedValue();

            String infoText = null;

            if (selectedFtpShare != null) {
                printDebug("Condivisione FTP selezionata: " + selectedFtpShare);
                // Ottieni i dettagli della condivisione FTP dal manager
                FtpCondBean ftpShare = ftpManager.getSharesByUser(userList.getSelectedValue()).stream()
                        .filter(share -> share.getShareName().equals(selectedFtpShare))
                        .findFirst()
                        .orElse(null);

                if (ftpShare != null) {
                    printDebug("Dettagli condivisione FTP trovati.");
                    infoText = ftpShare.toFormattedString();
                } else {
                    printDebug("Dettagli condivisione FTP non trovati.");
                    showErrorDialog("Impossibile trovare i dettagli della condivisione FTP.");
                    return;
                }
            } else if (selectedSambaShare != null) {
                printDebug("Condivisione Samba selezionata: " + selectedSambaShare);
                // Ottieni i dettagli della condivisione Samba dal manager
                SmbCondBean sambaShare = sambaManager.getSharesByUser(userList.getSelectedValue()).stream()
                        .filter(share -> share.getName().equals(selectedSambaShare))
                        .findFirst()
                        .orElse(null);

                if (sambaShare != null) {
                    printDebug("Dettagli condivisione Samba trovati.");
                    infoText = sambaShare.toFormattedString();
                } else {
                    printDebug("Dettagli condivisione Samba non trovati.");
                    showErrorDialog("Impossibile trovare i dettagli della condivisione Samba.");
                    return;
                }
            } else {
                printDebug("Nessuna condivisione selezionata.");
                showErrorDialog("Nessuna condivisione selezionata!");
                return;
            }

            // Mostra il dialog con le informazioni
            printDebug("Creazione del dialog delle informazioni.");
            JTextArea textArea = new JTextArea(infoText);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);

            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(400, 300));

            JOptionPane.showMessageDialog(
                    mainFrame,
                    scrollPane,
                    "Informazioni Condivisione",
                    JOptionPane.INFORMATION_MESSAGE
            );
            printDebug("Dialog delle informazioni mostrato con successo.");

        } catch (Exception ex) {
            printDebug("Errore durante il caricamento delle informazioni: " + ex.getMessage());
            showErrorDialog("Errore durante il caricamento delle informazioni: " + ex.getMessage());
        }

        printDebug("Gestione delle informazioni sulla condivisione completata.");
    }

    // Metodo per gestire lo stato di modifica utente
    private void toggleUserManagement() {
        printDebug("Gestione dello stato di modifica utente avviata.");

        boolean isEditable = ftpCheckbox.isEnabled(); // Controlla se la modifica è già abilitata
        boolean enable = !isEditable; // Inverte lo stato (toggle)
        printDebug("Stato attuale delle checkbox: " + (isEditable ? "Modifica abilitata" : "Modifica disabilitata"));
        printDebug("Nuovo stato delle checkbox: " + (enable ? "Abilitata" : "Disabilitata"));

        if (!enable) { // Quando si clicca "Salva Modifiche"
            printDebug("Salvataggio delle modifiche utente.");
            saveUserChanges();
        }

        // Aggiorna lo stato delle checkbox
        ftpCheckbox.setEnabled(enable);
        sambaCheckbox.setEnabled(enable);

        // Gestisci lo stato delle liste basandoti sull'abilitazione dei protocolli
        if (ftpCheckbox.isSelected() && enable) {
            printDebug("Abilitazione della lista condivisioni FTP.");
            ftpShareList.setEnabled(true);
        } else {
            printDebug("Disabilitazione della lista condivisioni FTP.");
            ftpShareList.setEnabled(false);
        }

        if (sambaCheckbox.isSelected() && enable) {
            printDebug("Abilitazione della lista condivisioni Samba.");
            sambaShareList.setEnabled(true);
        } else {
            printDebug("Disabilitazione della lista condivisioni Samba.");
            sambaShareList.setEnabled(false);
        }

        // Aggiorna lo stato dei pulsanti
        deleteShareButton.setEnabled(enable);
        addShareButton.setEnabled(enable);
        infoShareButton.setEnabled(enable);
        printDebug("Stato dei pulsanti aggiornato: " + (enable ? "Abilitati" : "Disabilitati"));

        // Disabilita/abilita la lista utenti e i pulsanti Elimina Utente e Aggiungi Utente
        userList.setEnabled(!enable);
        addUserButton.setEnabled(!enable);
        deleteUserButton.setEnabled(!enable);
        configSambaButton.setEnabled(!enable);
        configFtpButton.setEnabled(!enable);
        printDebug("Stato della Lista Utenti e pulsanti Aggiungi ed Elimina Utente e pulsanti Config FTP e Config Samba aggiornato: " + (!enable ? "Abilitata" : "Disabilitata"));

        // Cambia il testo del bottone in base allo stato
        manageUserButton.setText(enable ? "Salva Modifiche" : "Gestisci Utente");
        printDebug("Testo del bottone aggiornato: " + manageUserButton.getText());

        printDebug("Gestione dello stato di modifica utente completata.");
    }

    private void populateUserPanel(String username) {
        printDebug("Inizio popolamento del pannello utente per: " + username);

        try {
            // Ottieni il bean dell'utente selezionato
            printDebug("Ricerca del bean utente per: " + username);
            UserBean user = usersManager.getUsers().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst()
                    .orElse(null);

            if (user == null) {
                printDebug("Utente non trovato: " + username);
                showErrorDialog("Utente non trovato: " + username);
                userDetailPanel.setVisible(false); // Nascondi il pannello in caso di errore
                return;
            }

            printDebug("Utente trovato: " + username);
            printDebug("Samba abilitato: " + user.isSambaEnabled() + ", FTP abilitato: " + user.isFtpEnabled());

            // Aggiorna checkbox per Samba e FTP
            ftpCheckbox.setSelected(user.isFtpEnabled());
            sambaCheckbox.setSelected(user.isSambaEnabled());
            printDebug("Checkbox aggiornate: FTP=" + ftpCheckbox.isSelected() + ", Samba=" + sambaCheckbox.isSelected());

            // Deseleziona entrambe le liste prima di popolare
            ftpShareList.clearSelection();
            sambaShareList.clearSelection();
            printDebug("Liste di condivisioni deselezionate.");

            // Gestione Samba
            if (user.isSambaEnabled()) {
                printDebug("Caricamento delle condivisioni Samba per l'utente: " + username);
                ArrayList<SmbCondBean> sambaShares = sambaManager.getSharesByUser(username);
                List<String> sambaShareNames = sambaShares.stream()
                        .map(SmbCondBean::getName) // Ottieni solo i nomi delle condivisioni
                        .toList();
                sambaShareList.setListData(sambaShareNames.toArray(new String[0]));
                printDebug("Condivisioni Samba caricate: " + sambaShareNames);
            } else {
                sambaShareList.setListData(new String[0]); // Svuota
                printDebug("Utente non abilitato a Samba. Lista Samba svuotata.");
            }

            // Gestione FTP
            if (user.isFtpEnabled()) {
                printDebug("Caricamento delle condivisioni FTP per l'utente: " + username);
                ArrayList<FtpCondBean> ftpShares = ftpManager.getSharesByUser(username);
                List<String> ftpShareNames = ftpShares.stream()
                        .map(FtpCondBean::getShareName) // Ottieni solo i nomi delle condivisioni
                        .toList();
                ftpShareList.setListData(ftpShareNames.toArray(new String[0]));
                printDebug("Condivisioni FTP caricate: " + ftpShareNames);
            } else {
                ftpShareList.setListData(new String[0]); // Svuota
                printDebug("Utente non abilitato a FTP. Lista FTP svuotata.");
            }

            // Mostra il pannello per la gestione dell'utente
            userDetailPanel.setVisible(true);
            printDebug("Pannello dei dettagli utente reso visibile per: " + username);

        } catch (Exception e) {
            printDebug("Errore durante il caricamento dei dati per l'utente: " + e.getMessage());
            showErrorDialog("Errore durante il caricamento dei dati per l'utente: " + e.getMessage());
            userDetailPanel.setVisible(false); // Nascondi il pannello in caso di errore
        }

        printDebug("Popolamento del pannello utente completato per: " + username);
    }

    // Verifica se un pacchetto è installato
    private boolean isPackageInstalled(String packageName) {
        printDebug("Verifica se il pacchetto è installato: " + packageName);
        try {
            ProcessBuilder pb = new ProcessBuilder("dpkg", "-l", packageName);
            printDebug("Esecuzione del comando: " + String.join(" ", pb.command()));
            Process process = pb.start();
            int exitCode = process.waitFor();
            boolean isInstalled = exitCode == 0;
            printDebug("Pacchetto " + packageName + (isInstalled ? " installato." : " non installato."));
            return isInstalled;
        } catch (IOException | InterruptedException e) {
            printDebug("Errore durante il controllo del pacchetto " + packageName + ": " + e.getMessage());
            System.err.println("Errore durante il controllo del pacchetto " + packageName + ": " + e.getMessage());
            return false;
        }
    }

    // Verifica se un servizio è attivo
    private boolean isServiceActive(String serviceName) {
        //printDebug("Verifica se il servizio è attivo: " + serviceName);
        try {
            ProcessBuilder pb = new ProcessBuilder("systemctl", "is-active", "--quiet", serviceName);
            //printDebug("Esecuzione del comando: " + String.join(" ", pb.command()));
            Process process = pb.start();
            int exitCode = process.waitFor();
            boolean isActive = exitCode == 0;
            //printDebug("Servizio " + serviceName + (isActive ? " attivo." : " inattivo."));
            return isActive;
        } catch (IOException | InterruptedException e) {
            printDebug("Errore durante il controllo del servizio " + serviceName + ": " + e.getMessage());
            System.err.println("Errore durante il controllo del servizio " + serviceName + ": " + e.getMessage());
            return false;
        }
    }

    private void handleAddShare() {
        printDebug("Apertura finestra di dialogo per aggiungere una condivisione.");

        // Username
        String selectedUser = userList.getSelectedValue();
        printDebug("Utente selezionato: " + (selectedUser != null ? selectedUser : "Nessuno"));

        // Crea la finestra
        JDialog addShareDialog = new JDialog(mainFrame, "Aggiungi Condivisione per: " + selectedUser, true);
        addShareDialog.setLayout(new BorderLayout(10, 10));
        addShareDialog.setSize(500, 450);
        addShareDialog.setLocationRelativeTo(mainFrame);
        printDebug("Finestra di dialogo creata con dimensioni 500x450.");

        // Checkbox per FTP e Samba
        JCheckBox ftpCheckBox = new JCheckBox("FTP");
        JCheckBox sambaCheckBox = new JCheckBox("Samba");

        // Controlla se l'utente è abilitato a Samba o FTP
        boolean isSambaEnabled = usersManager.getUsers().stream()
                .anyMatch(user -> user.getUsername().equalsIgnoreCase(selectedUser) && user.isSambaEnabled());
        boolean isFtpEnabled = usersManager.getUsers().stream()
                .anyMatch(user -> user.getUsername().equalsIgnoreCase(selectedUser) && user.isFtpEnabled());
        printDebug("Stato abilitazione utente: Samba=" + isSambaEnabled + ", FTP=" + isFtpEnabled);

        // Disabilita le checkbox se l'utente non è abilitato
        sambaCheckBox.setEnabled(isSambaEnabled);
        ftpCheckBox.setEnabled(isFtpEnabled);

        // Pannello per i campi di input
        JPanel inputPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        printDebug("Pannello input creato con layout GridLayout.");

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
        JTextField sambaBrowsableField = new JTextField("yes");
        JLabel sambaWritableLabel = new JLabel("Writable:");
        JTextField sambaWritableField = new JTextField("yes");
        JLabel sambaGuestOkLabel = new JLabel("Guest OK:");
        JTextField sambaGuestOkField = new JTextField("no");
        JLabel sambaCreateMaskLabel = new JLabel("Create Mask:");
        JTextField sambaCreateMaskField = new JTextField("0664");
        JLabel sambaDirectoryMaskLabel = new JLabel("Directory Mask:");
        JTextField sambaDirectoryMaskField = new JTextField("0775");

        printDebug("Campi specifici per Samba creati e configurati.");

        // Disabilita i campi Samba inizialmente
        sambaCommentField.setEnabled(false);
        sambaBrowsableField.setEnabled(false);
        sambaWritableField.setEnabled(false);
        sambaGuestOkField.setEnabled(false);
        sambaCreateMaskField.setEnabled(false);
        sambaDirectoryMaskField.setEnabled(false);
        printDebug("Campi Samba disabilitati inizialmente.");

        // Listener per abilitare/disabilitare i campi Samba
        sambaCheckBox.addActionListener(e -> {
            boolean enabled = sambaCheckBox.isSelected();
            printDebug("Checkbox Samba selezionata: " + enabled);
            sambaCommentField.setEnabled(enabled);
            sambaBrowsableField.setEnabled(enabled);
            sambaWritableField.setEnabled(enabled);
            sambaGuestOkField.setEnabled(enabled);
            sambaCreateMaskField.setEnabled(enabled);
            sambaDirectoryMaskField.setEnabled(enabled);
        });

        // Listener per il pulsante di selezione della cartella
        pathButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = fileChooser.showOpenDialog(addShareDialog);
            if (result == JFileChooser.APPROVE_OPTION) {
                String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();
                pathField.setText(selectedPath);
                printDebug("Percorso selezionato: " + selectedPath);
            }
        });

        // Aggiunta campi al pannello input
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
        printDebug("Campi e layout del pannello input completati.");

        // Pulsanti
        JButton confirmButton = new JButton("Conferma");
        JButton cancelButton = new JButton("Annulla");

        confirmButton.addActionListener(e -> {
            printDebug("Pulsante Conferma cliccato.");
            try {
                // Validazione dei dati comuni
                if (shareNameField.getText().isEmpty() || pathField.getText().isEmpty()) {
                    printDebug("Errore: Nome condivisione o percorso vuoti.");
                    showErrorDialog("Nome Condivisione e Percorso sono obbligatori.");
                    return;
                }

                String shareName = shareNameField.getText();
                String path = pathField.getText();

                if (selectedUser == null) {
                    printDebug("Errore: Utente non selezionato.");
                    showErrorDialog("Seleziona un utente prima di aggiungere una condivisione.");
                    return;
                }

                // Gestione FTP
                if (ftpCheckBox.isSelected()) {
                    printDebug("Gestione FTP selezionata.");
                    // Controllo duplicati
                    boolean ftpDuplicate = ftpManager.getSharesByUser(selectedUser).stream()
                            .anyMatch(share -> share.getShareName().equals(shareName) || share.getPath().equals(path));

                    if (ftpDuplicate) {
                        printDebug("Errore: Duplicato FTP trovato.");
                        showErrorDialog("Esiste già una condivisione FTP con lo stesso nome o percorso per l'utente selezionato.");
                        return;
                    }

                    ftpManager.addShare(selectedUser, shareName, path);
                    printDebug("Condivisione FTP aggiunta: " + shareName + " - " + path);

                    ftpShareList.setListData(ftpManager.getSharesByUser(selectedUser)
                            .stream()
                            .map(FtpCondBean::getShareName)
                            .toArray(String[]::new));
                    printDebug("Lista FTP aggiornata.");
                }

                // Gestione Samba
                if (sambaCheckBox.isSelected()) {
                    printDebug("Gestione Samba selezionata.");

                    // Controllo per nome duplicato con percorso diverso
                    printDebug("Controllo per nome duplicato con percorso diverso...");
                    boolean nameConflict = sambaManager.getAllShares().stream()
                            .anyMatch(share -> share.getName().equalsIgnoreCase(shareName) &&
                                    share.getProperties().stream()
                                            .noneMatch(property -> property[0].equalsIgnoreCase("path") &&
                                                    property[1].equals(path)));

                    if (nameConflict) {
                        printDebug("Conflitto rilevato: Esiste già una condivisione Samba con lo stesso nome ma un percorso diverso.");
                        showErrorDialog("Esiste già una condivisione Samba con lo stesso nome ma un percorso diverso. Modifica i dettagli.");
                        return;
                    }
                    printDebug("Nessun conflitto di nome rilevato.");

                    // Controllo per percorso già condiviso
                    printDebug("Controllo per percorso già condiviso...");
                    SmbCondBean existingShare = sambaManager.getAllShares().stream()
                            .filter(share -> share.getProperties().stream()
                                    .anyMatch(property -> property[0].equalsIgnoreCase("path") && property[1].equals(path)))
                            .findFirst()
                            .orElse(null);

                    if (existingShare != null) {
                        printDebug("Percorso già condiviso rilevato con il nome: " + existingShare.getName());
                        // Aggiungi l'utente ai valid users
                        existingShare.addValidUser(selectedUser);
                        sambaManager.modifyShare(existingShare.getName(), existingShare);
                        printDebug("Utente aggiunto ai valid users della condivisione esistente: " + existingShare.getName());

                        // Mostra dialog informativo
                        showInfoDialog("Il percorso è già condiviso con il nome '" + existingShare.getName() +
                                "'. L'utente è stato aggiunto ai valid users.");
                    } else {
                        printDebug("Percorso non condiviso. Creazione di una nuova condivisione Samba...");

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

                        printDebug("Nuova condivisione Samba creata con il nome: " + shareName + " e percorso: " + path);
                    }

                    // Aggiorna la lista delle condivisioni Samba visivamente
                    printDebug("Aggiornamento della lista delle condivisioni Samba per l'utente: " + selectedUser);
                    sambaShareList.setListData(sambaManager.getSharesByUser(selectedUser)
                            .stream()
                            .map(SmbCondBean::getName)
                            .toArray(String[]::new));
                    printDebug("Lista delle condivisioni Samba aggiornata.");
                }

                addShareDialog.dispose();
                printDebug("Dialog chiusa con successo.");
            } catch (Exception ex) {
                printDebug("Errore durante l'aggiunta della condivisione: " + ex.getMessage());
                showErrorDialog("Errore durante l'aggiunta della condivisione: " + ex.getMessage());
            }
        });

        cancelButton.addActionListener(e -> {
            printDebug("Pulsante Annulla cliccato. Dialog chiusa.");
            addShareDialog.dispose();
        });

        // Pannello per i pulsanti
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(confirmButton);
        buttonPanel.add(cancelButton);

        // Aggiungi tutto alla finestra
        addShareDialog.add(inputPanel, BorderLayout.CENTER);
        addShareDialog.add(buttonPanel, BorderLayout.SOUTH);

        printDebug("Dialog completata e visibile.");
        addShareDialog.setVisible(true);
    }

    private void handleDeleteShare() {
        printDebug("Inizio eliminazione della condivisione.");

        try {
            String selectedFtpShare = ftpShareList.getSelectedValue();
            String selectedSambaShare = sambaShareList.getSelectedValue();
            String selectedUser = userList.getSelectedValue();

            printDebug("Utente selezionato: " + (selectedUser != null ? selectedUser : "Nessuno"));
            printDebug("Condivisione FTP selezionata: " + (selectedFtpShare != null ? selectedFtpShare : "Nessuna"));
            printDebug("Condivisione Samba selezionata: " + (selectedSambaShare != null ? selectedSambaShare : "Nessuna"));

            if (selectedUser == null) {
                printDebug("Errore: Nessun utente selezionato.");
                showErrorDialog("Seleziona un utente prima di eliminare una condivisione.");
                return;
            }

            if (selectedFtpShare == null && selectedSambaShare == null) {
                printDebug("Errore: Nessuna condivisione selezionata.");
                showErrorDialog("Seleziona una condivisione da eliminare.");
                return;
            }

            String shareName = selectedFtpShare != null ? selectedFtpShare : selectedSambaShare;
            String protocol = selectedFtpShare != null ? "FTP" : "Samba";

            printDebug("Condivisione selezionata: " + shareName + " (" + protocol + ")");

            // Chiede conferma all'utente
            int confirm = JOptionPane.showConfirmDialog(
                    null,
                    "Sei sicuro di voler rimuovere la condivisione " + shareName + ", " + protocol + "?",
                    "Conferma Eliminazione",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm != JOptionPane.YES_OPTION) {
                printDebug("Eliminazione annullata dall'utente.");
                return; // L'utente ha annullato
            }

            if (selectedFtpShare != null) {
                printDebug("Inizio eliminazione della condivisione FTP: " + selectedFtpShare);
                // Rimuove la condivisione FTP
                FtpCondBean ftpShare = ftpManager.getSharesByUser(selectedUser).stream()
                        .filter(share -> share.getShareName().equals(selectedFtpShare))
                        .findFirst()
                        .orElse(null);

                if (ftpShare != null) {
                    printDebug("Condivisione FTP trovata: " + ftpShare.getShareName());
                    ftpManager.removeShare(ftpShare); // Rimuove solo dalla lista temporanea
                    printDebug("Condivisione FTP rimossa: " + ftpShare.getShareName());
                    ftpShareList.setListData(ftpManager.getSharesByUser(selectedUser)
                            .stream()
                            .map(FtpCondBean::getShareName)
                            .toArray(String[]::new)); // Aggiorna la lista visivamente
                    printDebug("Lista condivisioni FTP aggiornata.");
                } else {
                    printDebug("Errore: Condivisione FTP non trovata.");
                    showErrorDialog("Condivisione FTP non trovata.");
                }
            } else if (selectedSambaShare != null) {
                printDebug("Inizio eliminazione della condivisione Samba: " + selectedSambaShare);
                // Rimuove l'utente dai valid users di una condivisione Samba
                SmbCondBean sambaShare = sambaManager.getSharesByUser(selectedUser).stream()
                        .filter(share -> share.getName().equals(selectedSambaShare))
                        .findFirst()
                        .orElse(null);

                if (sambaShare != null) {
                    printDebug("Condivisione Samba trovata: " + sambaShare.getName());
                    sambaShare.removeValidUser(selectedUser);
                    printDebug("Utente rimosso dai valid users della condivisione: " + sambaShare.getName());
                    sambaManager.modifyShare(sambaShare.getName(), sambaShare); // Usa il modifyShare aggiornato
                    printDebug("Condivisione Samba aggiornata: " + sambaShare.getName());
                    sambaShareList.setListData(sambaManager.getSharesByUser(selectedUser)
                            .stream()
                            .map(SmbCondBean::getName)
                            .toArray(String[]::new)); // Aggiorna la lista visivamente
                    printDebug("Lista condivisioni Samba aggiornata.");
                } else {
                    printDebug("Errore: Condivisione Samba non trovata.");
                    showErrorDialog("Condivisione Samba non trovata.");
                }
            }
        } catch (Exception ex) {
            printDebug("Errore durante l'eliminazione della condivisione: " + ex.getMessage());
            showErrorDialog("Errore durante l'eliminazione della condivisione: " + ex.getMessage());
        }

        printDebug("Eliminazione della condivisione completata.");
    }

    private void saveUserChanges() {
        printDebug("Inizio salvataggio delle modifiche utente.");

        // Chiedi conferma all'utente
        int confirm = JOptionPane.showConfirmDialog(
                null,
                "Sei sicuro di voler salvare le modifiche? I servizi FTP e Samba saranno riavviati.",
                "Conferma Salvataggio",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            printDebug("Salvataggio annullato dall'utente.");
            return; // L'utente ha annullato l'operazione
        }

        try {
            // Disattiva i servizi Samba e FTP
            printDebug("Arresto dei servizi Samba e FTP.");
            sambaManager.stopSambaService();
            ftpManager.stopFtpService();

            // Ottieni l'utente selezionato
            String selectedUser = userList.getSelectedValue();
            printDebug("Utente selezionato: " + (selectedUser != null ? selectedUser : "Nessuno"));

            if (selectedUser == null) {
                printDebug("Errore: Nessun utente selezionato.");
                showErrorDialog("Nessun utente selezionato.");
                return;
            }

            // Ottieni il bean dell'utente
            UserBean user = usersManager.getUsers().stream()
                    .filter(u -> u.getUsername().equals(selectedUser))
                    .findFirst()
                    .orElse(null);
            printDebug("Bean utente ottenuto: " + (user != null ? user.getUsername() : "Utente non trovato."));

            if (user == null) {
                printDebug("Errore: Utente non trovato.");
                showErrorDialog("Utente non trovato.");
                return;
            }

            // Gestione abilitazione/disabilitazione FTP
            if (ftpCheckbox.isSelected() && !user.isFtpEnabled()) {
                printDebug("Abilitazione FTP per l'utente: " + selectedUser);
                usersManager.enableFtp(selectedUser);
                showInfoDialog("Utente abilitato a FTP.");
            } else if (!ftpCheckbox.isSelected() && user.isFtpEnabled()) {
                printDebug("Rimozione FTP per l'utente: " + selectedUser);
                usersManager.disableFtp(selectedUser);
                showInfoDialog("Utente rimosso da FTP.");
            }

            // Gestione abilitazione/disabilitazione Samba
            if (sambaCheckbox.isSelected() && !user.isSambaEnabled()) {
                printDebug("Abilitazione Samba per l'utente: " + selectedUser);
                String password = JOptionPane.showInputDialog(
                        null,
                        "Inserisci una password per l'utente Samba:",
                        "Password Samba",
                        JOptionPane.PLAIN_MESSAGE
                );

                if (password != null && !password.isEmpty()) {
                    printDebug("Password Samba fornita per l'utente: " + selectedUser);
                    usersManager.enableSamba(selectedUser, password);
                    showInfoDialog("Utente abilitato a Samba.");
                } else {
                    printDebug("Operazione annullata: Password Samba non fornita.");
                    showErrorDialog("Operazione annullata: la password per Samba è obbligatoria.");
                    sambaManager.startSambaService();
                    ftpManager.startFtpService();
                    return; // Interrompi l'operazione
                }
            } else if (!sambaCheckbox.isSelected() && user.isSambaEnabled()) {
                printDebug("Rimozione Samba per l'utente: " + selectedUser);
                usersManager.disableSamba(selectedUser);
                showInfoDialog("Utente rimosso da Samba.");
            }

            boolean changesYesOrNot = false;

            // Salvataggio configurazioni solo se il servizio era già abilitato
            if (ftpCheckbox.isSelected() && user.isFtpEnabled()) {
                printDebug("Salvataggio configurazioni FTP su disco.");
                ftpManager.saveSharesOnDisk();
                changesYesOrNot = true;
            }

            if (sambaCheckbox.isSelected() && user.isSambaEnabled()) {
                printDebug("Salvataggio configurazioni Samba.");
                sambaManager.updateConfig();
                changesYesOrNot = true;
            }

            if(changesYesOrNot) {
                usersManager.setPermissionForUser(selectedUser);
                printDebug("Aggiornati i permessi per l'utente: " + selectedUser + " sulle eventuali nuove condivisioni.");
            }

            // Riavvia i servizi Samba e FTP
            printDebug("Riavvio dei servizi Samba e FTP.");
            sambaManager.startSambaService();
            ftpManager.startFtpService();

            showInfoDialog("Modifiche salvate con successo e servizi riavviati.");
            printDebug("Salvataggio delle modifiche completato con successo.");
        } catch (IOException e) {
            printDebug("Errore durante il salvataggio delle modifiche: " + e.getMessage());
            showErrorDialog("Errore durante il salvataggio delle modifiche: " + e.getMessage());
        } catch (Exception e) {
            printDebug("Errore inatteso: " + e.getMessage());
            showErrorDialog("Errore inatteso: " + e.getMessage());
        }
    }

    private void deleteUser(String username) {
        printDebug("Avvio del processo di eliminazione dell'utente: " + username);

        // Chiedi conferma all'utente
        int confirm = JOptionPane.showConfirmDialog(
                mainFrame,
                "Sei sicuro di voler eliminare l'utente '" + username + "'? Questa azione non può essere annullata.",
                "Conferma Eliminazione Utente",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            printDebug("Eliminazione annullata dall'utente.");
            return; // L'utente ha annullato l'operazione
        }

        try {
            printDebug("Invocazione di UserManager.removeUser per l'utente: " + username);
            usersManager.removeUser(username);
            printDebug("Utente rimosso con successo: " + username);

            // Mostra dialog di conferma
            printDebug("Mostra dialog di conferma per l'eliminazione dell'utente: " + username);
            showInfoDialog("Utente '" + username + "' eliminato con successo.");

            // Aggiorna la lista utenti nella GUI
            printDebug("Aggiornamento della lista utenti nella GUI.");
            userList.setListData(usersManager.getUsernames());
        } catch (IOException | InterruptedException ex) {
            printDebug("Errore durante l'eliminazione dell'utente: " + ex.getMessage());
            showErrorDialog("Errore durante l'eliminazione dell'utente: " + ex.getMessage());
        }
    }

    private void openAddUserDialog() {
        printDebug("Apertura finestra di dialogo per l'aggiunta di un nuovo utente.");

        // Crea il dialog
        JDialog addUserDialog = new JDialog(mainFrame, "Aggiungi Nuovo Utente", true);
        addUserDialog.setLayout(new BorderLayout(10, 10));
        addUserDialog.setSize(400, 300);
        addUserDialog.setLocationRelativeTo(mainFrame);
        printDebug("Dialog creato con dimensioni 400x300.");

        // Pannello per i campi di input
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 10, 10)); // 4 righe, 2 colonne, spaziatura 10px

        // Campi di input
        JLabel usernameLabel = new JLabel("Username:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Password:");
        JPasswordField passwordField = new JPasswordField();
        JCheckBox enableSambaCheckbox = new JCheckBox("Abilita Samba");
        JCheckBox enableFtpCheckbox = new JCheckBox("Abilita FTP");

        // Aggiungi i campi al pannello
        inputPanel.add(usernameLabel);
        inputPanel.add(usernameField);
        inputPanel.add(passwordLabel);
        inputPanel.add(passwordField);
        inputPanel.add(new JLabel()); // Spazio vuoto per allineamento
        inputPanel.add(enableSambaCheckbox);
        inputPanel.add(new JLabel()); // Spazio vuoto per allineamento
        inputPanel.add(enableFtpCheckbox);
        printDebug("Campi di input configurati e aggiunti al pannello.");

        // Pannello per i pulsanti
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton confirmButton = new JButton("Conferma");
        JButton cancelButton = new JButton("Annulla");
        buttonPanel.add(confirmButton);
        buttonPanel.add(cancelButton);
        printDebug("Pannello pulsanti configurato.");

        // Listener per il pulsante Conferma
        confirmButton.addActionListener(e -> {
            printDebug("Pulsante 'Conferma' cliccato.");
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            boolean enableSamba = enableSambaCheckbox.isSelected();
            boolean enableFtp = enableFtpCheckbox.isSelected();

            // Validazione
            if (username.isEmpty() || password.isEmpty()) {
                printDebug("Errore: Username o password vuoti.");
                showErrorDialog("Username e password sono obbligatori.");
                return;
            }

            try {
                printDebug("Invocazione di UserManager.addUser con username=" + username +
                        ", enableSamba=" + enableSamba + ", enableFtp=" + enableFtp);
                usersManager.addUser(username, password, enableFtp, enableSamba);
                printDebug("Utente aggiunto con successo: " + username);

                // Costruisci il messaggio di conferma
                StringBuilder successMessage = new StringBuilder("Utente '" + username + "' creato");
                if (enableSamba || enableFtp) {
                    successMessage.append(" e abilitato a");
                    if (enableSamba) successMessage.append(" Samba");
                    if (enableSamba && enableFtp) successMessage.append(" e");
                    if (enableFtp) successMessage.append(" FTP");
                }
                successMessage.append(".");

                // Mostra dialog di conferma
                printDebug("Mostra dialog di conferma: " + successMessage.toString());
                showInfoDialog(successMessage.toString());

                // Aggiorna la lista utenti nella GUI
                userList.setListData(usersManager.getUsernames());
                addUserDialog.dispose(); // Chiudi il dialog
            } catch (IOException ex) {
                printDebug("Errore durante l'aggiunta dell'utente: " + ex.getMessage());
                showErrorDialog("Errore durante l'aggiunta dell'utente: " + ex.getMessage());
            }
        });

        // Listener per il pulsante Annulla
        cancelButton.addActionListener(e -> {
            printDebug("Pulsante 'Annulla' cliccato. Chiusura del dialog.");
            addUserDialog.dispose();
        });

        // Aggiungi i pannelli al dialog
        addUserDialog.add(inputPanel, BorderLayout.CENTER);
        addUserDialog.add(buttonPanel, BorderLayout.SOUTH);
        printDebug("Pannelli aggiunti al dialog.");

        // Mostra il dialog
        addUserDialog.setVisible(true);
    }

    public void handleSambaConfigButton() {
        // Crea una finestra per la modifica della configurazione Samba
        JFrame frame = new JFrame("Modifica Configurazione Samba");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);

        // Carica la configurazione attuale dal SambaManager
        String currentConfig = sambaManager.getFormattedGlobalSettings()
                + "\n"
                + sambaManager.getFormattedHomeSettings()
                + "\n"
                + sambaManager.getFormattedShares();

        // Aggiungi una Text Area per visualizzare e modificare il contenuto
        JTextArea textArea = new JTextArea(currentConfig);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Font per file di configurazione
        JScrollPane scrollPane = new JScrollPane(textArea);

        // Aggiungi i pulsanti Salva e Annulla
        JButton saveButton = new JButton("Salva");
        JButton cancelButton = new JButton("Annulla");

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Layout della finestra
        frame.setLayout(new BorderLayout());
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        // Listener per il pulsante Salva
        saveButton.addActionListener(e -> {
            String newConfig = textArea.getText();

            try {
                // Aggiorna la configurazione interna con il nuovo testo
                sambaManager.readConfigFromText(newConfig);

                // Salva il nuovo contenuto nel file
                sambaManager.updateConfig();

                //Riavvio Samba
                sambaManager.stopSambaService();
                sambaManager.startSambaService();

                // Mostra una notifica
                showInfoDialog("Configurazione Samba aggiornata con successo, servizio riavviato.");
                frame.dispose();
            } catch (IOException | InterruptedException ex) {
                showErrorDialog("Errore durante l'aggiornamento della configurazione: " + ex.getMessage());
            }
        });

        // Listener per il pulsante Annulla
        cancelButton.addActionListener(e -> frame.dispose());

        // Mostra la finestra
        frame.setVisible(true);
    }

    public void handleFtpConfigButton() {
        // Crea una finestra per la modifica della configurazione FTP
        JFrame frame = new JFrame("Modifica Configurazione FTP");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);

        // Carica la configurazione attuale dal FtpManager
        String currentConfig = ftpManager.getFormattedConfig();

        // Aggiungi una Text Area per visualizzare e modificare il contenuto
        JTextArea textArea = new JTextArea(currentConfig);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Font per file di configurazione
        JScrollPane scrollPane = new JScrollPane(textArea);

        // Aggiungi i pulsanti Salva e Annulla
        JButton saveButton = new JButton("Salva");
        JButton cancelButton = new JButton("Annulla");

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        // Layout della finestra
        frame.setLayout(new BorderLayout());
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        // Listener per il pulsante Salva
        saveButton.addActionListener(e -> {
            String newConfig = textArea.getText();

            try {
                // Aggiorna la configurazione interna con il nuovo testo
                ftpManager.readConfigFromText(newConfig);

                // Salva il nuovo contenuto nel file
                ftpManager.updateConfig();

                //Riavvia il Servizio
                ftpManager.stopFtpService();
                ftpManager.startFtpService();

                // Mostra una notifica
                showInfoDialog("Configurazione FTP aggiornata con successo, servizio riavviato.");
                frame.dispose();
            } catch (IOException | InterruptedException ex) {
                showErrorDialog("Errore durante l'aggiornamento della configurazione: " + ex.getMessage());
            }
        });

        // Listener per il pulsante Annulla
        cancelButton.addActionListener(e -> frame.dispose());

        // Mostra la finestra
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        MainManager manager = new MainManager(); // Istanziazione della classe
        manager.toggleDebug(); // Attivazione immediata del debug
    }

}

