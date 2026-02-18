package main.json;

import java.util.ArrayList;
import java.util.List;

public class InfoMorceau {

    public int ordre;
    public long taille;

    // LISTE DES SLAVES (réplication)
    public List<String> slaveAddresses = new ArrayList<>();

    public InfoMorceau(int ordre, long taille) {
        this.ordre = ordre;
        this.taille = taille;
    }
}
