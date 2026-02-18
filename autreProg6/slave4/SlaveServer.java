package slave4;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetAddress;
public class SlaveServer {

    private ServerSocket serverSocket;
    private InetAddress masterIP;
    private Path baseDir;

    public SlaveServer(int port, Path baseDir) throws IOException {
        this.baseDir = baseDir;
        Files.createDirectories(baseDir);
        this.serverSocket = new ServerSocket(port);
        loadMasterIP();
    }

    private void loadMasterIP() throws IOException {
        try (BufferedReader reader =
                     new BufferedReader(new FileReader("masteraddress.txt"))) {
            String[] parts = reader.readLine().split(";;");
            masterIP = InetAddress.getByName(parts[0]);
        }
    }

    public void ecoute() {
        try (Socket socket = serverSocket.accept()) {

            if (!socket.getInetAddress().equals(masterIP)) {
                socket.close();
                return;
            }

            DataInputStream in = new DataInputStream(socket.getInputStream());

            String nomFichier = in.readUTF();
            long taille = in.readLong();

            Path fichier = baseDir.resolve(nomFichier);

            try (OutputStream out = Files.newOutputStream(fichier)) {
                byte[] buffer = new byte[4096];
                long total = 0;
                int read;

                while (total < taille &&
                        (read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    total += read;
                }
            }

            System.out.println("Fichier reçu : " + fichier);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void close() throws IOException{
            serverSocket.close();
    }
    public static void main(String[] args) throws IOException{
              int port = (args.length > 0) ? Integer.parseInt(args[0]) : 5001;
        Path directory = (args.length > 1) ? Paths.get(args[1]) : Paths.get("storage");

        SlaveServer slave = new SlaveServer(port, directory);
            slave.ecoute();
            slave.close();
    }
}

