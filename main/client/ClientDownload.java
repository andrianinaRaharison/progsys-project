package main.client;

import java.io.*;
import java.net.Socket;

public class ClientDownload {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 5001);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());

        // Demande de téléchargement
        out.writeUTF("DOWNLOAD");
        out.writeUTF("testenvoi.txt");

        String status = in.readUTF();
        if (status.equals("NOT_FOUND")) {
            System.out.println("Fichier introuvable !");
        } else if (status.equals("FOUND")) {
            long taille = in.readLong();
            try (FileOutputStream fileOut = new FileOutputStream("recu_testenvoi.txt")) {
                byte[] buffer = new byte[8192];
                long restant = taille;
                while (restant > 0) {
                    int lus = in.read(buffer, 0, (int)Math.min(buffer.length, restant));
                    if (lus == -1) break;
                    fileOut.write(buffer, 0, lus);
                    restant -= lus;
                }
            }
            System.out.println("Fichier téléchargé et reconstruit : recu_testenvoi.txt");
        }

        socket.close();
    }
}
