package evacuation.simulation;

import evacuation.agent.Agent;
import evacuation.graph.DangerZone;
import evacuation.graph.Edge;
import evacuation.graph.Exit;
import evacuation.graph.Graph;
import evacuation.graph.Node;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * The core simulation engine that drives agent movement and danger zone propagation.
 * Manages the graph, agents, and tick-based simulation loop.
 */
public class SimulationEngine implements Serializable {

    private static final long serialVersionUID = 1L;

    private Graph graph;
    private List<Agent> agents;
    private int tick;
    private boolean running;
    private int totalArrivedHistoric;

    private static final Random RANDOM = new Random();

    /**
     * Constructs a SimulationEngine for the given graph.
     *
     * @param graph the evacuation graph
     */
    public SimulationEngine(Graph graph) {
        this.graph                = graph;
        this.agents               = new ArrayList<>();
        this.tick                 = 0;
        this.running              = false;
        this.totalArrivedHistoric = 0;
    }

    /**
     * Adds an agent to the simulation.
     *
     * @param agent the agent to add
     */
    public void addAgent(Agent agent) {
        agents.add(agent);
        agent.setOtherAgents(agents);
    }

    /**
     * Removes an agent from the simulation.
     *
     * @param agent the agent to remove
     */
    public void removeAgent(Agent agent) {
        agents.remove(agent);
        System.out.println("[Simulation] Agent " + agent.getId() + " removed.");
    }

    /**
     * Adds a specified number of random agents with randomized properties within the given ranges.
     *
     * @param count          number of agents to add
     * @param minSpeed       minimum speed value
     * @param maxSpeed       maximum speed value
     * @param minTolerance   minimum density tolerance
     * @param maxTolerance   maximum density tolerance
     */
    public void addRandomAgents(int count, int minSpeed, int maxSpeed,
                                 double minTolerance, double maxTolerance) {
        List<Node> availableNodes = graph.getNodes().stream()
            .filter(n -> !n.isBlocked() && !(n instanceof Exit) && !(n instanceof DangerZone))
            .collect(Collectors.toList());
        List<Node> exits = graph.getNodes().stream()
            .filter(n -> n instanceof Exit && !n.isBlocked() && ((Exit) n).isOpen())
            .collect(Collectors.toList());

        if (availableNodes.isEmpty() || exits.isEmpty()) {
            System.out.println("[Simulation] Cannot add random agents: no nodes or exits available.");
            return;
        }

        Agent.Behavior[] behaviors = Agent.Behavior.values();
        Agent.PsychologicalState[] psychStates = {
            Agent.PsychologicalState.CALM,
            Agent.PsychologicalState.PANIC
        };
        Agent.DestinationMode[] modes = Agent.DestinationMode.values();

        for (int i = 0; i < count; i++) {
            Node startNode = availableNodes.get(RANDOM.nextInt(availableNodes.size()));
            Node dest      = exits.get(RANDOM.nextInt(exits.size()));
            int spd = minSpeed + (maxSpeed > minSpeed ? RANDOM.nextInt(maxSpeed - minSpeed + 1) : 0);
            double tol = minTolerance + RANDOM.nextDouble() * (maxTolerance - minTolerance);
            Agent.Behavior beh  = behaviors[RANDOM.nextInt(behaviors.length)];
            Agent.PsychologicalState psy = psychStates[RANDOM.nextInt(psychStates.length)];

            String agId = graph.generateUniqueId("RG");
            Agent ag = new Agent(agId, startNode, dest, graph, spd, tol, beh, psy,
                    Agent.DestinationMode.FIXED);
            addAgent(ag);
            if (running) ag.initialize();
            System.out.println("[Simulation] Random agent " + agId + " added at " + startNode.getId());
        }
    }

