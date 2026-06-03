package evacuation.routing;

import evacuation.graph.Graph;
import evacuation.graph.Node;

import java.util.*;

public class DijkstraPathFinder implements PathFinder {

    private Graph graph;
    private Map<String, Double> distances; // id nœud → distance depuis source
    private List<Node> dernierChemin;

    public DijkstraPathFinder(Graph graph) {
        this.graph = graph;
        this.distances = new HashMap<>();
        this.dernierChemin = new ArrayList<>();
    }

    @Override
    public List<Node> calculerChemin(Node src, Node dest) {

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();  // id → id prédécesseur
        Set<String> visited      = new HashSet<>();

        // Initialisation
        for (Node n : graph.getNodes()) {
            dist.put(n.getId(), Double.POSITIVE_INFINITY);
        }
        dist.put(src.getId(), 0.0);

        // File de priorité : (distance, nœud)
        PriorityQueue<Node> queue = new PriorityQueue<>(
            Comparator.comparingDouble(n -> dist.getOrDefault(n.getId(), Double.POSITIVE_INFINITY))
        );
        queue.add(src);

        while (!queue.isEmpty()) {
            Node u = queue.poll();
            if (visited.contains(u.getId())) continue;
            visited.add(u.getId());

            if (u.equals(dest)) break;

            for (Node v : graph.getVoisins(u)) {
                if (visited.contains(v.getId())) continue;
                double alt = dist.get(u.getId()) + graph.getPoids(u, v);
                if (alt < dist.getOrDefault(v.getId(), Double.POSITIVE_INFINITY)) {
                    dist.put(v.getId(), alt);
                    prev.put(v.getId(), u.getId());
                    queue.add(v);
                }
            }
        }

        // Reconstruction du chemin
        dernierChemin = reconstruireChemin(prev, src, dest);
        distances = dist;
        return dernierChemin;
    }

    private List<Node> reconstruireChemin(Map<String, String> prev, Node src, Node dest) {
        LinkedList<Node> chemin = new LinkedList<>();
        String current = dest.getId();
        while (current != null) {
            Node n = graph.getNode(current);
            if (n == null) return new ArrayList<>();
            chemin.addFirst(n);
            current = prev.get(current);
        }
        // Vérifie que le chemin commence bien à src
        if (chemin.isEmpty() || !chemin.getFirst().equals(src)) {
            return new ArrayList<>();
        }
        return chemin;
    }

    @Override
    public void recalculer() {
        // Réinitialise les distances mémorisées
        distances.clear();
    }

    @Override
    public List<Node> getPlusCourt() {
        return dernierChemin;
    }

    public Map<String, Double> getDistances() {
        return distances;
    }
}
