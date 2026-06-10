package evacuation.simulation;

import evacuation.agent.Agent;
import evacuation.graph.Graph;
import evacuation.graph.Node;
import evacuation.graph.Sortie;
import evacuation.graph.ZoneDanger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SimulationEngine implements Serializable {

    private static final long serialVersionUID = 1L;

    private Graph graph;
    private List<Agent> agents;
    private int tick;
    private boolean enCours;
    private int totalArrivesHistorique;

    public SimulationEngine(Graph graph) {
        this.graph  = graph;
        this.agents = new ArrayList<>();
        this.tick   = 0;
        this.enCours = false;
        this.totalArrivesHistorique = 0;
    }

    public void ajouterAgent(Agent agent) {
        agents.add(agent);
        agent.setAutresAgents(agents);
    }

    public void supprimerAgent(Agent agent) {
        agents.remove(agent);
        System.out.println("[Simulation] Agent " + agent.getId() + " retiré.");
    }

    public void initialiser() {
        tick   = 0;
        enCours = true;
        totalArrivesHistorique = 0;
        System.out.println("=== Simulation initialisée (" + agents.size() + " agent(s)) ===");
        for (Agent agent : agents) {
            agent.setAutresAgents(agents);
            agent.initialiser();
        }
    }

    public void tick() {
        if (!enCours) return;
        tick++;
        System.out.println("\n--- Tick " + tick + " ---");

        // Propagation des dangers
        for (Node n : graph.getNodes()) {
            if (n instanceof ZoneDanger) {
                ((ZoneDanger) n).propager(graph);
            }
        }

        for (Agent agent : agents) agent.deplacer();

        totalArrivesHistorique = (int) agents.stream()
            .filter(a -> a.getEtatDeplacement() == Agent.EtatDeplacement.ARRIVE).count();

        if (estTerminee()) {
            enCours = false;
            System.out.println("\n=== Simulation terminée en " + tick + " tick(s) ===");
        }
    }

    public void lancerSimulation() {
        initialiser();
        while (enCours) tick();
    }

    public boolean estTerminee() {
        for (Agent agent : agents) {
            Agent.EtatDeplacement etat = agent.getEtatDeplacement();
            if (etat == Agent.EtatDeplacement.EN_MOUVEMENT || etat == Agent.EtatDeplacement.EN_ATTENTE)
                return false;
        }
        return !agents.isEmpty();
    }

    public void onNoeudSupprime(Node noeudSupprime) {
        for (Agent agent : new ArrayList<>(agents)) {
            if (agent.getEtatDeplacement() == Agent.EtatDeplacement.ARRIVE) continue;
            if (agent.getPosition().equals(noeudSupprime)) {
                Node proche = graph.getNoeudLePlusProche(noeudSupprime, null);
                if (proche != null) {
                    agent.teleporterVers(proche);
                    System.out.println("[Simulation] Agent " + agent.getId() + " téléporté → " + proche.getId());
                }
            }
            if (noeudSupprime.equals(agent.getDestination())) {
                Node nouvelleDest = trouverSortieDisponible(agent.getPosition());
                if (nouvelleDest != null) {
                    agent.setDestination(nouvelleDest);
                    agent.recalculerChemin();
                }
            }
        }
    }

    public Node trouverSortieDisponible(Node depuis) {
        Node best = null;
        double minDist = Double.MAX_VALUE;
        for (Node n : graph.getNodes()) {
            if (n instanceof Sortie && !n.isBloque()) {
                double d = Math.hypot(n.getX() - depuis.getX(), n.getY() - depuis.getY());
                if (d < minDist) { minDist = d; best = n; }
            }
        }
        return best;
    }

    public void repartirAgentsSurSorties() {
        List<Sortie> sorties = graph.getNodes().stream()
            .filter(n -> n instanceof Sortie && !n.isBloque())
            .map(n -> (Sortie) n).collect(Collectors.toList());
        if (sorties.isEmpty()) { System.out.println("[Simulation] Aucune sortie."); return; }

        int[] charge = new int[sorties.size()];
        for (Agent a : agents) {
            if (a.getEtatDeplacement() == Agent.EtatDeplacement.ARRIVE) continue;
            for (int i = 0; i < sorties.size(); i++)
                if (sorties.get(i).equals(a.getDestination())) { charge[i]++; break; }
        }
        for (Agent a : agents) {
            if (a.getEtatDeplacement() == Agent.EtatDeplacement.ARRIVE) continue;
            int min = Integer.MAX_VALUE, idx = 0;
            for (int i = 0; i < sorties.size(); i++) if (charge[i] < min) { min = charge[i]; idx = i; }
            a.setDestination(sorties.get(idx));
            a.recalculerChemin();
            charge[idx]++;
        }
        System.out.println("[Simulation] Répartition sur " + sorties.size() + " sortie(s) effectuée.");
    }

    public int[] getStatsDeplacement() {
        int enAttente = 0, enMouvement = 0, arrive = 0, bloque = 0;
        for (Agent a : agents) switch (a.getEtatDeplacement()) {
            case EN_ATTENTE   -> enAttente++;
            case EN_MOUVEMENT -> enMouvement++;
            case ARRIVE       -> arrive++;
            case BLOQUE       -> bloque++;
        }
        return new int[]{enAttente, enMouvement, arrive, bloque};
    }

    public int[] getStatsPsychologiques() {
        int calme = 0, panique = 0, folie = 0;
        for (Agent a : agents) switch (a.getEtatPsychologique()) {
            case CALME   -> calme++;
            case PANIQUE -> panique++;
            case FOLIE   -> folie++;
        }
        return new int[]{calme, panique, folie};
    }

    public int[] getDensiteParNoeud() {
        int[] densite = new int[graph.getNodes().size()];
        for (Agent a : agents) {
            if (a.getEtatDeplacement() == Agent.EtatDeplacement.ARRIVE) continue;
            int idx = graph.getNodes().indexOf(a.getPosition());
            if (idx >= 0) densite[idx]++;
        }
        return densite;
    }

    public int getDensiteMax() {
        int[] d = getDensiteParNoeud(); int max = 1;
        for (int v : d) if (v > max) max = v; return max;
    }

    public int getCongestionMaxArete() {
        int max = 1;
        for (evacuation.graph.Edge e : graph.getEdges()) max = Math.max(max, e.getAgentsEnTransit());
        return max;
    }

    public void sauvegarder(File f) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) { oos.writeObject(this); }
    }

    public static SimulationEngine charger(File f) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            SimulationEngine e = (SimulationEngine) ois.readObject();
            for (Agent a : e.agents) a.setAutresAgents(e.agents);
            return e;
        }
    }

    public int         getTick()       { return tick; }
    public boolean     isEnCours()     { return enCours; }
    public List<Agent> getAgents()     { return agents; }
    public Graph       getGraph()      { return graph; }
    public void        setGraph(Graph g){ this.graph = g; }
}
