package evacuation.graph;

public class Edge {

    private Node source;
    private Node destination;
    private double poids;
    private boolean disponible;
    /** Nombre d'agents actuellement en transit sur cette arête. */
    private int agentsEnTransit;

    public Edge(Node source, Node destination, double poids) {
        this.source        = source;
        this.destination   = destination;
        this.poids         = poids;
        this.disponible    = true;
        this.agentsEnTransit = 0;
    }

    public double getPoids() {
        return disponible ? poids : Double.POSITIVE_INFINITY;
    }

    public void bloquer()   { this.disponible = false; }
    public void debloquer() { this.disponible = true;  }

    public void ajouterAgent()    { agentsEnTransit = Math.max(0, agentsEnTransit + 1); }
    public void retirerAgent()    { agentsEnTransit = Math.max(0, agentsEnTransit - 1); }
    public int  getAgentsEnTransit() { return agentsEnTransit; }

    public Node    getSource()      { return source; }
    public Node    getDestination() { return destination; }
    public boolean isDisponible()   { return disponible; }
    public double  getPoidsBase()   { return poids; }

    @Override
    public String toString() {
        return "Edge{" + source.getId() + " → " + destination.getId()
             + ", poids=" + poids + ", dispo=" + disponible + "}";
    }
}
