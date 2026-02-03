package main.json;

public class InfoMorceau {
    // Public pour que GSON et le Master puissent lire/écrire
    public int ordre;
    public String slaveAddress;
    public long tailleChunk; // Changé en long pour correspondre au Master

    public InfoMorceau(int ordre, String slaveAddress, long tailleChunk) {
        this.ordre = ordre;
        this.slaveAddress = slaveAddress;
        this.tailleChunk = tailleChunk;
    }

    // GSON a souvent besoin d'un constructeur vide
    public InfoMorceau() {}
}