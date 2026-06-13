package evacuation.routing;

import evacuation.graph.Graph;
import evacuation.graph.Node;

import java.io.Serializable;
import java.util.*;

/**
 * Pathfinder implementation using Dijkstra's algorithm.
 * Computes shortest weighted paths on the evacuation graph.
 */
public class DijkstraPathFinder implements PathFinder, Serializable {

    private static final long serialVersionUID = 1L;

    private Graph graph;
    private Map<String, Double> distances;
    private List<Node> lastPath;

    /**
     * Constructs a DijkstraPathFinder for the given graph.
     *
     * @param graph the evacuation graph to search
     */
    public DijkstraPathFinder(Graph graph) {
        this.graph    = graph;
        this.distances = new HashMap<>();
        this.lastPath  = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     * Uses Dijkstra's algorithm with edge weights that account for speed modifiers.
     */
    @Override
    public List<Node> findPath(Node src, Node dest) {
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();

        for (Node n : graph.getNodes()) {
            dist.put(n.getId(), Double.POSITIVE_INFINITY);
        }
        dist.put(src.getId(), 0.0);

        PriorityQueue<Node> queue = new PriorityQueue<>(
            Comparator.comparingDouble(n -> dist.getOrDefault(n.getId(), Double.POSITIVE_INFINITY))
        );
        queue.add(src);

        while (!queue.isEmpty()) {
            Node u = queue.poll();
            if (visited.contains(u.getId())) continue;
            visited.add(u.getId());
            if (u.equals(dest)) break;

            for (Node v : graph.getNeighbors(u)) {
                if (visited.contains(v.getId())) continue;
                Double distU = dist.get(u.getId());
                if (distU == null) continue;
                double alt = distU + graph.getWeight(u, v);
                if (alt < dist.getOrDefault(v.getId(), Double.POSITIVE_INFINITY)) {
                    dist.put(v.getId(), alt);
                    prev.put(v.getId(), u.getId());
                    queue.add(v);
                }
            }
        }

        lastPath  = reconstructPath(prev, src, dest);
        distances = dist;
        return lastPath;
    }

    private List<Node> reconstructPath(Map<String, String> prev, Node src, Node dest) {
        LinkedList<Node> path = new LinkedList<>();
        String current = dest.getId();
        while (current != null) {
            Node n = graph.getNode(current);
            if (n == null) return new ArrayList<>();
            path.addFirst(n);
            current = prev.get(current);
        }
        if (path.isEmpty() || !path.getFirst().equals(src)) {
            return new ArrayList<>();
        }
        return path;
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
        distances.clear();
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getLastPath() {
        return lastPath;
    }

    /**
     * Returns the distance map from the last computed path.
     *
     * @return map of node ID to distance from last source
     */
    public Map<String, Double> getDistances() {
        return distances;
    }
}
