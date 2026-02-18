package main.client;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {
    public static void main(String[] args) throws IOException {

        Socket socket = new Socket("localhost", 5001);

        File file = new File("donnee.txt");
        FileInputStream fileIn = new FileInputStream(file);

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());

        out.writeUTF("UPLOAD");
        out.writeUTF(file.getName());
        out.writeLong(file.length());

        byte[] buffer = new byte[4096];
        int read;
        while ((read = fileIn.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();

        String response = in.readUTF();
        System.out.println("Réponse Master : " + response);

        fileIn.close();
        socket.close();
    }
}
