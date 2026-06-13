package evacuation.graph;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a danger zone node in the evacuation graph.
 * Danger zones propagate through the graph over time, blocking edges and nodes.
 *
 * <p>Fire propagation uses two alternating sub-phases:
 * <ul>
 *   <li>Phase 0 (edges): edges from the burning front to neighbors turn red (blocked).
 *       Neighbor nodes remain accessible but threatened.</li>
 *   <li>Phase 1 (nodes): threatened nodes become blocked and form the new burning front.</li>
 * </ul>
 *
 * <p>Flood propagation blocks one edge per cycle from the zone's edges.
 */
public class DangerZone extends Node {

    private static final long serialVersionUID = 1L;

    /**
     * The type of danger represented by this zone.
     */
    public enum DangerType {
        /** Fire — propagates to neighboring nodes over time. */
        FIRE,
        /** Flood — blocks neighboring edges over time. */
        FLOOD,
        /** A dangerous person — slows nearby agents. */
        DANGEROUS_PERSON
    }

    private DangerType dangerType;
    private int riskLevel;
    private int propagationTicks;

    /** Nodes currently on the burning/flooding front. */
    private Set<Node> burningNodes;
    /** Nodes that will be blocked in the next phase 1. */
    private Set<Node> nodesToBlock;
    /** 0 = propagate to edges, 1 = block neighbor nodes. */
    private int subPhase;

    /**
     * Constructs a DangerZone at the origin.
     *
     * @param id        unique identifier
     * @param type      the type of danger
     * @param riskLevel the risk level (0–10)
     */
    public DangerZone(String id, DangerType type, int riskLevel) {
        super(id);
        init(type, riskLevel);
    }

    /**
     * Constructs a DangerZone at the specified position.
     *
     * @param id        unique identifier
     * @param x         x coordinate
     * @param y         y coordinate
     * @param type      the type of danger
     * @param riskLevel the risk level (0–10)
     */
    public DangerZone(String id, double x, double y, DangerType type, int riskLevel) {
        super(id, x, y);
        init(type, riskLevel);
    }

    /**
     * Constructs a DangerZone parsing the danger type from a string.
     *
     * @param id        unique identifier
     * @param x         x coordinate
     * @param y         y coordinate
     * @param typeStr   danger type string (FIRE, FLOOD, DANGEROUS_PERSON)
     * @param riskLevel the risk level (0–10)
     */
    public DangerZone(String id, double x, double y, String typeStr, int riskLevel) {
        this(id, x, y, parseType(typeStr), riskLevel);
    }

    private void init(DangerType type, int riskLevel) {
        this.dangerType        = type;
        this.riskLevel         = riskLevel;
        this.propagationTicks  = 0;
        this.subPhase          = 0;
        this.burningNodes      = new LinkedHashSet<>();
        this.nodesToBlock      = new LinkedHashSet<>();
        if (type != DangerType.DANGEROUS_PERSON) {
            block();
            burningNodes.add(this);
        }
    }

    private static DangerType parseType(String s) {
        if (s == null) return DangerType.FIRE;
        switch (s.toUpperCase()) {
            case "FLOOD":            return DangerType.FLOOD;
            case "DANGEROUS_PERSON": return DangerType.DANGEROUS_PERSON;
            // Legacy French names
            case "INONDATION":          return DangerType.FLOOD;
            case "PERSONNE_DANGEREUSE": return DangerType.DANGEROUS_PERSON;
            default:                    return DangerType.FIRE;
        }
    }

    /**
     * Resets the propagation state to its initial condition.
     * Called by SimulationEngine on simulation reset.
     */
    public void reset() {
        propagationTicks = 0;
        subPhase         = 0;
        burningNodes.clear();
        nodesToBlock.clear();
        if (dangerType != DangerType.DANGEROUS_PERSON) {
            burningNodes.add(this);
        }
    }

    /**
     * Propagates the danger zone effect by one step.
     * This method is called each simulation tick.
     * Returns true if the graph topology changed (triggering path recalculation).
     *
     * @param graph the simulation graph
     * @return true if any edges or nodes were blocked/changed
     */
    public boolean propagate(Graph graph) {
        propagationTicks++;
        if (propagationTicks % 4 != 0) return false;

        switch (dangerType) {
            case FIRE:             return propagateFire(graph);
            case FLOOD:            return propagateFlood(graph);
            case DANGEROUS_PERSON:
                System.out.println("[DANGER] Dangerous person at " + getId()
                    + " — nearby agents slowed");
                return false;
            default:               return false;
        }
    }

