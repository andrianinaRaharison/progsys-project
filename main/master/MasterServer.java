package main.master;

import java.io.*;
import java.net.*;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import main.json.ObjetJson;
import main.json.InfoMorceau;
import main.json.ListObjetJson;

/**
 * Classe MasterServer
 * Le Master coordonne le système :
 * - UPLOAD : reçoit un fichier du client, le découpe et l’envoie aux Slaves.
 * - DOWNLOAD : recompose un fichier en récupérant les morceaux depuis les Slaves et le renvoie au client.
 */
public class MasterServer {
    private ServerSocket serverSocket;              // Socket d’écoute du Master
    private List<InetSocketAddress> slaveAddresses; // Liste des adresses des Slaves
    private List<Socket> slaveSockets;              // Connexions actives aux Slaves

    /**
     * Constructeur du MasterServer
     * @param port Port sur lequel le Master écoute
     */
    public MasterServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.slaveAddresses = new ArrayList<>();
        this.slaveSockets = new ArrayList<>();
    }

    /**
     * Charge les adresses des Slaves depuis le fichier listeslaves.txt
     * Format attendu : "IP;;PORT" par ligne
     */
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

    /**
     * Établit les connexions avec les Slaves actifs
     */
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

    /**
     * Boucle principale d’écoute du Master
     * - Attend une connexion client
     * - Lit la commande (UPLOAD ou DOWNLOAD)
     * - Exécute l’action correspondante
     */
    public void ecoute() {
        System.out.println("Master en attente sur le port " + serverSocket.getLocalPort() + "...");
        while (true) {
            try (Socket clientSocket = serverSocket.accept();
                 DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                String commande = in.readUTF();

                if (commande.equals("UPLOAD")) {
                    String nomFichier = in.readUTF();
                    long tailleTotale = in.readLong();
                    System.out.println("Réception de : " + nomFichier + " (" + tailleTotale + " octets)");
                    diffuserEnStreaming(nomFichier, tailleTotale, in);
                    out.writeUTF("SUCCESS");

                } else if (commande.equals("DOWNLOAD")) {
                    String nomFichier = in.readUTF();
                    envoyerAuClient(nomFichier, out);
                }

            } catch (IOException e) {
                System.err.println("Erreur client : " + e.getMessage());
            }
        }
    }

    /**
     * UPLOAD : découpe le fichier reçu et l’envoie aux Slaves
     */
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
                slaveOut.writeUTF("UPLOAD"); // commande
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

    /**
     * DOWNLOAD : recompose le fichier en récupérant les morceaux depuis les Slaves
     */
    private void envoyerAuClient(String nomFichier, DataOutputStream clientOut) throws IOException {
        Gson gson = new Gson();
        File indexFile = new File("index.json");
        if (!indexFile.exists()) {
            clientOut.writeUTF("NOT_FOUND");
            return;
        }

        ListObjetJson index;
        try (FileReader reader = new FileReader(indexFile)) {
            index = gson.fromJson(reader, ListObjetJson.class);
        }

        ObjetJson fichier = index.catalogue.stream()
                .filter(f -> f.nom.equals(nomFichier))
                .findFirst()
                .orElse(null);

        if (fichier == null) {
            clientOut.writeUTF("NOT_FOUND");
            return;
        }

        clientOut.writeUTF("FOUND");
        clientOut.writeLong(fichier.tailleTotale);

        byte[] buffer = new byte[8192];

        for (InfoMorceau chunk : fichier.chunks) {
            String[] parts = chunk.slaveAddress.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            try (Socket slaveSocket = new Socket(ip, port);
                 DataOutputStream slaveOut = new DataOutputStream(slaveSocket.getOutputStream());
                 DataInputStream slaveIn = new DataInputStream(slaveSocket.getInputStream())) {

                // Demande au Slave d’envoyer le morceau
                slaveOut.writeUTF("DOWNLOAD");
                slaveOut.writeUTF(nomFichier + ".part" + chunk.ordre);

                long restant = chunk.tailleChunk;
                while (restant > 0) {
                    int lus = slaveIn.read(buffer, 0, (int) Math.min(buffer.length, restant));
                    if (lus == -1) break;
                    clientOut.write(buffer, 0, lus);
                    restant -= lus;
                }
            }
        }

    }

    /**
     * Sauvegarde les métadonnées du fichier dans index.json
     */
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
            System.out.println("Catalogue mis à jour : " + nouveauFichier.nom);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Point d’entrée du Master
     */
    public static void main(String[] args) {
        try {
            MasterServer master = new MasterServer(5001);
            master.ecoute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
