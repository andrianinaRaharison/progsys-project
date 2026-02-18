package main.client;

import java.io.*;
import java.net.Socket;

public class ClientDownload {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage : java ClientDownload <nom_fichier_a_download> [fichier_sortie]");
            return;
        }

        String nomFichier = args[0];
        String fichierSortie = args.length > 1 ? args[1] : "recu_" + nomFichier;

        try (Socket socket = new Socket("localhost", 5001);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            System.out.println("Connexion au Master pour téléchargement...");

            // Demande de téléchargement
            out.writeUTF("DOWNLOAD");
            out.writeUTF(nomFichier);
            //out.writeUTF(fichierSortie);   // ← LIGNE MANQUANTE
            //out.flush();

            String status = in.readUTF();
            if (status.equals("NOT_FOUND")) {
                System.out.println("❌ Fichier introuvable sur le Master : " + nomFichier);
                return;
            }

            long tailleTotale = in.readLong();
            try (FileOutputStream fileOut = new FileOutputStream(fichierSortie)) {
                byte[] buffer = new byte[8192];
                long restant = tailleTotale;
                int lus;

                while (restant > 0 && (lus = in.read(buffer, 0, (int)Math.min(buffer.length, restant))) != -1) {
                    fileOut.write(buffer, 0, lus);
                    restant -= lus;
                }
            }

            System.out.println("✅ Fichier téléchargé et reconstruit : " + fichierSortie);

        } catch (IOException e) {
            System.err.println("Erreur lors du téléchargement : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
