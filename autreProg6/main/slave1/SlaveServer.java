package main.slave1;

import java.io.*;
import java.net.*;
import java.nio.file.*;

/**
 * Serveur Slave
 * Rôle :
 * - Stocker des morceaux de fichiers (chunks)
 * - Répondre aux demandes UPLOAD et DOWNLOAD du Master
 */
public class SlaveServer {

    // Socket d'écoute du slave
    private ServerSocket serverSocket;

    // Répertoire de stockage des chunks
    private Path baseDir;

    /**
     * Constructeur du Slave
     * @param port Port d'écoute
     * @param baseDir Dossier de stockage
     */
    public SlaveServer(int port, Path baseDir) throws IOException {
        this.baseDir = baseDir;

        // Crée le dossier s'il n'existe pas
        Files.createDirectories(baseDir);

        // Démarre le serveur
        this.serverSocket = new ServerSocket(port);

        System.out.println("Slave démarré sur port " + port);
    }

    /**
     * Boucle principale du Slave
     * Attend les connexions du Master
     */
    public void ecoute() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();

                // Un thread par connexion
                new Thread(() -> gererConnexion(socket)).start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gère une connexion du Master
     * Peut être UPLOAD ou DOWNLOAD
     */
    private void gererConnexion(Socket socket) {

        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // Lecture de la commande
            String commande = in.readUTF();

            // ================= UPLOAD =================
            if ("UPLOAD".equals(commande)) {

                String nomFichier = in.readUTF(); // ex: fichier.part0
                long taille = in.readLong();      // taille du chunk

                Path fichier = baseDir.resolve(nomFichier);

                // Réception du chunk
                try (OutputStream fos = Files.newOutputStream(fichier)) {

                    byte[] buffer = new byte[8192];
                    long totalLu = 0;

                    while (totalLu < taille) {

                        int aLire = (int) Math.min(buffer.length, taille - totalLu);
                        int lus = in.read(buffer, 0, aLire);

                        if (lus == -1) {
                            throw new IOException("Fin de flux inattendue");
                        }

                        fos.write(buffer, 0, lus);
                        totalLu += lus;
                    }

                    fos.flush();
                }

                // 🔴 IMPORTANT : confirmation au Master
                out.writeUTF("SUCCESS");
                out.flush();

                System.out.println("Chunk reçu : " + fichier);
            }

            // ================= DOWNLOAD =================
            else if ("DOWNLOAD".equals(commande)) {

                String nomFichier = in.readUTF();
                Path fichier = baseDir.resolve(nomFichier);

                // Vérifie si le chunk existe
                if (!Files.exists(fichier)) {
                    out.writeUTF("NOT_FOUND");
                    out.flush();
                    return;
                }

                long taille = Files.size(fichier);

                // Informe le Master
                out.writeUTF("FOUND");
                out.writeLong(taille);
                out.flush();

                // Envoi du chunk
                try (InputStream fis = Files.newInputStream(fichier)) {

                    byte[] buffer = new byte[8192];
                    int lus;

                    while ((lus = fis.read(buffer)) != -1) {
                        out.write(buffer, 0, lus);
                    }

                    out.flush();
                }

                System.out.println("Chunk envoyé : " + fichier);
            }

        } catch (IOException e) {
            System.err.println("Erreur Slave : " + e.getMessage());
        }
    }

    /**
     * Point d'entrée du programme
     * args[0] : port
     * args[1] : dossier de stockage
     */
    public static void main(String[] args) throws IOException {

        int port = (args.length > 0)
                ? Integer.parseInt(args[0])
                : 5002;

        Path dir = (args.length > 1)
                ? Paths.get(args[1])
                : Paths.get("storage");

        SlaveServer slave = new SlaveServer(port, dir);
        slave.ecoute();
    }
}