    /**
     * Initializes (or reinitializes) the simulation.
     * Resets the graph state (undo previous propagation), resets all agents,
     * resets statistics, and initializes agents for movement.
     */
    public void initialize() {
        // Reset danger zone propagation and unblock nodes/edges from previous run
        for (Node n : graph.getNodes()) {
            if (n instanceof DangerZone) {
                ((DangerZone) n).reset();
            } else if (n.isBlocked()) {
                n.unblock();
            }
        }
        for (Edge e : graph.getEdges()) {
            if (!e.isAvailable()) {
                e.unblock();
            }
        }

        resetStats();

        tick                  = 0;
        running               = true;
        totalArrivedHistoric  = 0;
        System.out.println("=== Simulation initialized (" + agents.size() + " agent(s)) ===");
        for (Agent agent : agents) {
            agent.setOtherAgents(agents);
            agent.reset();
            agent.initialize();
        }
    }

    /**
     * Advances the simulation by one tick.
     * Propagates danger zones, moves agents, removes deleted agents, and checks for completion.
     */
    public void tick() {
        if (!running) return;
        tick++;
        System.out.println("\n--- Tick " + tick + " ---");

        // Propagate danger zones
        boolean graphChanged = false;
        for (Node n : graph.getNodes()) {
            if (n instanceof DangerZone) {
                if (((DangerZone) n).propagate(graph)) {
                    graphChanged = true;
                }
            }
        }
        if (graphChanged) {
            notifyGraphChanged();
        }

        for (Agent agent : agents) agent.move();

        // Remove agents marked for deletion
        agents.removeIf(a -> {
            if (a.isMarkedForDeletion()) {
                System.out.println("[Simulation] Agent " + a.getId() + " deleted (arrival behavior).");
                return true;
            }
            return false;
        });

        totalArrivedHistoric = (int) agents.stream()
            .filter(a -> a.getMovementState() == Agent.MovementState.ARRIVED).count();

        if (isFinished()) {
            running = false;
            System.out.println("\n=== Simulation finished in " + tick + " tick(s) ===");
        }
    }

    /**
     * Triggers a path recalculation for all non-arrived, non-waiting agents.
     * Call this when the graph topology changes (danger zone spread, node/edge removed, etc.).
     */
    public void notifyGraphChanged() {
        for (Agent a : agents) {
            if (a.getMovementState() != Agent.MovementState.ARRIVED
                    && a.getMovementState() != Agent.MovementState.WAITING) {
                a.recalculatePath();
            }
        }
    }

    /**
     * Runs the full simulation synchronously from initialization to completion.
     */
    public void runSimulation() {
        initialize();
        while (running) tick();
    }

    /**
     * Returns true if all agents have reached a terminal state (ARRIVED or BLOCKED).
     *
     * @return true if simulation is finished
     */
    public boolean isFinished() {
        for (Agent agent : agents) {
            Agent.MovementState state = agent.getMovementState();
            if (state == Agent.MovementState.MOVING || state == Agent.MovementState.WAITING)
                return false;
        }
        return !agents.isEmpty();
    }

    /**
     * Handles the removal of a node during an active simulation.
     * Affected agents are redirected, panicked, or teleported as appropriate.
     *
     * @param removedNode the node being removed
     */
    public void onNodeRemoved(Node removedNode) {
        for (Agent agent : new ArrayList<>(agents)) {
            if (agent.getMovementState() == Agent.MovementState.ARRIVED) continue;

            if (removedNode.equals(agent.getTargetNode())) {
                agent.returnToDepartureNode();
                System.out.println("[Simulation] Agent " + agent.getId()
                        + " returning (target node removed)");

            } else if (agent.getPosition().equals(removedNode)) {
                Node closest = graph.getClosestNode(removedNode, removedNode);
                if (closest != null) {
                    agent.induceMadness();
                    agent.teleportTo(closest);
                    System.out.println("[Simulation] Agent " + agent.getId()
                            + " (MADNESS) teleported → " + closest.getId());
                }

            } else if (removedNode.equals(agent.getDepartureNode())) {
                agent.inducePanic();
                System.out.println("[Simulation] Agent " + agent.getId()
                        + " PANIC (departure node removed)");

            } else if (removedNode.equals(agent.getDestination())) {
                agent.recalculatePath();
            }
        }
    }

