package slave2;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Classe SlaveServer
 * Chaque Slave reçoit des morceaux de fichiers envoyés par le Master (UPLOAD),
 * les stocke localement, et peut ensuite renvoyer ces morceaux au Master (DOWNLOAD).
 */
public class SlaveServer {

    private ServerSocket serverSocket; // Socket d'écoute du Slave
    private InetAddress masterIP;      // Adresse IP autorisée (celle du Master)
    private Path baseDir;              // Répertoire de stockage local des fichiers

    /**
     * Constructeur du SlaveServer
     * @param port Port sur lequel le Slave écoute
     * @param baseDir Répertoire où les fichiers/morceaux seront stockés
     */
    public SlaveServer(int port, Path baseDir) throws IOException {
        this.baseDir = baseDir;
        Files.createDirectories(baseDir); // Création du dossier si inexistant
        this.serverSocket = new ServerSocket(port);
        loadMasterIP(); // Chargement de l'adresse IP du Master autorisé
    }

    /**
     * Lecture du fichier masteraddress.txt pour connaître l'IP du Master
     * Format attendu : "IP;;PORT"
     */
    private void loadMasterIP() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("masteraddress.txt"))) {
            String[] parts = reader.readLine().split(";;");
            masterIP = InetAddress.getByName(parts[0]);
        }
    }

    /**
     * Fonction principale d'écoute
     * - Attend une connexion du Master
     * - Vérifie que la connexion vient bien du Master autorisé
     * - Lit la commande envoyée (UPLOAD ou DOWNLOAD)
     * - Exécute l'action correspondante
     */
    public void ecoute() {
        try (Socket socket = serverSocket.accept()) {

            // Vérification de l'origine de la connexion
            if (!socket.getInetAddress().equals(masterIP)) {
                socket.close();
                return;
            }

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Lecture de la commande envoyée par le Master
            String commande = in.readUTF();

            if (commande.equals("UPLOAD")) {
                // Réception d’un morceau de fichier
                recevoirFichier(in);

            } else if (commande.equals("DOWNLOAD")) {
                // Envoi d’un morceau de fichier
                envoyerFichier(in, out);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Réception d’un fichier/morceau depuis le Master
     * @param in flux d’entrée (données envoyées par le Master)
     */
    private void recevoirFichier(DataInputStream in) throws IOException {
        String nomFichier = in.readUTF();   // Nom du fichier/morceau
        long taille = in.readLong();        // Taille du fichier/morceau

        Path fichier = baseDir.resolve(nomFichier); // Chemin local de stockage

        try (OutputStream fileOut = Files.newOutputStream(fichier)) {
            byte[] buffer = new byte[4096];
            long total = 0;
            int read;

            // Lecture et écriture des octets jusqu’à atteindre la taille attendue
            while (total < taille && (read = in.read(buffer)) != -1) {
                fileOut.write(buffer, 0, read);
                total += read;
            }
        }

        System.out.println("Fichier reçu : " + fichier);
    }

    /**
     * Envoi d’un fichier/morceau au Master
     * @param in flux d’entrée (nom du fichier demandé)
     * @param out flux de sortie (données envoyées au Master)
     */
    private void envoyerFichier(DataInputStream in, DataOutputStream out) throws IOException {
        String nomFichier = in.readUTF();   // Nom du fichier/morceau demandé
        Path fichier = baseDir.resolve(nomFichier);

        if (!Files.exists(fichier)) {
            out.writeUTF("NOT_FOUND"); // Si le fichier n’existe pas
            return;
        }

        long taille = Files.size(fichier);
        out.writeUTF("FOUND");        // Confirmation que le fichier existe
        out.writeLong(taille);        // Envoi de la taille

        try (DataInputStream fileIn = new DataInputStream(Files.newInputStream(fichier))) {
            byte[] buffer = new byte[4096];
            int read;
            // Lecture et envoi des octets au Master
            while ((read = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        System.out.println("Fichier envoyé : " + fichier);
    }

    /**
     * Fermeture du serveur
     */
    public void close() throws IOException {
        serverSocket.close();
    }

    /**
     * Point d’entrée du programme Slave
     * @param args [0] = port, [1] = dossier de stockage
     */
    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 5001;
        Path directory = (args.length > 1) ? Paths.get(args[1]) : Paths.get("storage");

        SlaveServer slave = new SlaveServer(port, directory);
        slave.ecoute();
        slave.close();
    }
}
