package evacuation.graph;

import java.io.Serializable;

/**
 * Represents a node (vertex) in the evacuation graph.
 * A node has a position (x, y), a capacity, a blocked state,
 * and tracks how many agents have passed through it.
 */
public class Node implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Unique identifier for this node. */
    private String id;
    /** Whether this node is blocked (impassable). */
    private boolean blocked;
    /** X coordinate in graph space. */
    private double x;
    /** Y coordinate in graph space. */
    private double y;
    /** Maximum number of agents that can occupy this node simultaneously. */
    private int capacity = 3;
    /** Total number of agents that have passed through this node. */
    private int agentsPassed = 0;

    /**
     * Constructs a Node at the origin (0, 0).
     *
     * @param id unique identifier
     */
    public Node(String id) {
        this(id, 0, 0);
    }

    /**
     * Constructs a Node at the specified position.
     *
     * @param id unique identifier
     * @param x  x coordinate
     * @param y  y coordinate
     */
    public Node(String id, double x, double y) {
        this.id      = id;
        this.blocked = false;
        this.x       = x;
        this.y       = y;
    }

    /**
     * Returns whether this node is currently blocked.
     *
     * @return true if blocked
     */
    public boolean isBlocked() { return blocked; }

    /**
     * Blocks this node, preventing agents from entering.
     */
    public void block() {
        this.blocked = true;
        System.out.println("[Node] " + id + " is blocked.");
    }

    /**
     * Unblocks this node, allowing agents to enter again.
     */
    public void unblock() {
        this.blocked = false;
        System.out.println("[Node] " + id + " is unblocked.");
    }

    /**
     * Records that an agent has passed through this node.
     * Increments the agentsPassed counter.
     */
    public void recordAgentPassage() {
        agentsPassed++;
    }

    /**
     * Returns the total number of agents that have passed through this node.
     *
     * @return agent passage count
     */
    public int getAgentsPassed() { return agentsPassed; }

    /**
     * Returns a formatted statistics string for this node.
     *
     * @return stats string showing id, capacity, and agents passed
     */
    public String getStats() {
        return String.format("Node %s | capacity=%d | agentsPassed=%d | blocked=%b",
                id, capacity, agentsPassed, blocked);
    }

    /**
     * Resets the agentsPassed counter to zero.
     */
    public void resetStats() {
        agentsPassed = 0;
    }

    public String getId() { return id; }

    public double getX() { return x; }
    public double getY() { return y; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    
    public int getCapacity() { return capacity; }
    public void setCapacity(int c) { this.capacity = Math.max(1, c); }

    // Deprecated French-named aliases kept for backward compatibility
    /** @deprecated Use {@link #isBlocked()} */
    @Deprecated public boolean isBloque() { return blocked; }
    /** @deprecated Use {@link #block()} */
    @Deprecated public void bloquer() { block(); }
    /** @deprecated Use {@link #unblock()} */
    @Deprecated public void debloquer() { unblock(); }
    /** @deprecated Use {@link #getCapacity()} */
    @Deprecated public int getCapacite() { return capacity; }
    /** @deprecated Use {@link #setCapacity(int)} */
    @Deprecated public void setCapacite(int c) { setCapacity(c); }

    @Override
    public String toString() {
        return "Node{id='" + id + "', blocked=" + blocked + "}";
    }
}