    /**
     * Finds the nearest available exit from a given node using Euclidean distance.
     *
     * @param from the reference node
     * @return the nearest open exit, or null if none available
     */
    public Node findAvailableExit(Node from) {
        Node best = null;
        double minDist = Double.MAX_VALUE;
        for (Node n : graph.getNodes()) {
            if (n instanceof Exit && !n.isBlocked()) {
                double d = Math.hypot(n.getX() - from.getX(), n.getY() - from.getY());
                if (d < minDist) { minDist = d; best = n; }
            }
        }
        return best;
    }

    /**
     * Redistributes all non-arrived agents across available exits using load balancing.
     */
    public void distributeAgentsToExits() {
        List<Exit> exits = graph.getNodes().stream()
            .filter(n -> n instanceof Exit && !n.isBlocked())
            .map(n -> (Exit) n).collect(Collectors.toList());
        if (exits.isEmpty()) { System.out.println("[Simulation] No exits available."); return; }

        int[] load = new int[exits.size()];
        for (Agent a : agents) {
            if (a.getMovementState() == Agent.MovementState.ARRIVED) continue;
            for (int i = 0; i < exits.size(); i++)
                if (exits.get(i).equals(a.getDestination())) { load[i]++; break; }
        }
        for (Agent a : agents) {
            if (a.getMovementState() == Agent.MovementState.ARRIVED) continue;
            int min = Integer.MAX_VALUE, idx = 0;
            for (int i = 0; i < exits.size(); i++) if (load[i] < min) { min = load[i]; idx = i; }
            a.setDestination(exits.get(idx));
            a.recalculatePath();
            load[idx]++;
        }
        System.out.println("[Simulation] Agents distributed across " + exits.size() + " exit(s).");
    }

    /**
     * Resets all node and edge statistics (agent passage counts).
     */
    public void resetStats() {
        graph.resetStats();
    }

    /**
     * Returns movement state counts as an array: [waiting, moving, arrived, blocked].
     *
     * @return int array with counts for each movement state
     */
    public int[] getMovementStats() {
        int waiting = 0, moving = 0, arrived = 0, blocked = 0;
        for (Agent a : agents) switch (a.getMovementState()) {
            case WAITING  -> waiting++;
            case MOVING   -> moving++;
            case ARRIVED  -> arrived++;
            case BLOCKED  -> blocked++;
        }
        return new int[]{waiting, moving, arrived, blocked};
    }

    /**
     * Returns psychological state counts as an array: [calm, panic, madness].
     *
     * @return int array with counts for each psychological state
     */
    public int[] getPsychologicalStats() {
        int calm = 0, panic = 0, madness = 0;
        for (Agent a : agents) switch (a.getPsychologicalState()) {
            case CALM    -> calm++;
            case PANIC   -> panic++;
            case MADNESS -> madness++;
        }
        return new int[]{calm, panic, madness};
    }

    /**
     * Returns agent density per node (index corresponds to graph.getNodes() order).
     *
     * @return int array of agent counts per node
     */
    public int[] getDensityPerNode() {
        int[] density = new int[graph.getNodes().size()];
        for (Agent a : agents) {
            if (a.getMovementState() == Agent.MovementState.ARRIVED) continue;
            int idx = graph.getNodes().indexOf(a.getPosition());
            if (idx >= 0) density[idx]++;
        }
        return density;
    }

    /**
     * Returns the maximum agent density across all nodes.
     *
     * @return maximum density value (minimum 1)
     */
    public int getMaxDensity() {
        int[] d = getDensityPerNode(); int max = 1;
        for (int v : d) if (v > max) max = v;
        return max;
    }

