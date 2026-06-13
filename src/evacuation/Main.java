package evacuation;

import evacuation.agent.Agent;
import evacuation.graph.DangerZone;
import evacuation.graph.Edge;
import evacuation.graph.Exit;
import evacuation.graph.Graph;
import evacuation.graph.Node;
import evacuation.simulation.SimulationEngine;
import evacuation.ui.MainFrame;

import javax.swing.*;

/**
 * Application entry point for the evacuation simulation.
 * Creates a default scenario and launches either the CLI or GUI mode.
 */
public class Main {

    /**
     * Main entry point.
     * Pass "cli" as the first argument to run in console mode.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("cli")) {
            createSimulation().runSimulation();
        } else {
            SimulationEngine engine = createSimulation();
            SwingUtilities.invokeLater(() -> new MainFrame(engine));
        }
    }

    /**
     * Creates and returns a default simulation with a pre-built graph and agents.
     *
     * @return the initialized SimulationEngine ready to run
     */
    private static SimulationEngine createSimulation() {
        Graph graph = new Graph();

        // ── Nodes — 4×3 grid + cross connections ──────────────────────────────
        Node a = new Node("A", 1.0, 0.5);
        Node b = new Node("B", 3.0, 0.5);
        Node c = new Node("C", 5.0, 0.5);
        Node d = new Node("D", 7.0, 0.5);

        Node e = new Node("E", 1.0, 2.2);
        Node f = new Node("F", 3.0, 2.2);
        Node g = new Node("G", 5.0, 2.2);
        Node h = new Node("H", 7.0, 2.2);

        Node ii = new Node("I", 1.5, 3.8);
        Node j  = new Node("J", 4.0, 3.8);
        Node k  = new Node("K", 6.5, 3.8);

        // ── Exits ─────────────────────────────────────────────────────────────
        Exit s1 = new Exit("S1", 0.0, 1.3, 20);   // west
        Exit s2 = new Exit("S2", 8.2, 1.3, 20);   // east
        Exit s3 = new Exit("S3", 0.0, 3.8, 20);   // south-west
        Exit s4 = new Exit("S4", 8.2, 4.3, 20);   // south-east

        // ── Danger zones ──────────────────────────────────────────────────────
        DangerZone z1 = new DangerZone("Z1", 5.0, 4.9, DangerZone.DangerType.FIRE, 8);
        DangerZone z2 = new DangerZone("Z2", 2.5, 4.9, DangerZone.DangerType.FLOOD, 6);

        for (Node n : new Node[]{a,b,c,d, e,f,g,h, ii,j,k, s1,s2,s3,s4, z1,z2})
            graph.addNode(n);

        // ── Horizontal edges ──────────────────────────────────────────────────
        graph.addEdge(new Edge(a, b, 2));
        graph.addEdge(new Edge(b, c, 2));
        graph.addEdge(new Edge(c, d, 2));

        graph.addEdge(new Edge(e, f, 2));
        graph.addEdge(new Edge(f, g, 2));
        graph.addEdge(new Edge(g, h, 2));

        graph.addEdge(new Edge(ii, j, 3));
        graph.addEdge(new Edge(j,  k, 3));

        // ── Vertical edges ────────────────────────────────────────────────────
        graph.addEdge(new Edge(a, e, 2));
        graph.addEdge(new Edge(b, f, 2));
        graph.addEdge(new Edge(c, g, 2));
        graph.addEdge(new Edge(d, h, 2));

        graph.addEdge(new Edge(e, ii, 2));
        graph.addEdge(new Edge(f,  j, 2));
        graph.addEdge(new Edge(g,  k, 2));

        // ── Diagonal edges ────────────────────────────────────────────────────
        graph.addEdge(new Edge(a, f, 3));
        graph.addEdge(new Edge(b, g, 3));
        graph.addEdge(new Edge(c, h, 3));
        graph.addEdge(new Edge(e, j, 3));
        graph.addEdge(new Edge(f, k, 4));
        graph.addEdge(new Edge(h, k, 3));

        // ── Connections to exits ──────────────────────────────────────────────
        graph.addEdge(new Edge(a,  s1, 1));
        graph.addEdge(new Edge(e,  s1, 1));
        graph.addEdge(new Edge(d,  s2, 1));
        graph.addEdge(new Edge(h,  s2, 1));
        graph.addEdge(new Edge(e,  s3, 2));
        graph.addEdge(new Edge(ii, s3, 1));
        graph.addEdge(new Edge(h,  s4, 2));
        graph.addEdge(new Edge(k,  s4, 1));

        // ── Connections to danger zones ───────────────────────────────────────
        graph.addEdge(new Edge(j,  z1, 2));
        graph.addEdge(new Edge(k,  z1, 1));
        graph.addEdge(new Edge(ii, z2, 2));
        graph.addEdge(new Edge(j,  z2, 1));

        // ── Agents ───────────────────────────────────────────────────────────
        SimulationEngine engine = new SimulationEngine(graph);

        // AG1: CALM, A → S4, long diagonal route
        engine.addAgent(new Agent("AG1", a, s4, graph, 1, 1.0,
                Agent.Behavior.YIELD, Agent.PsychologicalState.CALM,
                Agent.DestinationMode.FIXED));

        // AG2: PANIC, D → S3, crosses the whole graph
        engine.addAgent(new Agent("AG2", d, s3, graph, 1, 0.5,
                Agent.Behavior.PRIORITY, Agent.PsychologicalState.PANIC,
                Agent.DestinationMode.FIXED));

        // AG3: MADNESS, K → S1, random movement
        engine.addAgent(new Agent("AG3", k, s1, graph, 1, 0.8,
                Agent.Behavior.YIELD, Agent.PsychologicalState.MADNESS,
                Agent.DestinationMode.FIXED));

        // AG4: CALM, B → S4, short path
        engine.addAgent(new Agent("AG4", b, s4, graph, 1, 1.0,
                Agent.Behavior.YIELD, Agent.PsychologicalState.CALM,
                Agent.DestinationMode.FIXED));

        // AG5: PANIC, G → S1, crossing path
        engine.addAgent(new Agent("AG5", g, s1, graph, 1, 0.6,
                Agent.Behavior.PRIORITY, Agent.PsychologicalState.PANIC,
                Agent.DestinationMode.FIXED));

        return engine;
    }
}
