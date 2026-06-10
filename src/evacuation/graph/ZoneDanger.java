package evacuation.graph;

import java.util.List;

public class ZoneDanger extends Node {

    public enum TypeDanger {
        FEU,           // se propage aux voisins à chaque tick
        INONDATION,    // rend les noeuds voisins indisponibles progressivement
        PERSONNE_DANGEREUSE  // noeud accessible mais ralentit les agents
    }

    private TypeDanger typeDanger;
    private int niveauRisque;
    private int ticksPropagation; // compteur pour cadence de propagation

    public ZoneDanger(String id, TypeDanger type, int niveauRisque) {
        super(id);
        this.typeDanger = type;
        this.niveauRisque = niveauRisque;
        this.ticksPropagation = 0;
        if (type != TypeDanger.PERSONNE_DANGEREUSE) bloquer();
    }

    public ZoneDanger(String id, double x, double y, TypeDanger type, int niveauRisque) {
        super(id, x, y);
        this.typeDanger = type;
        this.niveauRisque = niveauRisque;
        this.ticksPropagation = 0;
        if (type != TypeDanger.PERSONNE_DANGEREUSE) bloquer();
    }

    // Constructeur legacy pour compatibilité avec les String
    public ZoneDanger(String id, double x, double y, String typeStr, int niveauRisque) {
        this(id, x, y, parseType(typeStr), niveauRisque);
    }

    private static TypeDanger parseType(String s) {
        if (s == null) return TypeDanger.FEU;
        switch (s.toUpperCase()) {
            case "INONDATION": return TypeDanger.INONDATION;
            case "PERSONNE_DANGEREUSE": return TypeDanger.PERSONNE_DANGEREUSE;
            default: return TypeDanger.FEU;
        }
    }

    /**
     * Applique l'effet du danger sur le graphe à chaque tick.
     * @param graph le graphe sur lequel agir
     */
    public void propager(Graph graph) {
        ticksPropagation++;
        // Propagation toutes les 3 ticks
        if (ticksPropagation % 3 != 0) return;

        List<Node> voisins = graph.getVoisinsRaw(this); // voisins sans filtre bloqué
        switch (typeDanger) {
            case FEU -> {
                // Le feu se propage : convertit les voisins en ZoneDanger FEU
                for (Node v : voisins) {
                    if (!(v instanceof ZoneDanger) && !(v instanceof Sortie)) {
                        // Marque comme dangereux (bloqué)
                        v.bloquer();
                        System.out.println("[FEU] Le feu s'est propagé à " + v.getId());
                    }
                }
            }
            case INONDATION -> {
                // L'inondation bloque les arêtes adjacentes progressivement
                for (evacuation.graph.Edge e : graph.getEdgesDuNoeud(this)) {
                    if (e.isDisponible()) {
                        e.bloquer();
                        System.out.println("[INONDATION] Arête bloquée : " + e.getSource().getId() + " → " + e.getDestination().getId());
                        break; // une arête par tick
                    }
                }
            }
            case PERSONNE_DANGEREUSE -> {
                // Ralentit les agents dans les noeuds voisins (effet géré dans Agent)
                System.out.println("[DANGER] Personne dangereuse en " + getId() + " — agents voisins ralentis");
            }
        }
    }

    public void signaler() {
        System.out.println("[ZoneDanger] ALERTE : " + getId()
            + " — type=" + typeDanger + ", risque=" + niveauRisque + "/10");
    }

    /** Pour PERSONNE_DANGEREUSE : facteur de ralentissement appliqué aux agents voisins */
    public double getFacteurRalentissement() {
        return typeDanger == TypeDanger.PERSONNE_DANGEREUSE ? 0.5 : 1.0;
    }

    public int         getNiveauRisque() { return niveauRisque; }
    public TypeDanger  getTypeDanger()   { return typeDanger; }
    public String      getTypeDangerStr() { return typeDanger.name(); }

    @Override
    public String toString() {
        return "ZoneDanger{id='" + getId() + "', type='" + typeDanger + "', risque=" + niveauRisque + "}";
    }
}
