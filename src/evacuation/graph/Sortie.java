package evacuation.graph;

public class Sortie extends Node{
    private int capacite;
    private boolean estOuverte;

    public Sortie(String id, String nom, int capacite) {
        super(id, nom);
        this.capacite = capacite;
        this.estOuverte = true;
    }

    public Sortie(String id, String nom, int x, int y, int capacite) {
        super(id, nom, x, y);
        this.capacite = capacite;
        this.estOuverte = true;
    }

    public void ouvrir() {
        this.estOuverte = true;
        System.out.println("[Sortie] " + getNom() + " est ouverte.");
    }

    public void fermer() {
        this.estOuverte = false;
        System.out.println("[Sortie] " + getNom() + " est fermée.");
    }

    public int getCapacite(){
        return capacite;
    }

    public boolean isEstOuverte(){
        return estOuverte;
    }

    @Override
    public String toString() {
        return "Sortie{id='" + getId() + "', nom='" + getNom() + "', capacite=" + capacite + ", ouverte=" + estOuverte + "}";
    }
}
