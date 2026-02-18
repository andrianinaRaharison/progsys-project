package main.client;

import java.io.*;
import java.net.Socket;

public class Client {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage : java Client <nom_fichier_a_uploader>");
            return;
        }

        String nomFichier = args[0];
        File file = new File(nomFichier);

        if (!file.exists() || !file.isFile()) {
            System.out.println("❌ Fichier introuvable : " + nomFichier);
            return;
        }

        try (Socket socket = new Socket("localhost", 5001);
             FileInputStream fileIn = new FileInputStream(file);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            System.out.println("Connexion au Master...");

            // Envoi de la commande et des informations du fichier
            out.writeUTF("UPLOAD");
            out.writeUTF(file.getName());
            out.writeLong(file.length());

            // Envoi du contenu du fichier
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();

            // Lecture de la réponse du Master
            String response = in.readUTF();
            System.out.println("Réponse Master : " + response);

        } catch (IOException e) {
            System.err.println("Erreur lors de l'upload : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
