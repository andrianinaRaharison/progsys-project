package main.json;

import java.util.List;
import java.util.ArrayList;

public class ObjetJson {
    public String nom;
    public long tailleTotale;
    public List<InfoMorceau> chunks = new ArrayList<>();

    public ObjetJson(String nom, long tailleTotale) {
        this.nom = nom;
        this.tailleTotale = tailleTotale;
    }

    public ObjetJson() {}
}