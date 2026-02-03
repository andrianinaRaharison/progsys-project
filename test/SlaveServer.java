package test;
import java.io.*;
import java.net.*;

public class SlaveServer {
    public static void main(String[] args) throws IOException {
        int port = 5000; // Port du slave
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Slave prêt sur le port " + port);

        while (true) {
            try (Socket socket = serverSocket.accept();
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {
                
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();
                
                try (FileOutputStream fos = new FileOutputStream("storage/" + fileName)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while (fileSize > 0 && (read = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize))) != -1) {
                        fos.write(buffer, 0, read);
                        fileSize -= read;
                    }
                }
                System.out.println("Fichier " + fileName + " reçu et stocké.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
