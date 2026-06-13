package evacuation.graph;

import java.io.Serializable;

/**
 * Represents an edge (connection) between two nodes in the evacuation graph.
 * An edge has a weight, availability status, block type, agent transit tracking,
 * direction flag, and a speed modifier.
 */
public class Edge implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The type of blockage affecting this edge.
     */
    public enum BlockType {
        /** No blockage. */
        NONE,
        /** Blocked by fire. */
        FIRE,
        /** Blocked by flood. */
        FLOOD
    }

    private Node source;
    private Node destination;
    private double weight;
    private boolean available;
    private BlockType blockType;
    private int agentsInTransit;
    private int capacity = 3;
    /** Whether this edge is directed (one-way: source→destination only). */
    private boolean directed = false;
    /** Speed modifier: 1.0=normal, 2.0=fast, 0.5=slow. Affects Dijkstra cost. */
    private double speedModifier = 1.0;
    /** Total number of agents that have transited this edge. */
    private int agentsPassed = 0;

    /**
     * Constructs a bidirectional edge between two nodes with the given weight.
     *
     * @param source      the source node
     * @param destination the destination node
     * @param weight      the base weight (cost) of this edge
     */
    public Edge(Node source, Node destination, double weight) {
        this.source          = source;
        this.destination     = destination;
        this.weight          = weight;
        this.available       = true;
        this.blockType       = BlockType.NONE;
        this.agentsInTransit = 0;
    }

    /**
     * Returns the effective weight of this edge for pathfinding.
     * If the edge is unavailable, returns positive infinity.
     * If a speed modifier is set, divides the weight by it (faster = lower cost).
     *
     * @return the effective weight, or {@link Double#POSITIVE_INFINITY} if unavailable
     */
    public double getWeight() {
        return available ? weight / speedModifier : Double.POSITIVE_INFINITY;
    }

    /**
     * Blocks this edge with the default FIRE blockage type.
     */
    public void block() {
        this.available = false;
        if (this.blockType == BlockType.NONE) this.blockType = BlockType.FIRE;
    }

    /**
     * Blocks this edge due to fire.
     */
    public void blockByFire() {
        this.available = false;
        this.blockType = BlockType.FIRE;
    }

    /**
     * Blocks this edge due to flooding.
     */
    public void blockByFlood() {
        this.available = false;
        this.blockType = BlockType.FLOOD;
    }

    /**
     * Unblocks this edge, restoring it to full availability.
     */
    public void unblock() {
        this.available = true;
        this.blockType = BlockType.NONE;
    }

    /**
     * Adds an agent to the in-transit count.
     */
    public void addAgent() { agentsInTransit = Math.max(0, agentsInTransit + 1); }

    /**
     * Removes an agent from the in-transit count.
     */
    public void removeAgent() { agentsInTransit = Math.max(0, agentsInTransit - 1); }

    /**
     * Records that an agent has fully transited this edge.
     * Increments the agentsPassed counter.
     */
    public void recordTransit() { agentsPassed++; }

    /**
     * Resets the agentsPassed counter to zero.
     */
    public void resetStats() { agentsPassed = 0; }

    public Node getSource() { return source; }
    public Node getDestination() { return destination; }
    public boolean isAvailable() { return available; }
    public double getBaseWeight() { return weight; }
    public BlockType getBlockType() { return blockType; }
    public int getAgentsInTransit() { return agentsInTransit; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int c) { this.capacity = Math.max(1, c); }

    public boolean isDirected() { return directed; }
    public void setDirected(boolean directed) { this.directed = directed; }

    public double getSpeedModifier() { return speedModifier; }
    public void setSpeedModifier(double speedModifier) {
        this.speedModifier = Math.max(0.01, speedModifier);
    }

    public int getAgentsPassed() { return agentsPassed; }

    // ── Deprecated French-named aliases ──────────────────────────────────────
    /** @deprecated Use {@link #getWeight()} */
    @Deprecated public double getPoids() { return getWeight(); }
    /** @deprecated Use {@link #isAvailable()} */
    @Deprecated public boolean isDisponible() { return available; }
    /** @deprecated Use {@link #getBaseWeight()} */
    @Deprecated public double getPoidsBase() { return weight; }
    /** @deprecated Use {@link #getBlockType()} */
    @Deprecated public BlockType getTypeBlocage() { return blockType; }
    /** @deprecated Use {@link #addAgent()} */
    @Deprecated public void ajouterAgent() { addAgent(); }
    /** @deprecated Use {@link #removeAgent()} */
    @Deprecated public void retirerAgent() { removeAgent(); }
    /** @deprecated Use {@link #getAgentsInTransit()} */
    @Deprecated public int getAgentsEnTransit() { return agentsInTransit; }
    /** @deprecated Use {@link #getCapacity()} */
    @Deprecated public int getCapacite() { return capacity; }
    /** @deprecated Use {@link #setCapacity(int)} */
    @Deprecated public void setCapacite(int c) { setCapacity(c); }
    /** @deprecated Use {@link #block()} */
    @Deprecated public void bloquer() { block(); }
    /** @deprecated Use {@link #blockByFire()} */
    @Deprecated public void bloquerParFeu() { blockByFire(); }
    /** @deprecated Use {@link #blockByFlood()} */
    @Deprecated public void bloquerParInondation() { blockByFlood(); }
    /** @deprecated Use {@link #unblock()} */
    @Deprecated public void debloquer() { unblock(); }

    // Deprecated inner enum for backward compatibility
    /** @deprecated Use {@link BlockType} */
    @Deprecated
    public enum TypeBlocage {
        AUCUN, FEU, INONDATION;
        public BlockType toBlockType() {
            switch (this) {
                case FEU:       return BlockType.FIRE;
                case INONDATION: return BlockType.FLOOD;
                default:        return BlockType.NONE;
            }
        }
    }

    @Override
    public String toString() {
        return "Edge{" + source.getId() + " → " + destination.getId()
             + ", weight=" + weight + ", available=" + available
             + ", blockType=" + blockType + ", directed=" + directed
             + ", speedModifier=" + speedModifier + "}";
    }
}
