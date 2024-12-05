# FTP_Samba GUI Manager

**FTP_Samba GUI Manager** is a Java graphical application designed to simplify the management of FTP and Samba servers on Debian-based systems. It offers an intuitive interface to configure shares, users, and service parameters, directly integrating changes into the configuration files.

---

## Features

- **User Management**:
  - Add/remove FTP and Samba users.
  - Assign users to specific shares.
  - Automatically configure access permissions.

- **Share Management**:
  - Create, edit, and remove FTP and Samba shares.
  - Configure valid users, paths, and advanced parameters.

- **Configuration Editing**:
  - Built-in editor for Samba (`smb.conf`) and FTP (`vsftpd.conf`) configuration files.
  - Save changes with automatic backup.
  - Automatically restart services after each update.

- **User-friendly GUI**:
  - A window with a text editor to modify configuration files.
  - Action buttons to save or discard changes.
  - Detailed notifications for successful operations or errors.

---

## System Requirements

- **Operating System**: Debian-based (tested on Debian 12).
- **Java**: JDK 17 or higher.
- **Administrator Privileges**: The app must be run as root to modify system files and restart services.

---

## Download Jar File from Releases

A precompiled Jar file is included in each release. You can download and use it directly or clone the repository and compile it yourself.

---

## Installation and Configuration

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/ftp_samba_gui_manager.git
   cd ftp_samba_gui_manager
   ```

2. **Compile the project**:
   ```bash
   javac -d out -sourcepath src src/org/antonio/Main.java
   ```

3. **Run the application**:
   ```bash
   java -cp out org.antonio.Main
   ```

4. **Configure paths**:
   - Verify and update the paths in the code if necessary:
     - Samba configuration file: `/etc/samba/smb.conf`.
     - FTP configuration file: `/etc/vsftpd.conf`.
     - FTP user file: `/etc/vsftpd.userlist`.

---

## Usage

### Adding a User

1. In the GUI, select "Add User."
2. Enter the username, password, and enable FTP/Samba.

### Deleting a User

1. In the GUI, select a user.
2. Click "Delete User" and confirm.

### Editing Shares and Permissions

1. Select a user; the panel with their details will populate.
2. Click "Manage User."
3. Add or remove shares for Samba and/or FTP.
4. Enable or disable Samba and/or FTP access for the user.
5. Save the changes to update the configuration and restart the services.

---

## Contributions

Contributions and suggestions are welcome! Feel free to open issues or pull requests.

---