    private boolean propagateFire(Graph graph) {
        if (burningNodes.isEmpty()) return false;

        if (subPhase == 0) {
            // Phase A: edges from burning front turn red (blocked by fire)
            boolean changed = false;
            nodesToBlock.clear();

            for (Node burning : burningNodes) {
                for (Edge e : graph.getEdgesOfNode(burning)) {
                    if (!e.isAvailable()) continue;
                    e.blockByFire();
                    changed = true;

                    Node neighbor = e.getSource().equals(burning)
                                    ? e.getDestination() : e.getSource();

                    if (!(neighbor instanceof DangerZone)
                            && !(neighbor instanceof Exit)
                            && !neighbor.isBlocked()) {
                        nodesToBlock.add(neighbor);
                        System.out.println("[FIRE] Edge "
                            + e.getSource().getId() + " → " + e.getDestination().getId()
                            + " on fire / " + neighbor.getId() + " threatened (still accessible)");
                    }
                }
            }
            subPhase = 1;
            return changed;

        } else {
            // Phase B: threatened nodes become blocked, form new burning front
            boolean changed = false;
            burningNodes.clear();

            for (Node n : nodesToBlock) {
                if (n.isBlocked() || n instanceof Exit || n instanceof DangerZone) continue;
                n.block();
                burningNodes.add(n);
                changed = true;
                System.out.println("[FIRE] Node " + n.getId() + " caught by fire!");
            }
            nodesToBlock.clear();
            subPhase = 0;
            return changed;
        }
    }

    private boolean propagateFlood(Graph graph) {
        for (Edge e : graph.getEdgesOfNode(this)) {
            if (e.isAvailable()) {
                e.blockByFlood();
                System.out.println("[FLOOD] Edge blocked: "
                    + e.getSource().getId() + " → " + e.getDestination().getId());
                return true;
            }
        }
        return false;
    }

    /**
     * Signals this danger zone (prints an alert message).
     */
    public void signal() {
        System.out.println("[DangerZone] ALERT: " + getId()
            + " — type=" + dangerType + ", risk=" + riskLevel + "/10");
    }

    /**
     * Returns the slowdown factor applied to agents near this danger zone.
     * DANGEROUS_PERSON returns 0.5 (half speed); other types return 1.0.
     *
     * @return slowdown factor
     */
    public double getSlowdownFactor() {
        return dangerType == DangerType.DANGEROUS_PERSON ? 0.5 : 1.0;
    }

    /**
     * Returns the set of nodes that will be blocked in the next phase 1.
     *
     * @return unmodifiable set of nodes to block
     */
    public Set<Node> getNodesToBlock() {
        return Collections.unmodifiableSet(nodesToBlock);
    }

    /** @return the risk level of this danger zone */
    public int getRiskLevel() { return riskLevel; }
    /** @return the danger type */
    public DangerType getDangerType() { return dangerType; }
    /** @return the danger type as a string */
    public String getDangerTypeStr() { return dangerType.name(); }

    // ── Deprecated French-named aliases ──────────────────────────────────────

    /** @deprecated Use {@link #reset()} */
    @Deprecated public void reinitialiserPropagation() { reset(); }
    /** @deprecated Use {@link #propagate(Graph)} */
    @Deprecated public boolean propager(Graph graph) { return propagate(graph); }
    /** @deprecated Use {@link #getSlowdownFactor()} */
    @Deprecated public double getFacteurRalentissement() { return getSlowdownFactor(); }
    /** @deprecated Use {@link #getRiskLevel()} */
    @Deprecated public int getNiveauRisque() { return riskLevel; }
    /** @deprecated Use {@link #getDangerType()} */
    @Deprecated public DangerType getTypeDanger() { return dangerType; }
    /** @deprecated Use {@link #getDangerTypeStr()} */
    @Deprecated public String getTypeDangerStr() { return dangerType.name(); }
    /** @deprecated Use {@link #signal()} */
    @Deprecated public void signaler() { signal(); }

    // Legacy inner enum for backward compatibility
    /** @deprecated Use {@link DangerType} */
    @Deprecated
    public enum TypeDanger {
        FEU, INONDATION, PERSONNE_DANGEREUSE;
        public DangerType toDangerType() {
            switch (this) {
                case INONDATION:          return DangerType.FLOOD;
                case PERSONNE_DANGEREUSE: return DangerType.DANGEROUS_PERSON;
                default:                  return DangerType.FIRE;
            }
        }
        public static TypeDanger fromDangerType(DangerType dt) {
            switch (dt) {
                case FLOOD:            return INONDATION;
                case DANGEROUS_PERSON: return PERSONNE_DANGEREUSE;
                default:               return FEU;
            }
        }
    }

    @Override
    public String toString() {
        return "DangerZone{id='" + getId() + "', type='" + dangerType
             + "', risk=" + riskLevel + ", subPhase=" + subPhase + "}";
    }
}
