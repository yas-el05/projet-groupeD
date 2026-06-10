package evacuation.graph;

import java.util.ArrayList;
import java.util.List;

public class Graph {

    private List<Node> nodes;
    private List<Edge> edges;

    public Graph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public void ajouterNode(Node n) { nodes.add(n); }

    public void supprimerNode(Node n) {
        edges.removeIf(a -> a.getSource().equals(n) || a.getDestination().equals(n));
        nodes.remove(n);
        System.out.println("[Graph] Nœud supprimé : " + n.getId());
    }

    public Node getNode(String id) {
        return nodes.stream().filter(n -> n.getId().equals(id)).findFirst().orElse(null);
    }

    public void ajouterEdge(Edge a) { edges.add(a); }

    public void supprimerEdge(Edge a) {
        edges.remove(a);
        System.out.println("[Graph] Arête supprimée : " + a.getSource().getId() + " → " + a.getDestination().getId());
    }

    public List<Edge> getEdgesDuNoeud(Node n) {
        List<Edge> result = new ArrayList<>();
        for (Edge e : edges)
            if (e.getSource().equals(n) || e.getDestination().equals(n)) result.add(e);
        return result;
    }

    /** Voisins sans filtre bloqué (pour la propagation des dangers). */
    public List<Node> getVoisinsRaw(Node n) {
        List<Node> voisins = new ArrayList<>();
        for (Edge a : edges) {
            if (a.getSource().equals(n))      voisins.add(a.getDestination());
            else if (a.getDestination().equals(n)) voisins.add(a.getSource());
        }
        return voisins;
    }

    public List<Node> getVoisins(Node n) {
        List<Node> voisins = new ArrayList<>();
        for (Edge a : edges) {
            if (!a.isDisponible()) continue;
            if (a.getSource().equals(n) && !a.getDestination().isBloque())
                voisins.add(a.getDestination());
            else if (a.getDestination().equals(n) && !a.getSource().isBloque())
                voisins.add(a.getSource());
        }
        return voisins;
    }

    public double getPoids(Node u, Node v) {
        for (Edge a : edges) {
            if ((a.getSource().equals(u) && a.getDestination().equals(v))
             || (a.getSource().equals(v) && a.getDestination().equals(u)))
                return a.getPoids();
        }
        return Double.POSITIVE_INFINITY;
    }

    public boolean areteExiste(Node a, Node b) {
        for (Edge e : edges) {
            if ((e.getSource().equals(a) && e.getDestination().equals(b))
             || (e.getSource().equals(b) && e.getDestination().equals(a))) return true;
        }
        return false;
    }

    /** Vérifie si un nœud est trop proche d'un autre (rayon en unités graphe). */
    public boolean tropProche(double x, double y, double rayon) {
        for (Node n : nodes) {
            if (Math.hypot(n.getX() - x, n.getY() - y) < rayon) return true;
        }
        return false;
    }

    public Node getNoeudLePlusProche(Node reference, Node exclu) {
        Node closest = null;
        double minDist = Double.MAX_VALUE;
        for (Node n : nodes) {
            if (n.equals(exclu) || n.isBloque()) continue;
            double d = Math.hypot(n.getX() - reference.getX(), n.getY() - reference.getY());
            if (d < minDist) { minDist = d; closest = n; }
        }
        return closest;
    }

    public String genererIdUnique(String prefixe) {
        int i = 1;
        while (getNode(prefixe + i) != null) i++;
        return prefixe + i;
    }

    public List<Node> getNodes() { return nodes; }
    public List<Edge> getEdges() { return edges; }
}
