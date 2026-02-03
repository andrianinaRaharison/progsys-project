package main.client;
import java.io.IOException;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.File;
import java.net.Socket;
import java.net.UnknownHostException;
public class Client {
    public static void main(String[] args) throws UnknownHostException,IOException{
        Socket socket = new Socket("localhost",5001);
        File file = new File("testenvoi.txt");
        long taille = file.length();
        InputStream in = new FileInputStream(file);
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        byte[] buffer = new byte[4096];
        int bytesLus;
        out.writeUTF(file.getName());
        out.writeLong(taille);
        while( (bytesLus = in.read(buffer)) != -1 ){
            out.write(buffer,0,bytesLus);
        }
        in.close();
        out.close();
        socket.close();
    }
}
