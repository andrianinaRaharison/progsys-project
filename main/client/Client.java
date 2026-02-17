package main.client;

import java.io.IOException;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.File;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {
    public static void main(String[] args) throws UnknownHostException, IOException {
        // Connexion au Master
        Socket socket = new Socket("localhost", 5001);

        // Fichier à envoyer
        File file = new File("donnee.txt");
        long taille = file.length();
        InputStream in = new FileInputStream(file);

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        // --- IMPORTANT : envoyer la commande UPLOAD ---
        out.writeUTF("UPLOAD");          // commande
        out.writeUTF(file.getName());    // nom du fichier
        out.writeLong(taille);           // taille du fichier

        // Envoi du contenu du fichier
        byte[] buffer = new byte[4096];
        int bytesLus;
        while ((bytesLus = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesLus);
        }

        in.close();
        out.close();
        socket.close();

        System.out.println(" Fichier " + file.getName() + " envoyé au Master.");
    }
}
