package evacuation.graph;

public class Sortie extends Node {

    private int capacite;
    private boolean estOuverte;

    public Sortie(String id, int capacite) {
        super(id);
        this.capacite = capacite;
        this.estOuverte = true;
    }

    public Sortie(String id, double x, double y, int capacite) {
        super(id, x, y);
        this.capacite = capacite;
        this.estOuverte = true;
    }

    public void ouvrir()  { this.estOuverte = true;  System.out.println("[Sortie] " + getId() + " est ouverte."); }
    public void fermer()  { this.estOuverte = false; System.out.println("[Sortie] " + getId() + " est fermée."); }

    public int     getCapacite()   { return capacite; }
    public boolean isEstOuverte()  { return estOuverte; }

    @Override
    public String toString() {
        return "Sortie{id='" + getId() + "', capacite=" + capacite + ", ouverte=" + estOuverte + "}";
    }
}
