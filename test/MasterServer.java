package test;
import java.util.*;
import java.net.*;
import java.io.*;

public class MasterServer {
    // Map: Nom du fichier -> Liste des adresses des Slaves (Réplication)
    private static Map<String, List<String>> fileIndex = new HashMap<>();
    
    // Liste des Slaves disponibles (à adapter selon tes adresses IP)
    private static List<String> allSlaves = Arrays.asList("127.0.0.1:5001", "127.0.0.1:5002"); 

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(9000);
        System.out.println("Master (Replication Mode) prêt sur le port 9000...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private static void handleClient(Socket client) {
        try (DataInputStream in = new DataInputStream(client.getInputStream());
             DataOutputStream out = new DataOutputStream(client.getOutputStream())) {
            
            String command = in.readUTF(); 
            String fileName = in.readUTF();

            if (command.equals("UPLOAD")) {
                // Stratégie : On envoie le fichier à TOUS les slaves connus (Réplication Totale)
                fileIndex.put(fileName, new ArrayList<>(allSlaves));
                
                // On envoie le nombre de slaves et leurs adresses au client
                out.writeInt(allSlaves.size());
                for (String slaveAddr : allSlaves) {
                    out.writeUTF(slaveAddr);
                }
            } else if (command.equals("DOWNLOAD")) {
                List<String> locations = fileIndex.get(fileName);
                if (locations != null && !locations.isEmpty()) {
                    out.writeUTF(locations.get(0)); // On donne le premier disponible
                } else {
                    out.writeUTF("NOT_FOUND");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}