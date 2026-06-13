package evacuation.graph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents the evacuation graph containing nodes and edges.
 * Provides methods for adding/removing elements, querying neighbors,
 * finding paths, and adding random elements.
 */
public class Graph implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<Node> nodes;
    private List<Edge> edges;

    private static final Random RANDOM = new Random();

    /**
     * Constructs an empty Graph.
     */
    public Graph() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    /**
     * Adds a node to the graph.
     *
     * @param n the node to add
     */
    public void addNode(Node n) { nodes.add(n); }

    /**
     * Removes a node and all its connected edges from the graph.
     *
     * @param n the node to remove
     */
    public void removeNode(Node n) {
        edges.removeIf(e -> e.getSource().equals(n) || e.getDestination().equals(n));
        nodes.remove(n);
        System.out.println("[Graph] Node removed: " + n.getId());
    }

    /**
     * Finds a node by its unique identifier.
     *
     * @param id the node ID to search for
     * @return the node, or null if not found
     */
    public Node getNode(String id) {
        return nodes.stream().filter(n -> n.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * Adds an edge to the graph.
     *
     * @param e the edge to add
     */
    public void addEdge(Edge e) { edges.add(e); }

    /**
     * Removes an edge from the graph.
     *
     * @param e the edge to remove
     */
    public void removeEdge(Edge e) {
        edges.remove(e);
        System.out.println("[Graph] Edge removed: " + e.getSource().getId()
                + " → " + e.getDestination().getId());
    }

    /**
     * Returns all edges connected to the given node (both source and destination).
     *
     * @param n the node
     * @return list of edges connected to n
     */
    public List<Edge> getEdgesOfNode(Node n) {
        List<Edge> result = new ArrayList<>();
        for (Edge e : edges)
            if (e.getSource().equals(n) || e.getDestination().equals(n)) result.add(e);
        return result;
    }

    /**
     * Returns all neighbors of a node, including via blocked edges (raw view).
     * Does not filter blocked nodes or unavailable edges.
     *
     * @param n the node
     * @return list of all directly connected nodes
     */
    public List<Node> getRawNeighbors(Node n) {
        List<Node> neighbors = new ArrayList<>();
        for (Edge e : edges) {
            if (e.getSource().equals(n))           neighbors.add(e.getDestination());
            else if (!e.isDirected() && e.getDestination().equals(n)) neighbors.add(e.getSource());
        }
        return neighbors;
    }

    /**
     * Returns the accessible neighbors of a node.
     * Filters out unavailable edges, blocked nodes, and respects edge direction.
     * For directed edges, only traversal from source→destination is allowed.
     *
     * @param n the node
     * @return list of accessible neighbor nodes
     */
    public List<Node> getNeighbors(Node n) {
        List<Node> neighbors = new ArrayList<>();
        for (Edge e : edges) {
            if (!e.isAvailable()) continue;
            if (e.getSource().equals(n) && !e.getDestination().isBlocked()) {
                neighbors.add(e.getDestination());
            } else if (!e.isDirected() && e.getDestination().equals(n) && !e.getSource().isBlocked()) {
                neighbors.add(e.getSource());
            }
        }
        return neighbors;
    }

    /**
     * Returns the effective weight of the edge between two nodes.
     * Uses the edge's {@link Edge#getWeight()} method which accounts for speed modifier.
     *
     * @param u source node
     * @param v destination node
     * @return effective weight, or positive infinity if no edge exists
     */
    public double getWeight(Node u, Node v) {
        for (Edge e : edges) {
            if (e.getSource().equals(u) && e.getDestination().equals(v))
                return e.getWeight();
            if (!e.isDirected() && e.getSource().equals(v) && e.getDestination().equals(u))
                return e.getWeight();
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * Finds the edge between two nodes (in either direction for undirected edges).
     *
     * @param u first node
     * @param v second node
     * @return the edge, or null if none exists
     */
    public Edge getEdge(Node u, Node v) {
        for (Edge e : edges) {
            if (e.getSource().equals(u) && e.getDestination().equals(v)) return e;
            if (!e.isDirected() && e.getSource().equals(v) && e.getDestination().equals(u)) return e;
        }
        return null;
    }

    /**
     * Checks whether an edge exists between two nodes.
     *
     * @param a first node
     * @param b second node
     * @return true if an edge exists
     */
    public boolean edgeExists(Node a, Node b) {
        for (Edge e : edges) {
            if ((e.getSource().equals(a) && e.getDestination().equals(b))
             || (!e.isDirected() && e.getSource().equals(b) && e.getDestination().equals(a)))
                return true;
        }
        return false;
    }

    /**
     * Checks whether any existing node is within the given radius of (x, y).
     *
     * @param x     x coordinate
     * @param y     y coordinate
     * @param radius minimum distance threshold
     * @return true if a node is too close
     */
    public boolean isTooClose(double x, double y, double radius) {
        for (Node n : nodes) {
            if (Math.hypot(n.getX() - x, n.getY() - y) < radius) return true;
        }
        return false;
    }

    /**
     * Returns the closest non-blocked node to the reference node, excluding the given node.
     *
     * @param reference the reference node
     * @param excluded  the node to exclude (may be null)
     * @return closest node, or null if none available
     */
    public Node getClosestNode(Node reference, Node excluded) {
        Node closest = null;
        double minDist = Double.MAX_VALUE;
        for (Node n : nodes) {
            if (n.equals(excluded) || n.isBlocked()) continue;
            double d = Math.hypot(n.getX() - reference.getX(), n.getY() - reference.getY());
            if (d < minDist) { minDist = d; closest = n; }
        }
        return closest;
    }

    /**
     * Generates a unique node ID with the given prefix.
     *
     * @param prefix the ID prefix (e.g., "N", "S", "Z")
     * @return unique ID string
     */
    public String generateUniqueId(String prefix) {
        int i = 1;
        while (getNode(prefix + i) != null) i++;
        return prefix + i;
    }

    /**
     * Adds a specified number of random nodes with unique IDs and positions.
     * Nodes are placed within [0, maxX] x [0, maxY], avoiding positions
     * that are too close to existing nodes (minimum distance 0.7).
     *
     * @param count the number of nodes to add
     * @param maxX  maximum x coordinate
     * @param maxY  maximum y coordinate
     * @return list of the newly added nodes
     */
    public List<Node> addRandomNodes(int count, double maxX, double maxY) {
        List<Node> added = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = count * 100;
        while (added.size() < count && attempts < maxAttempts) {
            attempts++;
            double x = RANDOM.nextDouble() * maxX;
            double y = RANDOM.nextDouble() * maxY;
            if (isTooClose(x, y, 0.7)) continue;
            String id = generateUniqueId("RN");
            Node n = new Node(id, x, y);
            addNode(n);
            added.add(n);
        }
        return added;
    }

    /**
     * Connects new nodes randomly to the existing graph.
     * Each new node gets at most edgesPerNode edges connecting it to random
     * existing (non-new) nodes in the graph, if they are not already connected.
     *
     * @param newNodes    the list of newly added nodes
     * @param edgesPerNode maximum number of edges per new node
     */
    public void addRandomEdges(List<Node> newNodes, int edgesPerNode) {
        List<Node> existingNodes = new ArrayList<>(nodes);
        existingNodes.removeAll(newNodes);

        for (Node newNode : newNodes) {
            List<Node> candidates = new ArrayList<>(existingNodes);
            int added = 0;
            while (added < edgesPerNode && !candidates.isEmpty()) {
                int idx = RANDOM.nextInt(candidates.size());
                Node target = candidates.remove(idx);
                if (!edgeExists(newNode, target)) {
                    double dist = Math.hypot(newNode.getX() - target.getX(),
                                             newNode.getY() - target.getY());
                    double weight = Math.max(1.0, dist);
                    addEdge(new Edge(newNode, target, weight));
                    added++;
                }
            }
            // Also connect to nearby new nodes
            for (Node other : newNodes) {
                if (other.equals(newNode)) continue;
                if (added >= edgesPerNode) break;
                if (!edgeExists(newNode, other)) {
                    double dist = Math.hypot(newNode.getX() - other.getX(),
                                             newNode.getY() - other.getY());
                    addEdge(new Edge(newNode, other, Math.max(1.0, dist)));
                    added++;
                }
            }
            existingNodes.add(newNode);
        }
    }

    /**
     * Resets statistics for all nodes and edges in the graph.
     */
    public void resetStats() {
        for (Node n : nodes) n.resetStats();
        for (Edge e : edges) e.resetStats();
    }

    /** @return the list of all nodes in the graph */
    public List<Node> getNodes() { return nodes; }
    /** @return the list of all edges in the graph */
    public List<Edge> getEdges() { return edges; }

    // ── Deprecated French-named aliases ──────────────────────────────────────
    /** @deprecated Use {@link #addNode(Node)} */
    @Deprecated public void ajouterNode(Node n) { addNode(n); }
    /** @deprecated Use {@link #removeNode(Node)} */
    @Deprecated public void supprimerNode(Node n) { removeNode(n); }
    /** @deprecated Use {@link #addEdge(Edge)} */
    @Deprecated public void ajouterEdge(Edge e) { addEdge(e); }
    /** @deprecated Use {@link #removeEdge(Edge)} */
    @Deprecated public void supprimerEdge(Edge e) { removeEdge(e); }
    /** @deprecated Use {@link #getEdgesOfNode(Node)} */
    @Deprecated public List<Edge> getEdgesDuNoeud(Node n) { return getEdgesOfNode(n); }
    /** @deprecated Use {@link #getRawNeighbors(Node)} */
    @Deprecated public List<Node> getVoisinsRaw(Node n) { return getRawNeighbors(n); }
    /** @deprecated Use {@link #getNeighbors(Node)} */
    @Deprecated public List<Node> getVoisins(Node n) { return getNeighbors(n); }
    /** @deprecated Use {@link #getWeight(Node, Node)} */
    @Deprecated public double getPoids(Node u, Node v) { return getWeight(u, v); }
    /** @deprecated Use {@link #edgeExists(Node, Node)} */
    @Deprecated public boolean areteExiste(Node a, Node b) { return edgeExists(a, b); }
    /** @deprecated Use {@link #isTooClose(double, double, double)} */
    @Deprecated public boolean tropProche(double x, double y, double r) { return isTooClose(x, y, r); }
    /** @deprecated Use {@link #getClosestNode(Node, Node)} */
    @Deprecated public Node getNoeudLePlusProche(Node ref, Node excl) { return getClosestNode(ref, excl); }
    /** @deprecated Use {@link #generateUniqueId(String)} */
    @Deprecated public String genererIdUnique(String prefix) { return generateUniqueId(prefix); }
}