    /**
     * Returns the maximum agent count on any edge.
     *
     * @return maximum edge congestion (minimum 1)
     */
    public int getMaxEdgeCongestion() {
        int max = 1;
        for (Edge e : graph.getEdges()) max = Math.max(max, e.getAgentsInTransit());
        return max;
    }

    /**
     * Saves the simulation state to a file.
     *
     * @param f the output file
     * @throws IOException if serialization fails
     */
    public void save(File f) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
            oos.writeObject(this);
        }
    }

    /**
     * Loads a simulation state from a file.
     *
     * @param f the input file
     * @return the loaded SimulationEngine
     * @throws IOException            if deserialization fails
     * @throws ClassNotFoundException if the class cannot be found
     */
    public static SimulationEngine load(File f) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            SimulationEngine e = (SimulationEngine) ois.readObject();
            for (Agent a : e.agents) a.setOtherAgents(e.agents);
            return e;
        }
    }

    public int getTick() { return tick; }
    public boolean isRunning() { return running; }
    public List<Agent> getAgents() { return agents; }
    public Graph getGraph() { return graph; }
    public void setGraph(Graph g) { this.graph = g; }

    // ── Deprecated French-named aliases ──────────────────────────────────────
    /** @deprecated Use {@link #addAgent(Agent)} */
    @Deprecated public void ajouterAgent(Agent a) { addAgent(a); }
    /** @deprecated Use {@link #removeAgent(Agent)} */
    @Deprecated public void supprimerAgent(Agent a) { removeAgent(a); }
    /** @deprecated Use {@link #initialize()} */
    @Deprecated public void initialiser() { initialize(); }
    /** @deprecated Use {@link #tick()} */
    @Deprecated public void tick_deprecated() { tick(); }
    /** @deprecated Use {@link #notifyGraphChanged()} */
    @Deprecated public void notifierChangementGraphe() { notifyGraphChanged(); }
    /** @deprecated Use {@link #runSimulation()} */
    @Deprecated public void lancerSimulation() { runSimulation(); }
    /** @deprecated Use {@link #isFinished()} */
    @Deprecated public boolean estTerminee() { return isFinished(); }
    /** @deprecated Use {@link #onNodeRemoved(Node)} */
    @Deprecated public void onNoeudSupprime(Node n) { onNodeRemoved(n); }
    /** @deprecated Use {@link #findAvailableExit(Node)} */
    @Deprecated public Node trouverSortieDisponible(Node from) { return findAvailableExit(from); }
    /** @deprecated Use {@link #distributeAgentsToExits()} */
    @Deprecated public void repartirAgentsSurSorties() { distributeAgentsToExits(); }
    /** @deprecated Use {@link #getMovementStats()} */
    @Deprecated public int[] getStatsDeplacement() { return getMovementStats(); }
    /** @deprecated Use {@link #getPsychologicalStats()} */
    @Deprecated public int[] getStatsPsychologiques() { return getPsychologicalStats(); }
    /** @deprecated Use {@link #getDensityPerNode()} */
    @Deprecated public int[] getDensiteParNoeud() { return getDensityPerNode(); }
    /** @deprecated Use {@link #getMaxDensity()} */
    @Deprecated public int getDensiteMax() { return getMaxDensity(); }
    /** @deprecated Use {@link #getMaxEdgeCongestion()} */
    @Deprecated public int getCongestionMaxArete() { return getMaxEdgeCongestion(); }
    /** @deprecated Use {@link #save(File)} */
    @Deprecated public void sauvegarder(File f) throws IOException { save(f); }
    /** @deprecated Use {@link #load(File)} */
    @Deprecated public static SimulationEngine charger(File f) throws IOException, ClassNotFoundException {
        return load(f);
    }
    /** @deprecated Use {@link #isRunning()} */
    @Deprecated public boolean isEnCours() { return running; }
}
