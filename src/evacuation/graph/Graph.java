package evacuation.graph;
import java.util.ArrayList;
import java.util.List;

public class Graph {

    private List<Node> Nodes;
    private List<Edge> Edges;

    public Graph() {
        this.Nodes = new ArrayList<>();
        this.Edges = new ArrayList<>();
    }

    // Gestion des noeuds 
    public void ajouterNode(Node n) {
        Nodes.add(n);
    }

    //Supprime un nœud et toutes les arêtes qui lui sont connectées
    public void supprimerNode(Node n) {
        Edges.removeIf(a -> a.getSource().equals(n) || a.getDestination().equals(n));
        Nodes.remove(n);
        System.out.println("[Graph] Nœud supprimé : " + n.getNom());
    }

    public Node getNode(String id) {
        return Nodes.stream()
            .filter(n -> n.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    // Gestion des aretes
    public void ajouterEdge(Edge a) {
        Edges.add(a);
    }

    public void supprimerEdge(Edge a) {
        Edges.remove(a);
    }

    // Voisins d'un noeud 
    public List<Node> getVoisins(Node n) {
        List<Node> voisins = new ArrayList<>();
        for (Edge a : Edges) {
            if (!a.isDisponible()) continue;
            if (a.getSource().equals(n) && !a.getDestination().isBloque()) {
                voisins.add(a.getDestination());
            } else if (a.getDestination().equals(n) && !a.getSource().isBloque()) {
                voisins.add(a.getSource()); // Graph non orienté
            }
        }
        return voisins;
    }

    // Retourne le poids de l'arête entre deux nœuds, ou +∞ si inexistante
    public double getPoids(Node u, Node v) {
        for (Edge a : Edges) {
            if ((a.getSource().equals(u) && a.getDestination().equals(v))
             || (a.getSource().equals(v) && a.getDestination().equals(u))) {
                return a.getPoids();
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    public List<Node> getNodes(){
        return Nodes;
    }
    public List<Edge> getEdges(){
        return Edges;
    }
}
