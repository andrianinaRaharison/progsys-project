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
 * Master avec réplication
 * Chaque chunk est stocké sur plusieurs slaves
 */
public class MasterServer {

    private ServerSocket serverSocket;
    private List<InetSocketAddress> slaveAddresses;

    // FACTEUR DE RÉPLICATION
    private static final int FACTEUR_REPLICATION = 4;

    public MasterServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.slaveAddresses = new ArrayList<>();
        chargerAdressesSlaves();
    }

    private void chargerAdressesSlaves() throws IOException {
        slaveAddresses.clear();

        try (BufferedReader reader =
                     new BufferedReader(new FileReader("listeslaves.txt"))) {

            String ligne;
            while ((ligne = reader.readLine()) != null) {

                String[] parts = ligne.split(";;");
                if (parts.length == 2) {
                    slaveAddresses.add(
                            new InetSocketAddress(parts[0],
                                    Integer.parseInt(parts[1])));
                }
            }
        }
    }

    public void ecoute() {
        System.out.println("Master démarré sur port "
                + serverSocket.getLocalPort());

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> traiterClient(clientSocket)).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void traiterClient(Socket clientSocket) {

        try (DataInputStream in =
                     new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out =
                     new DataOutputStream(clientSocket.getOutputStream())) {

            String commande = in.readUTF();

            if ("UPLOAD".equals(commande)) {
                String nom = in.readUTF();
                long taille = in.readLong();

                diffuserAvecReplication(nom, taille, in);

                out.writeUTF("SUCCESS");
                out.flush();
            }

            else if ("DOWNLOAD".equals(commande)) {
                String nom = in.readUTF();
                envoyerAuClient(nom, out);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Upload avec réplication
     */
    private void diffuserAvecReplication(String nom,
                                         long tailleTotale,
                                         DataInputStream clientIn)
            throws IOException {

        int nbSlaves = slaveAddresses.size();

        if (nbSlaves < FACTEUR_REPLICATION) {
            throw new IOException("Pas assez de slaves pour réplication.");
        }

        ObjetJson metadata = new ObjetJson(nom, tailleTotale);

        long tailleParChunk = tailleTotale / nbSlaves;
        long totalLu = 0;
        byte[] buffer = new byte[8192];

        for (int i = 0; i < nbSlaves; i++) {

            long tailleChunk = (i == nbSlaves - 1)
                    ? (tailleTotale - totalLu)
                    : tailleParChunk;

            // Lire chunk en mémoire
            ByteArrayOutputStream chunkBuffer =
                    new ByteArrayOutputStream();

            long lu = 0;
            while (lu < tailleChunk) {

                int aLire = (int)Math.min(buffer.length,
                        tailleChunk - lu);

                int lus = clientIn.read(buffer, 0, aLire);

                if (lus == -1)
                    throw new IOException("Flux client interrompu");

                chunkBuffer.write(buffer, 0, lus);
                lu += lus;
            }

            byte[] chunkData = chunkBuffer.toByteArray();

            InfoMorceau info =
                    new InfoMorceau(i, tailleChunk);

            // Envoi à plusieurs slaves
            for (int r = 0; r < FACTEUR_REPLICATION; r++) {

                int indexSlave = (i + r) % nbSlaves;
                InetSocketAddress addr =
                        slaveAddresses.get(indexSlave);

                try (Socket slave =
                             new Socket(addr.getHostName(),
                                     addr.getPort());
                     DataOutputStream out =
                             new DataOutputStream(
                                     slave.getOutputStream());
                     DataInputStream in =
                             new DataInputStream(
                                     slave.getInputStream())) {

                    out.writeUTF("UPLOAD");
                    out.writeUTF(nom + ".part" + i);
                    out.writeLong(tailleChunk);
                    out.write(chunkData);
                    out.flush();

                    String rep = in.readUTF();
                    if (!"SUCCESS".equals(rep))
                        throw new IOException("Échec slave");

                    info.slaveAddresses.add(
                            addr.getHostName() + ":"
                                    + addr.getPort());

                } catch (Exception e) {
                    System.err.println(
                            "Erreur réplication : "
                                    + e.getMessage());
                }
            }

            if (info.slaveAddresses.isEmpty())
                throw new IOException("Chunk perdu");

            metadata.chunks.add(info);
            totalLu += tailleChunk;
        }

        sauvegarderMetadata(metadata);
    }

    /**
     * Download tolérant aux pannes
     */
    private void envoyerAuClient(String nom,
                                 DataOutputStream clientOut)
            throws IOException {

        Gson gson = new Gson();
        File indexFile = new File("index.json");

        if (!indexFile.exists()) {
            clientOut.writeUTF("NOT_FOUND");
            return;
        }

        ListObjetJson index;

        try (FileReader reader = new FileReader(indexFile)) {
            index = gson.fromJson(reader,
                    ListObjetJson.class);
        }

        ObjetJson fichier = index.catalogue.stream()
                .filter(f -> f.nom.equals(nom))
                .findFirst().orElse(null);

        if (fichier == null) {
            clientOut.writeUTF("NOT_FOUND");
            return;
        }

        clientOut.writeUTF("FOUND");
        clientOut.writeLong(fichier.tailleTotale);
        clientOut.flush();

        byte[] buffer = new byte[8192];

        for (InfoMorceau chunk : fichier.chunks) {

            boolean recupere = false;

            for (String addrStr : chunk.slaveAddresses) {

                String[] parts = addrStr.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);

                try (Socket slave =
                             new Socket(ip, port);
                     DataOutputStream out =
                             new DataOutputStream(
                                     slave.getOutputStream());
                     DataInputStream in =
                             new DataInputStream(
                                     slave.getInputStream())) {

                    out.writeUTF("DOWNLOAD");
                    out.writeUTF(nom + ".part"
                            + chunk.ordre);
                    out.flush();

                    if (!"FOUND".equals(in.readUTF()))
                        continue;

                    long restant = in.readLong();

                    while (restant > 0) {

                        int lus = in.read(buffer, 0,
                                (int)Math.min(
                                        buffer.length,
                                        restant));

                        clientOut.write(buffer, 0, lus);
                        restant -= lus;
                    }

                    recupere = true;
                    break;

                } catch (Exception ignored) {}
            }

            if (!recupere)
                throw new IOException(
                        "Chunk "
                                + chunk.ordre
                                + " introuvable");
        }
    }

    private synchronized void sauvegarderMetadata(
            ObjetJson fichier) {

        Gson gson =
                new GsonBuilder()
                        .setPrettyPrinting()
                        .create();

        File indexFile = new File("index.json");
        ListObjetJson index = new ListObjetJson();

        if (indexFile.exists()) {
            try (FileReader reader =
                         new FileReader(indexFile)) {

                index = gson.fromJson(reader,
                        ListObjetJson.class);

                if (index == null)
                    index = new ListObjetJson();

            } catch (Exception ignored) {}
        }

        index.catalogue.removeIf(
                f -> f.nom.equals(fichier.nom));

        index.catalogue.add(fichier);

        try (FileWriter writer =
                     new FileWriter(indexFile)) {

            gson.toJson(index, writer);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        try {
            MasterServer master =
                    new MasterServer(5001);
            master.ecoute();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
