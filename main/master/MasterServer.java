package main.master;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.io.FileWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.io.DataOutputStream;
import java.io.File;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import main.json.ObjetJson;
import main.json.InfoMorceau;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import main.json.ListObjetJson;
public class MasterServer {
    private ServerSocket serverSocket;
    private List<InetSocketAddress> slaveAddresses;
    private List<Socket> slaveSockets;

    public MasterServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.slaveAddresses = new ArrayList<>();
        this.slaveSockets = new ArrayList<>();
    }

    
    public void chargerAdressesSlaves() throws IOException {
        slaveAddresses.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader("listeslaves.txt"))) {
            String ligne;
            while ((ligne = reader.readLine()) != null) {
                String[] parts = ligne.split(";;");
                if (parts.length == 2) {
                    slaveAddresses.add(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
                }
            }
        }
    }

 
    public void connecterAuxSlaves() {
        slaveSockets.clear();
        for (InetSocketAddress addr : slaveAddresses) {
            try {
                Socket s = new Socket();
                s.connect(addr, 2000); // Timeout de 2 secondes
                slaveSockets.add(s);
            } catch (IOException e) {
                System.err.println("Impossible de contacter le slave : " + addr);
            }
        }
    }

 
    public void ecoute() {
        System.out.println("Master en attente sur le port " + serverSocket.getLocalPort() + "...");
        while (true) {
            try (Socket clientSocket = serverSocket.accept();
                 DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                String nomFichier = in.readUTF();
                long tailleTotale = in.readLong();

                System.out.println("Réception de : " + nomFichier + " (" + tailleTotale + " octets)");
                
                diffuserEnStreaming(nomFichier, tailleTotale, in);
                
                out.writeUTF("SUCCESS");

            } catch (IOException e) {
                System.err.println("Erreur client : " + e.getMessage());
            }
        }
    }


private void diffuserEnStreaming(String nom, long tailleTotale, DataInputStream clientIn) throws IOException {
    chargerAdressesSlaves();
    connecterAuxSlaves();

    int nbSlaves = slaveSockets.size();
    if (nbSlaves == 0) throw new IOException("Aucun esclave actif.");

    ObjetJson metadata = new ObjetJson(nom, tailleTotale);
    long tailleParDefaut = tailleTotale / nbSlaves;
    long octetsLusTotal = 0;
    byte[] buffer = new byte[8192];

    for (int i = 0; i < nbSlaves; i++) {
        Socket slave = slaveSockets.get(i);
        long tailleCeChunk = (i == nbSlaves - 1) ? (tailleTotale - octetsLusTotal) : tailleParDefaut;
        
        try {
            DataOutputStream slaveOut = new DataOutputStream(slave.getOutputStream());
            slaveOut.writeUTF(nom + ".part" + i);
            slaveOut.writeLong(tailleCeChunk);

            long octetsEnvoyesCeChunk = 0;
            while (octetsEnvoyesCeChunk < tailleCeChunk) {
                int aLire = (int) Math.min(buffer.length, tailleCeChunk - octetsEnvoyesCeChunk);
                int lus = clientIn.read(buffer, 0, aLire);
                if (lus == -1) break;
                slaveOut.write(buffer, 0, lus);
                octetsEnvoyesCeChunk += lus;
            }
            slaveOut.flush();

            String addrStr = slave.getInetAddress().getHostAddress() + ":" + slave.getPort();
            
           
            metadata.chunks.add(new InfoMorceau(i, addrStr, tailleCeChunk));
            
            octetsLusTotal += octetsEnvoyesCeChunk;
        } catch (IOException e) {
            System.err.println("Erreur avec le slave " + i + ": " + e.getMessage());
        } finally {
            slave.close(); 
        }
    }
    sauvegarderMetadata(metadata);
}
  private void sauvegarderMetadata(ObjetJson nouveauFichier) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    File indexFile = new File("index.json");
    ListObjetJson index;

   
    if (indexFile.exists()) {
        try (FileReader reader = new FileReader(indexFile)) {
            index = gson.fromJson(reader, ListObjetJson.class);
            if (index == null) index = new ListObjetJson();
        } catch (IOException e) {
            index = new ListObjetJson();
        }
    } else {
        index = new ListObjetJson();
    }

  
    index.catalogue.removeIf(f -> f.nom.equals(nouveauFichier.nom));
    index.catalogue.add(nouveauFichier);

 
    try (FileWriter writer = new FileWriter(indexFile)) {
        gson.toJson(index, writer);
        System.out.println("mis à jour : " + nouveauFichier.nom + " ajouté au catalogue.");
    } catch (IOException e) {
        e.printStackTrace();
    }
}

    public static void main(String[] args) {
        try {
            MasterServer master = new MasterServer(5001);
            master.ecoute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
