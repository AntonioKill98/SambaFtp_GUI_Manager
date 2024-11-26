# FTP_Samba GUI Manager

**FTP_Samba GUI Manager** è un'applicazione Java con interfaccia grafica progettata per semplificare la gestione dei server FTP e Samba su sistemi basati su Debian. Offre un'interfaccia intuitiva per configurare condivisioni, utenti e parametri di servizio, integrando le modifiche direttamente nei file di configurazione.

---

## Funzionalità

- **Gestione utenti**:
  - Aggiunta/rimozione di utenti FTP e Samba.
  - Associazione di utenti a condivisioni specifiche.
  - Configurazione automatica dei permessi di accesso.

- **Gestione condivisioni**:
  - Creazione, modifica e rimozione di condivisioni FTP e Samba.
  - Configurazione di valid users, percorsi e parametri avanzati.

- **Modifica della configurazione**:
  - Editor integrato per i file di configurazione di Samba (`smb.conf`) e FTP (`vsftpd.conf`).
  - Salvataggio delle modifiche con backup automatico.
  - Riavvio automatico dei servizi dopo ogni aggiornamento.

- **Interfaccia grafica user-friendly**:
  - Finestra con text editor per modificare i file di configurazione.
  - Bottoni di azione per salvare o annullare le modifiche.
  - Notifiche dettagliate per operazioni di successo o errori.

---

## Requisiti di sistema

- **Sistema operativo**: Debian-based (testato su Debian 12).
- **Java**: JDK 17 o superiore.
- **Permessi di amministratore**: L'app richiede privilegi `sudo` per modificare file di sistema e riavviare i servizi.

---

## Installazione e configurazione

1. **Clona il repository**:
   ```bash
   git clone https://github.com/tuo-username/ftp_samba_gui_manager.git
   cd ftp_samba_gui_manager
   ```

2. **Compila il progetto**:
   ```bash
   javac -d out -sourcepath src src/org/antonio/Main.java
   ```

3. **Esegui l'applicazione**:
   ```bash
   java -cp out org.antonio.Main
   ```

4. **Configura i percorsi**:
   - Verifica e aggiorna i percorsi nel codice, se necessario:
     - Percorso del file Samba: `/etc/samba/smb.conf`.
     - Percorso del file FTP: `/etc/vsftpd.conf`.
     - File utenti FTP: `/etc/vsftpd.userlist`.

---

## Utilizzo

### Aggiunta di un utente

1. Nella GUI, seleziona "Aggiungi Utente".
2. Inserisci nome utente, password e abilita FTP/Samba.
3. Configura le condivisioni e salva.

### Modifica delle condivisioni

1. Clicca su "Configura Samba" o "Configura FTP".
2. Modifica i file direttamente nella text box.
3. Salva per aggiornare la configurazione e riavviare i servizi.

---

## Contributi

Contributi e suggerimenti sono benvenuti! Sentiti libero di aprire issue o pull request.

---

## Licenza

Questo progetto è distribuito sotto la licenza MIT. Consulta il file [LICENSE](LICENSE) per maggiori dettagli.

---
