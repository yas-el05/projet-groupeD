package evacuation.graph;

/**
 * Represents an evacuation exit node in the graph.
 * An exit has a capacity and can be opened or closed.
 * Agents target exits as their final destination.
 */
public class Exit extends Node {

    private static final long serialVersionUID = 1L;

    /** The evacuation capacity of this exit. */
    private int capacity;
    /** Whether this exit is currently open. */
    private boolean open;

    /**
     * Constructs an Exit at the origin with the given capacity.
     *
     * @param id       unique identifier
     * @param capacity maximum throughput capacity
     */
    public Exit(String id, int capacity) {
        super(id);
        this.capacity = capacity;
        this.open     = true;
    }

    /**
     * Constructs an Exit at the specified position.
     *
     * @param id       unique identifier
     * @param x        x coordinate
     * @param y        y coordinate
     * @param capacity maximum throughput capacity
     */
    public Exit(String id, double x, double y, int capacity) {
        super(id, x, y);
        this.capacity = capacity;
        this.open     = true;
    }

    /**
     * Opens this exit, allowing agents to use it.
     */
    public void open() {
        this.open = true;
        System.out.println("[Exit] " + getId() + " is open.");
    }

    /**
     * Closes this exit, preventing agents from using it.
     */
    public void close() {
        this.open = false;
        System.out.println("[Exit] " + getId() + " is closed.");
    }

    /**
     * Returns whether this exit is currently open.
     *
     * @return true if open
     */
    public boolean isOpen() { return open; }

    public int getCapacity() { return capacity; }

  
    public void setCapacity(int capacity) { this.capacity = Math.max(1, capacity); }

    // Deprecated aliases for backward compatibility with Sortie
    /** @deprecated Use {@link #isOpen()} */
    @Deprecated public boolean isEstOuverte() { return open; }
    /** @deprecated Use {@link #open()} */
    @Deprecated public void ouvrir() { open(); }
    /** @deprecated Use {@link #close()} */
    @Deprecated public void fermer() { close(); }

    @Override
    public String toString() {
        return "Exit{id='" + getId() + "', capacity=" + capacity + ", open=" + open + "}";
    }
}
