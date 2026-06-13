package evacuation.agent;

import evacuation.graph.DangerZone;
import evacuation.graph.Edge;
import evacuation.graph.Exit;
import evacuation.graph.Graph;
import evacuation.graph.Node;
import evacuation.routing.DijkstraPathFinder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Represents an agent moving through the evacuation graph toward an exit.
 *
 * <p>Agents have psychological states that affect speed and behavior,
 * and movement modes that determine how they choose their next node.
 *
 * <p>Animation speeds by psychological state:
 * <ul>
 *   <li>CALM: base speed (0.013 × speed per frame)</li>
 *   <li>PANIC: 2.2× base speed</li>
 *   <li>MADNESS: 3.2× base speed, random movement, stops at exits</li>
 * </ul>
 */
public class Agent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The movement state of an agent.
     */
    public enum MovementState {
        /** Agent is waiting, not yet initialized. */
        WAITING,
        /** Agent is actively moving. */
        MOVING,
        /** Agent has reached its destination. */
        ARRIVED,
        /** Agent is blocked with no path available. */
        BLOCKED
    }

    /**
     * The psychological state of an agent.
     */
    public enum PsychologicalState {
        /** Calm — moves at normal speed, yields to others. */
        CALM,
        /** Panic — moves faster, recalculates on congestion, becomes PRIORITY. */
        PANIC,
        /** Madness — moves randomly, very fast, stops at any exit. */
        MADNESS
    }

    /**
     * The movement behavior of an agent when navigating.
     */
    public enum Behavior {
        /** Agent yields to others in congested nodes. */
        YIELD,
        /** Agent has priority, does not yield. */
        PRIORITY,
        /** Agent tries to follow the nearest moving agent. */
        FOLLOW
    }

    /**
     * The destination selection mode.
     */
    public enum DestinationMode {
        /** Agent moves toward a fixed destination. */
        FIXED,
        /** Agent picks a random new destination each time it arrives. */
        RANDOM_WALK,
        /** Agent moves away from its destination (flee). */
        FLEE_DESTINATION,
        /** Agent moves toward the most crowded neighbor. */
        TOWARD_DENSE,
        /** Agent moves toward the least crowded neighbor. */
        FLEE_DENSITY
    }

    /**
     * What the agent does when it arrives at its destination.
     */
    public enum ArrivalBehavior {
        /** Agent stops upon arrival. */
        STOP,
        /** Agent picks a random new destination. */
        RANDOM_DESTINATION,
        /** Agent is marked for deletion upon arrival. */
        DELETE
    }

    private final String id;

    // Logical position
    private Node position;
    private final Node startingPosition;
    private Node departureNode;
    private Node targetNode;
    private double edgeProgress;

    // Display position (for smooth animation)
    private double displayX, displayY;

    private Node destination;
    private final Node initialDestination;
    private final Graph graph;

    private int speed;
    private double densityTolerance;
    private Behavior behavior;
    private PsychologicalState psychologicalState;
    private PsychologicalState initialPsychologicalState;
    private Behavior initialBehavior;
    private boolean autoPanicked = false;

    private DestinationMode destinationMode;
    private MovementState movementState;
    private ArrivalBehavior arrivalBehavior = ArrivalBehavior.STOP;
    private boolean markedForDeletion = false;
    private int agentsPassed = 0;

    private DijkstraPathFinder pathFinder;
    private List<Node> path;
    private int pathIndex;
    private int waitingTicks;

    /** The edge currently being traversed. */
    private transient Edge currentEdge = null;

    private static final Random RANDOM = new Random();
    private List<Agent> otherAgents = new ArrayList<>();

    /**
     * Constructs an Agent with all parameters.
     *
     * @param id                unique identifier
     * @param startNode         initial position node
     * @param destination       target destination node
     * @param graph             the simulation graph
     * @param speed             movement speed (1–5)
     * @param densityTolerance  how much congestion the agent tolerates (0.0–1.0)
     * @param behavior          movement behavior
     * @param psychologicalState initial psychological state
     * @param destinationMode   destination selection mode
     */
    public Agent(String id, Node startNode, Node destination, Graph graph,
                 int speed, double densityTolerance,
                 Behavior behavior, PsychologicalState psychologicalState,
                 DestinationMode destinationMode) {
        this.id                       = id;
        this.position                 = startNode;
        this.startingPosition         = startNode;
        this.destination              = destination;
        this.initialDestination       = destination;
        this.graph                    = graph;
        this.speed                    = Math.max(1, speed);
        this.densityTolerance         = Math.min(1.0, Math.max(0.0, densityTolerance));
        this.behavior                 = behavior;
        this.psychologicalState       = psychologicalState;
        this.initialPsychologicalState = psychologicalState;
        this.initialBehavior          = behavior;
        this.destinationMode          = destinationMode;
        this.movementState            = MovementState.WAITING;
        this.pathFinder               = new DijkstraPathFinder(graph);
        this.path                     = new ArrayList<>();
        this.pathIndex                = 0;
        this.waitingTicks             = 0;
        this.departureNode            = startNode;
        this.targetNode               = null;
        this.edgeProgress             = 1.0;
        this.displayX                 = startNode.getX();
        this.displayY                 = startNode.getY();
    }

    /**
     * Constructs an Agent with default settings (speed=1, tolerance=1.0, YIELD, CALM, FIXED).
     *
     * @param id          unique identifier
     * @param startNode   initial position
     * @param destination target destination
     * @param graph       the simulation graph
     */
    public Agent(String id, Node startNode, Node destination, Graph graph) {
        this(id, startNode, destination, graph, 1, 1.0,
             Behavior.YIELD, PsychologicalState.CALM, DestinationMode.FIXED);
    }

    // ── Reset ──────────────────────────────────────────────────────────────────

    /**
     * Resets this agent to its initial state, as if the simulation had not started.
     */
    public void reset() {
        releaseCurrentEdge();
        Node safeStart = graph.getNodes().contains(startingPosition)
                         ? startingPosition
                         : graph.getClosestNode(startingPosition, null);
        if (safeStart == null) safeStart = startingPosition;
        this.position              = safeStart;
        this.destination           = initialDestination;
        this.departureNode         = safeStart;
        this.targetNode            = null;
        this.edgeProgress          = 1.0;
        this.displayX              = safeStart.getX();
        this.displayY              = safeStart.getY();
        this.movementState         = MovementState.WAITING;
        this.psychologicalState    = initialPsychologicalState;
        this.behavior              = initialBehavior;
        this.autoPanicked          = false;
        this.path                  = new ArrayList<>();
        this.pathIndex             = 0;
        this.waitingTicks          = 0;
        this.markedForDeletion     = false;
        this.agentsPassed          = 0;
    }

    // ── Initialize ─────────────────────────────────────────────────────────────

    /**
     * Initializes the agent for simulation, computing the initial path.
     * Automatically selects the nearest open exit if no destination is set.
     */
    public void initialize() {
        Node nearestExit = findNearestExit();
        if (nearestExit != null) destination = nearestExit;

        if (destination == null || (destination.isBlocked() && !(destination instanceof Exit))) {
            movementState = MovementState.BLOCKED;
            return;
        }
        path = pathFinder.findPath(position, destination);
        if (path.isEmpty()) {
            movementState = MovementState.BLOCKED;
        } else {
            pathIndex     = 1;
            movementState = MovementState.MOVING;
            System.out.println("[Agent " + id + "] Path: " + pathToString());
        }
    }

    // ── Tick ───────────────────────────────────────────────────────────────────

    /**
     * Advances the agent's logical state by one simulation tick.
     */
    public void move() {
        if (movementState == MovementState.ARRIVED
                || movementState == MovementState.WAITING) return;

        checkPsychologicalState();

        if (targetNode != null && edgeProgress < 1.0) return;

        if (waitingTicks > 0) { waitingTicks--; return; }

        if (movementState == MovementState.BLOCKED) { recalculatePath(); return; }

        if (psychologicalState == PsychologicalState.MADNESS) { moveRandomly(); return; }

        double slowFactor = getNeighborSlowdownFactor();
        advanceOneNode(slowFactor);
    }

    // ── Visual animation ───────────────────────────────────────────────────────

    /**
     * Updates the visual display position of this agent for smooth animation.
     * Should be called at 60 fps.
     *
     * @param animSpeed animation step size per frame
     * @return true if still animating
     */
    public boolean animateMovement(double animSpeed) {
        if (movementState == MovementState.ARRIVED) {
            if (targetNode != null && edgeProgress < 1.0) {
                edgeProgress = Math.min(1.0, edgeProgress + animSpeed);
                double sx = departureNode.getX(), sy = departureNode.getY();
                double ex = targetNode.getX(),    ey = targetNode.getY();
                displayX = sx + (ex - sx) * edgeProgress;
                displayY = sy + (ey - sy) * edgeProgress;
                if (edgeProgress >= 1.0) {
                    displayX = ex; displayY = ey;
                    releaseCurrentEdge();
                    targetNode = null;
                }
                return true;
            }
            displayX = position.getX();
            displayY = position.getY();
            releaseCurrentEdge();
            targetNode = null;
            return false;
        }

        if (targetNode == null || edgeProgress >= 1.0) {
            displayX = position.getX();
            displayY = position.getY();
            return false;
        }

        edgeProgress = Math.min(1.0, edgeProgress + animSpeed);
        double sx = departureNode.getX(), sy = departureNode.getY();
        double ex = targetNode.getX(),    ey = targetNode.getY();
        displayX = sx + (ex - sx) * edgeProgress;
        displayY = sy + (ey - sy) * edgeProgress;
        if (edgeProgress >= 1.0) {
            displayX = ex; displayY = ey;
            releaseCurrentEdge();
            targetNode = null;
        }
        return edgeProgress < 1.0;
    }

    /**
     * Returns the animation speed for this agent, based on its psychological state
     * and the current edge's speed modifier.
     *
     * @return animation step size per frame
     */
    public double getAnimationSpeed() {
        double base = 0.013 * speed;
        double edgeMult = (currentEdge != null) ? currentEdge.getSpeedModifier() : 1.0;
        return switch (psychologicalState) {
            case PANIC    -> base * 2.2 * edgeMult;
            case MADNESS  -> base * 3.2 * edgeMult;
            default       -> base * edgeMult;
        };
    }

    /**
     * Synchronizes the display position with the current logical node position.
     */
    public void syncDisplayWithNode() {
        if (targetNode == null) {
            displayX = position.getX();
            displayY = position.getY();
        }
    }

    // ── Psychological state ────────────────────────────────────────────────────

    private void checkPsychologicalState() {
        if (movementState == MovementState.ARRIVED) return;
        boolean fireNearby = hasNearbyFire();

        if (fireNearby && psychologicalState == PsychologicalState.CALM) {
            autoPanicked      = true;
            psychologicalState = PsychologicalState.PANIC;
            behavior           = Behavior.PRIORITY;
            System.out.println("[Agent " + id + "] Fire nearby → PANIC (automatic)");
            recalculatePath();
        } else if (!fireNearby && autoPanicked
                && (psychologicalState == PsychologicalState.PANIC
                    || psychologicalState == PsychologicalState.MADNESS)) {
            autoPanicked      = false;
            psychologicalState = initialPsychologicalState;
            behavior           = initialBehavior;
            System.out.println("[Agent " + id + "] Safe zone → reverting to " + initialPsychologicalState);
            if (movementState != MovementState.BLOCKED) recalculatePath();
        }
    }

    private boolean hasNearbyFire() {
        for (Node v : graph.getRawNeighbors(position))
            if (v instanceof DangerZone
                    && ((DangerZone) v).getDangerType() == DangerZone.DangerType.FIRE)
                return true;
        return false;
    }

    // ── Node advancement ───────────────────────────────────────────────────────

    private boolean advanceOneNode(double slowFactor) {
        releaseCurrentEdge();

        if (pathIndex >= path.size()) { arrive(); return false; }

        Node next = path.get(pathIndex);

        // Override next for special destination modes
        if (destinationMode == DestinationMode.FLEE_DESTINATION) {
            Node fleeTo = getNeighborFurthestFromDestination();
            if (fleeTo != null) next = fleeTo;
        } else if (destinationMode == DestinationMode.TOWARD_DENSE) {
            Node denseTo = getMostDenseNeighbor();
            if (denseTo != null) next = denseTo;
        } else if (destinationMode == DestinationMode.FLEE_DENSITY) {
            Node fleeDense = getLeastDenseNeighbor();
            if (fleeDense != null) next = fleeDense;
        } else if (behavior == Behavior.FOLLOW) {
            Agent nearest = findNearestMovingAgent();
            if (nearest != null && nearest.getTargetNode() != null) {
                Node followTarget = nearest.getTargetNode();
                List<Node> accessible = graph.getNeighbors(position);
                if (accessible.contains(followTarget) && !followTarget.isBlocked()) {
                    next = followTarget;
                }
            }
        }

        if (next.isBlocked() || !graph.getNeighbors(position).contains(next)) {
            recalculatePath();
            return false;
        }

        // Node congestion check
        if (!(next instanceof Exit)) {
            int agentsHere = countAgentsAtNode(next);
            if (agentsHere >= next.getCapacity()) {
                if (psychologicalState == PsychologicalState.PANIC) {
                    recalculatePath();
                } else {
                    waitingTicks = 1;
                }
                return false;
            }
        }

        // Edge congestion check
        Edge edgeToNext = graph.getEdge(position, next);
        if (edgeToNext != null && edgeToNext.getAgentsInTransit() >= edgeToNext.getCapacity()) {
            if (psychologicalState == PsychologicalState.PANIC) {
                recalculatePath();
            } else {
                waitingTicks = 1;
            }
            return false;
        }

        // YIELD behavior (probabilistic pause)
        if (behavior == Behavior.YIELD
                && psychologicalState != PsychologicalState.PANIC) {
            if (countAgentsAtNode(next) > 0
                    && RANDOM.nextDouble() > densityTolerance) {
                waitingTicks = (int) Math.max(1, 1.0 / slowFactor);
                return false;
            }
        }

        Node oldPosition = position;
        departureNode = position;
        targetNode    = next;
        edgeProgress  = 0.0;
        position      = next;
        pathIndex++;

        // Record passage stats
        oldPosition.recordAgentPassage();
        agentsPassed++;

        if (edgeToNext != null) {
            edgeToNext.addAgent();
            edgeToNext.recordTransit();
            currentEdge = edgeToNext;
        }

        if (position.equals(destination)) { arrive(); return false; }
        return true;
    }

    private void arrive() {
        releaseCurrentEdge();
        movementState = MovementState.ARRIVED;
        System.out.println("[Agent " + id + "] Arrived at " + destination.getId());

        switch (arrivalBehavior) {
            case DELETE:
                markedForDeletion = true;
                break;
            case RANDOM_DESTINATION:
                destination = pickRandomDestination();
                if (destination != null) {
                    movementState = MovementState.MOVING;
                    recalculatePath();
                }
                break;
            case STOP:
            default:
                if (destinationMode == DestinationMode.RANDOM_WALK) {
                    destination = pickRandomDestination();
                    if (destination != null) {
                        movementState = MovementState.MOVING;
                        recalculatePath();
                    }
                }
                break;
        }
    }

    private void moveRandomly() {
        if (position instanceof Exit) { arrive(); return; }

        releaseCurrentEdge();

        List<Node> neighbors = graph.getNeighbors(position);
        if (neighbors.isEmpty()) { movementState = MovementState.BLOCKED; return; }
        Node next = neighbors.get(RANDOM.nextInt(neighbors.size()));

        Node oldPosition = position;
        departureNode = position;
        targetNode    = next;
        edgeProgress  = 0.0;
        position      = next;
        oldPosition.recordAgentPassage();
        agentsPassed++;

        Edge edge = graph.getEdge(departureNode, next);
        if (edge != null) { edge.addAgent(); edge.recordTransit(); currentEdge = edge; }

        if (position instanceof Exit) { arrive(); }
    }

    // ── Path recalculation ─────────────────────────────────────────────────────

    /**
     * Recalculates the path to the destination.
     * Automatically selects the nearest exit if in FIXED mode.
     */
    public void recalculatePath() {
        releaseCurrentEdge();
        targetNode    = null;
        edgeProgress  = 1.0;
        displayX      = position.getX();
        displayY      = position.getY();

        if (destinationMode == DestinationMode.RANDOM_WALK) {
            destination = pickRandomDestination();
        } else {
            Node nearestExit = findNearestExit();
            if (nearestExit != null) destination = nearestExit;
        }

        pathFinder.reset();
        if (destination == null) { movementState = MovementState.BLOCKED; return; }
        path = pathFinder.findPath(position, destination);
        if (path.isEmpty()) {
            movementState = MovementState.BLOCKED;
            System.out.println("[Agent " + id + "] Blocked — no path to " + destination.getId());
        } else {
            pathIndex     = 1;
            movementState = MovementState.MOVING;
            System.out.println("[Agent " + id + "] Redirected → " + pathToString());
        }
    }

    /**
     * Teleports the agent to the specified node and recalculates its path.
     * If the target node is at capacity, applies a strong congestion wait.
     *
     * @param node the node to teleport to
     */
    public void teleportTo(Node node) {
        releaseCurrentEdge();
        position      = node;
        departureNode = node;
        targetNode    = null;
        edgeProgress  = 1.0;
        displayX      = node.getX();
        displayY      = node.getY();
        path.clear();
        pathIndex = 0;
        int agentsHere = countAgentsAtNode(node);
        if (agentsHere >= node.getCapacity()) {
            waitingTicks = 2;
        }
        recalculatePath();
    }

    // ── Induced states ─────────────────────────────────────────────────────────

    /**
     * Induces panic in this agent (without modifying the initial state).
     */
    public void inducePanic() {
        if (psychologicalState == PsychologicalState.MADNESS) return;
        autoPanicked      = true;
        psychologicalState = PsychologicalState.PANIC;
        behavior           = Behavior.PRIORITY;
    }

    /**
     * Induces madness in this agent (without modifying the initial state).
     */
    public void induceMadness() {
        autoPanicked      = true;
        psychologicalState = PsychologicalState.MADNESS;
    }

    /**
     * Returns this agent toward its departure node (used when target node is deleted).
     * Induces panic.
     */
    public void returnToDepartureNode() {
        releaseCurrentEdge();
        position      = departureNode;
        targetNode    = null;
        edgeProgress  = 1.0;
        displayX      = departureNode.getX();
        displayY      = departureNode.getY();
        path.clear();
        pathIndex = 0;
        inducePanic();
        recalculatePath();
    }

    // ── Special destination mode helpers ──────────────────────────────────────

    private Node getNeighborFurthestFromDestination() {
        if (destination == null) return null;
        return graph.getNeighbors(position).stream()
            .filter(n -> !n.isBlocked())
            .max(Comparator.comparingDouble(n ->
                Math.hypot(n.getX() - destination.getX(), n.getY() - destination.getY())))
            .orElse(null);
    }

    private Node getMostDenseNeighbor() {
        List<Node> neighbors = graph.getNeighbors(position);
        return neighbors.stream()
            .filter(n -> !n.isBlocked() && !(n instanceof Exit))
            .max(Comparator.comparingInt(n -> countAgentsAtNode(n)))
            .orElse(null);
    }

    private Node getLeastDenseNeighbor() {
        List<Node> neighbors = graph.getNeighbors(position);
        return neighbors.stream()
            .filter(n -> !n.isBlocked() && !(n instanceof Exit))
            .min(Comparator.comparingInt(n -> countAgentsAtNode(n)))
            .orElse(null);
    }

    private Agent findNearestMovingAgent() {
        Agent nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Agent a : otherAgents) {
            if (a == this) continue;
            if (a.getMovementState() != MovementState.MOVING) continue;
            double d = Math.hypot(a.getDisplayX() - displayX, a.getDisplayY() - displayY);
            if (d < minDist) { minDist = d; nearest = a; }
        }
        return nearest;
    }

    // ── Private utilities ──────────────────────────────────────────────────────

    private void releaseCurrentEdge() {
        if (currentEdge != null) {
            currentEdge.removeAgent();
            currentEdge = null;
        }
    }

    private Node findNearestExit() {
        Node best = null;
        int minLen = Integer.MAX_VALUE;
        for (Node n : graph.getNodes()) {
            if (n instanceof Exit && !n.isBlocked() && ((Exit) n).isOpen()) {
                List<Node> ch = pathFinder.findPath(position, n);
                if (!ch.isEmpty() && ch.size() < minLen) {
                    minLen = ch.size();
                    best   = n;
                }
            }
        }
        return best;
    }

    private double getNeighborSlowdownFactor() {
        for (Node v : graph.getRawNeighbors(position))
            if (v instanceof DangerZone) return ((DangerZone) v).getSlowdownFactor();
        return 1.0;
    }

    private int countAgentsAtNode(Node node) {
        int count = 0;
        for (Agent a : otherAgents)
            if (a != this && a.getPosition().equals(node)) count++;
        return count;
    }

    private Node pickRandomDestination() {
        List<Node> accessible = new ArrayList<>();
        for (Node n : graph.getNodes())
            if (!n.isBlocked() && !n.equals(position)) accessible.add(n);
        return accessible.isEmpty() ? null : accessible.get(RANDOM.nextInt(accessible.size()));
    }

    private String pathToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            sb.append(path.get(i).getId());
            if (i < path.size() - 1) sb.append(" → ");
        }
        return sb.toString();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getId() { return id; }
    public Node getPosition() { return position; }
    public Node getDestination() { return destination; }
    public Node getDepartureNode() { return departureNode; }
    public Node getTargetNode() { return targetNode; }
    public int getSpeed() { return speed; }
    public double getDensityTolerance() { return densityTolerance; }
    public Behavior getBehavior() { return behavior; }
    public PsychologicalState getPsychologicalState() { return psychologicalState; }
    public DestinationMode getDestinationMode() { return destinationMode; }
    public MovementState getMovementState() { return movementState; }
    public List<Node> getPath() { return path; }
    public double getDisplayX() { return displayX; }
    public double getDisplayY() { return displayY; }
    public double getEdgeProgress() { return edgeProgress; }
    public ArrivalBehavior getArrivalBehavior() { return arrivalBehavior; }
    public boolean isMarkedForDeletion() { return markedForDeletion; }
    public int getAgentsPassed() { return agentsPassed; }

    public void setSpeed(int v) { this.speed = Math.max(1, v); }
    public void setDensityTolerance(double t) { this.densityTolerance = Math.min(1.0, Math.max(0.0, t)); }
    public void setBehavior(Behavior c) { this.behavior = c; this.initialBehavior = c; }
    public void setDestinationMode(DestinationMode m) { this.destinationMode = m; }
    public void setDestination(Node d) { this.destination = d; }
    public void setOtherAgents(List<Agent> a) { this.otherAgents = a; }
    public void setMovementState(MovementState e) { this.movementState = e; }
    public void setArrivalBehavior(ArrivalBehavior ab) { this.arrivalBehavior = ab; }

    public void setPsychologicalState(PsychologicalState state) {
        this.psychologicalState        = state;
        this.initialPsychologicalState = state;
        this.autoPanicked              = false;
        if (state == PsychologicalState.PANIC) this.behavior = Behavior.PRIORITY;
    }

    // ── Deprecated French-named aliases ──────────────────────────────────────

    /** @deprecated Use {@link #reset()} */
    @Deprecated public void reinitialiser() { reset(); }
    /** @deprecated Use {@link #initialize()} */
    @Deprecated public void initialiser() { initialize(); }
    /** @deprecated Use {@link #move()} */
    @Deprecated public void deplacer() { move(); }
    /** @deprecated Use {@link #animateMovement(double)} */
    @Deprecated public boolean animerDeplacement(double v) { return animateMovement(v); }
    /** @deprecated Use {@link #getAnimationSpeed()} */
    @Deprecated public double getVitesseAnimation() { return getAnimationSpeed(); }
    /** @deprecated Use {@link #syncDisplayWithNode()} */
    @Deprecated public void syncAffichageAvecNoeud() { syncDisplayWithNode(); }
    /** @deprecated Use {@link #recalculatePath()} */
    @Deprecated public void recalculerChemin() { recalculatePath(); }
    /** @deprecated Use {@link #teleportTo(Node)} */
    @Deprecated public void teleporterVers(Node n) { teleportTo(n); }
    /** @deprecated Use {@link #inducePanic()} */
    @Deprecated public void induirePanique() { inducePanic(); }
    /** @deprecated Use {@link #induceMadness()} */
    @Deprecated public void induireFolie() { induceMadness(); }
    /** @deprecated Use {@link #returnToDepartureNode()} */
    @Deprecated public void retournerVersNoeudDepart() { returnToDepartureNode(); }
    /** @deprecated Use {@link #getSpeed()} */
    @Deprecated public int getVitesse() { return speed; }
    /** @deprecated Use {@link #setSpeed(int)} */
    @Deprecated public void setVitesse(int v) { setSpeed(v); }
    /** @deprecated Use {@link #getDensityTolerance()} */
    @Deprecated public double getToleranceDensite() { return densityTolerance; }
    /** @deprecated Use {@link #getBehavior()} */
    @Deprecated public Behavior getComportement() { return behavior; }
    /** @deprecated Use {@link #getPsychologicalState()} */
    @Deprecated public PsychologicalState getEtatPsychologique() { return psychologicalState; }
    /** @deprecated Use {@link #getDestinationMode()} */
    @Deprecated public DestinationMode getModeDestination() { return destinationMode; }
    /** @deprecated Use {@link #getMovementState()} */
    @Deprecated public MovementState getEtatDeplacement() { return movementState; }
    /** @deprecated Use {@link #getPath()} */
    @Deprecated public List<Node> getChemin() { return path; }
    /** @deprecated Use {@link #getTargetNode()} */
    @Deprecated public Node getNoeudCible() { return targetNode; }
    /** @deprecated Use {@link #getDepartureNode()} */
    @Deprecated public Node getNoeudDepart() { return departureNode; }
    /** @deprecated Use {@link #getEdgeProgress()} */
    @Deprecated public double getProgressArete() { return edgeProgress; }
    /** @deprecated Use {@link #setOtherAgents(List)} */
    @Deprecated public void setAutresAgents(List<Agent> a) { setOtherAgents(a); }
    /** @deprecated Use {@link #setMovementState(MovementState)} */
    @Deprecated public void setEtatDeplacement(MovementState e) { setMovementState(e); }
    /** @deprecated Use {@link #setPsychologicalState(PsychologicalState)} */
    @Deprecated public void setEtatPsychologique(PsychologicalState s) { setPsychologicalState(s); }

    @Override
    public String toString() {
        return "Agent{id='" + id + "', pos='" + position.getId()
             + "', dest='" + (destination != null ? destination.getId() : "—")
             + "', state=" + movementState + "}";
    }
}
