package evacuation.routing;

import evacuation.graph.Node;
import java.util.List;

/**
 * Interface for pathfinding algorithms on the evacuation graph.
 * Implementations compute shortest paths between nodes.
 */
public interface PathFinder {

    /**
     * Computes the shortest path from source to destination.
     *
     * @param src  the starting node
     * @param dest the target node
     * @return ordered list of nodes from src to dest (inclusive), or empty list if no path
     */
    List<Node> findPath(Node src, Node dest);

    /**
     * Invalidates cached computation, forcing recalculation on next call.
     */
    void reset();

    /**
     * Returns the last computed shortest path.
     *
     * @return last computed path, or empty list if none computed
     */
    List<Node> getLastPath();

    // Deprecated French-named aliases
    /** @deprecated Use {@link #findPath(Node, Node)} */
    @Deprecated
    default List<Node> calculerChemin(Node src, Node dest) { return findPath(src, dest); }

    /** @deprecated Use {@link #reset()} */
    @Deprecated
    default void recalculer() { reset(); }

    /** @deprecated Use {@link #getLastPath()} */
    @Deprecated
    default List<Node> getPlusCourt() { return getLastPath(); }
}
