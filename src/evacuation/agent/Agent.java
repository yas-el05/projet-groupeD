package evacuation.agent;

import evacuation.graph.Graph;
import evacuation.graph.Node;
import evacuation.graph.Sortie;
import evacuation.graph.ZoneDanger;
import evacuation.routing.DijkstraPathFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Un agent se déplace de nœud en nœud.
 *
 * Architecture mouvement :
 *  - position      : nœud LOGIQUE actuel (où l'agent "est" pour le routage)
 *  - noeudCible    : prochain nœud LOGIQUE (null quand on est sur un nœud)
 *  - progressArete : [0.0 – 1.0] avancement sur l'arête en cours
 *  - displayX/Y    : coordonnées interpolées utilisées UNIQUEMENT pour le rendu
 *
 * Un tick logique fait avancer l'agent d'un nœud (position ← noeudCible).
 * Entre deux ticks, l'animation (appelée par le timer de rendu) fait
 * progresser progressArete de 0 → 1, ce qui anime le déplacement visuel.
 */
public class Agent {

    public enum EtatDeplacement   { EN_ATTENTE, EN_MOUVEMENT, ARRIVE, BLOQUE }
    public enum EtatPsychologique { CALME, PANIQUE, FOLIE }
    public enum Comportement      { LAISSE_PASSER, PRIORITAIRE, SUIT_AGENT }
    public enum ModeDestination   { FIXE, ALEATOIRE, FUIT_DESTINATION, VERS_DENSE, FUIT_DENSITE }

    private final String id;

    // ── Position logique ───────────────────────────────────────────────────────
    private Node position;      // nœud où l'agent SE TROUVE (logiquement)
    private Node noeudDepart;   // nœud d'où il vient pour l'animation en cours
    private Node noeudCible;    // prochain nœud vers lequel il se dirige (null = sur nœud)
    private double progressArete; // 0.0 (sur noeudDepart) → 1.0 (sur noeudCible/position)

    // ── Position affichage ─────────────────────────────────────────────────────
    private double displayX, displayY;

    private Node destination;
    private final Graph graph;

    private int vitesse;
    private double toleranceDensite;
    private Comportement comportement;
    private EtatPsychologique etatPsychologique;
    private ModeDestination modeDestination;
    private EtatDeplacement etatDeplacement;

    private DijkstraPathFinder pathFinder;
    private List<Node> chemin;
    private int indexChemin;
    private int ticksAttente;

    private static final Random RANDOM = new Random();
    private List<Agent> autresAgents = new ArrayList<>();

    public Agent(String id, Node positionInitiale, Node destination, Graph graph,
                 int vitesse, double toleranceDensite,
                 Comportement comportement, EtatPsychologique etatPsychologique,
                 ModeDestination modeDestination) {
        this.id               = id;
        this.position         = positionInitiale;
        this.destination      = destination;
        this.graph            = graph;
        this.vitesse          = Math.max(1, vitesse);
        this.toleranceDensite = Math.min(1.0, Math.max(0.0, toleranceDensite));
        this.comportement     = comportement;
        this.etatPsychologique = etatPsychologique;
        this.modeDestination  = modeDestination;
        this.etatDeplacement  = EtatDeplacement.EN_ATTENTE;
        this.pathFinder       = new DijkstraPathFinder(graph);
        this.chemin           = new ArrayList<>();
        this.indexChemin      = 0;
        this.ticksAttente     = 0;
        this.noeudDepart      = positionInitiale;
        this.noeudCible       = null;
        this.progressArete    = 1.0;
        this.displayX         = positionInitiale.getX();
        this.displayY         = positionInitiale.getY();
    }

    public Agent(String id, Node positionInitiale, Node destination, Graph graph) {
        this(id, positionInitiale, destination, graph, 1, 1.0,
             Comportement.LAISSE_PASSER, EtatPsychologique.CALME, ModeDestination.FIXE);
    }

    // ── Initialisation ─────────────────────────────────────────────────────────
    public void initialiser() {
        if (destination == null || (destination.isBloque() && !(destination instanceof Sortie))) {
            etatDeplacement = EtatDeplacement.BLOQUE;
            return;
        }
        chemin = pathFinder.calculerChemin(position, destination);
        if (chemin.isEmpty()) {
            etatDeplacement = EtatDeplacement.BLOQUE;
        } else {
            indexChemin    = 1;
            etatDeplacement = EtatDeplacement.EN_MOUVEMENT;
            System.out.println("[Agent " + id + "] Chemin : " + cheminToString());
        }
    }

    // ── Tick logique (appelé par SimulationEngine.tick()) ─────────────────────
    /**
     * Fait avancer l'agent d'un nœud logiquement.
     * NE modifie PAS displayX/Y — c'est le rôle de animerDeplacement().
     */
    public void deplacer() {
        if (etatDeplacement == EtatDeplacement.ARRIVE
                || etatDeplacement == EtatDeplacement.EN_ATTENTE) return;

        if (ticksAttente > 0) { ticksAttente--; return; }

        if (etatDeplacement == EtatDeplacement.BLOQUE) { recalculerChemin(); return; }

        if (etatPsychologique == EtatPsychologique.FOLIE) { deplacerAleatoirement(); return; }

        double facteurRalent = getFacteurRalentissementVoisin();
        int pas = vitesseEffective();
        for (int i = 0; i < pas; i++) {
            if (!avancerUnNoeud(facteurRalent)) break;
            if (etatDeplacement == EtatDeplacement.ARRIVE) break;
        }
    }

    /**
     * Animation visuelle — à appeler par le timer de rendu (60 fps).
     * Interpole displayX/Y entre noeudDepart et position courante.
     * Retourne true si l'animation est encore en cours.
     */
    public boolean animerDeplacement(double vitesseAnim) {
        // Si on est sur un nœud (pas en transit), affichage = position du nœud
        if (noeudCible == null || progressArete >= 1.0) {
            displayX = position.getX();
            displayY = position.getY();
            return false;
        }
        // Interpolation de noeudDepart → noeudCible
        progressArete = Math.min(1.0, progressArete + vitesseAnim);
        double sx = noeudDepart.getX(), sy = noeudDepart.getY();
        double ex = noeudCible.getX(),  ey = noeudCible.getY();
        displayX = sx + (ex - sx) * progressArete;
        displayY = sy + (ey - sy) * progressArete;
        // Quand l'animation est terminée, on "snappe" sur la cible
        if (progressArete >= 1.0) {
            displayX = ex;
            displayY = ey;
            noeudCible = null;
        }
        return progressArete < 1.0;
    }

    /**
     * Synchronise displayX/Y avec la position LOGIQUE courante.
     * Appelé quand le nœud sous-jacent est déplacé manuellement.
     */
    public void syncAffichageAvecNoeud() {
        if (noeudCible == null) {
            // L'agent est sur son nœud position, on suit le nœud
            displayX = position.getX();
            displayY = position.getY();
        }
        // Si en transit, noeudDepart et noeudCible sont gardés tels quels —
        // l'animation les utilisera avec leurs nouvelles coordonnées.
    }

    // ── Avancement logique ─────────────────────────────────────────────────────
    private boolean avancerUnNoeud(double facteurRalent) {
        if (indexChemin >= chemin.size()) {
            arriver();
            return false;
        }

        Node prochain = chemin.get(indexChemin);

        if (prochain.isBloque() || !graph.getVoisins(position).contains(prochain)) {
            recalculerChemin();
            return false;
        }

        if (comportement == Comportement.LAISSE_PASSER
                && etatPsychologique != EtatPsychologique.PANIQUE) {
            if (compterAgentsDansNoeud(prochain) > 0
                    && RANDOM.nextDouble() > toleranceDensite) {
                ticksAttente = (int) Math.max(1, 1.0 / facteurRalent);
                return false;
            }
        }

        // Mémorise le départ pour l'animation
        noeudDepart   = position;
        noeudCible    = prochain;
        progressArete = 0.0;

        position = prochain;
        indexChemin++;

        if (position.equals(destination)) {
            arriver();
            return false;
        }
        return true;
    }

    private void arriver() {
        etatDeplacement = EtatDeplacement.ARRIVE;
        // Garde noeudCible et progressArete pour que l'animation puisse finir
        // de glisser jusqu'au nœud de destination avant de se figer
        System.out.println("[Agent " + id + "] ✔ Arrivé à " + destination.getId());
        if (modeDestination == ModeDestination.ALEATOIRE) {
            destination = choisirDestinationAleatoire();
            if (destination != null) {
                etatDeplacement = EtatDeplacement.EN_MOUVEMENT;
                recalculerChemin();
            }
        }
    }

    private void deplacerAleatoirement() {
        List<Node> voisins = graph.getVoisins(position);
        if (voisins.isEmpty()) { etatDeplacement = EtatDeplacement.BLOQUE; return; }
        Node next = voisins.get(RANDOM.nextInt(voisins.size()));
        noeudDepart   = position;
        noeudCible    = next;
        progressArete = 0.0;
        position      = next;
    }

    public void recalculerChemin() {
        if (modeDestination == ModeDestination.ALEATOIRE)
            destination = choisirDestinationAleatoire();
        pathFinder.recalculer();
        if (destination == null) { etatDeplacement = EtatDeplacement.BLOQUE; return; }
        chemin = pathFinder.calculerChemin(position, destination);
        if (chemin.isEmpty()) {
            etatDeplacement = EtatDeplacement.BLOQUE;
        } else {
            indexChemin    = 1;
            etatDeplacement = EtatDeplacement.EN_MOUVEMENT;
        }
    }

    /** Téléportation instantanée (suppression de nœud, etc.). */
    public void teleporterVers(Node noeud) {
        position      = noeud;
        noeudDepart   = noeud;
        noeudCible    = null;
        progressArete = 1.0;
        displayX      = noeud.getX();
        displayY      = noeud.getY();
        chemin.clear();
        indexChemin = 0;
        recalculerChemin();
    }

    // ── Utilitaires privés ─────────────────────────────────────────────────────
    private double getFacteurRalentissementVoisin() {
        for (Node v : graph.getVoisinsRaw(position))
            if (v instanceof ZoneDanger) return ((ZoneDanger) v).getFacteurRalentissement();
        return 1.0;
    }

    private int vitesseEffective() {
        return etatPsychologique == EtatPsychologique.PANIQUE ? vitesse * 2 : vitesse;
    }

    private int compterAgentsDansNoeud(Node noeud) {
        int count = 0;
        for (Agent a : autresAgents)
            if (a != this && a.getPosition().equals(noeud)) count++;
        return count;
    }

    private Node choisirDestinationAleatoire() {
        List<Node> acc = new ArrayList<>();
        for (Node n : graph.getNodes())
            if (!n.isBloque() && !n.equals(position)) acc.add(n);
        return acc.isEmpty() ? null : acc.get(RANDOM.nextInt(acc.size()));
    }

    private String cheminToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chemin.size(); i++) {
            sb.append(chemin.get(i).getId());
            if (i < chemin.size() - 1) sb.append(" → ");
        }
        return sb.toString();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────
    public String             getId()                { return id; }
    public Node               getPosition()          { return position; }
    public Node               getDestination()       { return destination; }
    public int                getVitesse()           { return vitesse; }
    public double             getToleranceDensite()  { return toleranceDensite; }
    public Comportement       getComportement()      { return comportement; }
    public EtatPsychologique  getEtatPsychologique() { return etatPsychologique; }
    public ModeDestination    getModeDestination()   { return modeDestination; }
    public EtatDeplacement    getEtatDeplacement()   { return etatDeplacement; }
    public List<Node>         getChemin()            { return chemin; }
    public double             getDisplayX()          { return displayX; }
    public double             getDisplayY()          { return displayY; }
    public Node               getNoeudCible()        { return noeudCible; }

    public void setVitesse(int v)                        { this.vitesse = Math.max(1, v); }
    public void setToleranceDensite(double t)            { this.toleranceDensite = Math.min(1.0, Math.max(0.0, t)); }
    public void setComportement(Comportement c)          { this.comportement = c; }
    public void setModeDestination(ModeDestination m)    { this.modeDestination = m; }
    public void setDestination(Node d)                   { this.destination = d; }
    public void setAutresAgents(List<Agent> a)           { this.autresAgents = a; }
    public void setEtatDeplacement(EtatDeplacement e)    { this.etatDeplacement = e; }

    public void setEtatPsychologique(EtatPsychologique etat) {
        this.etatPsychologique = etat;
        if (etat == EtatPsychologique.PANIQUE) this.comportement = Comportement.PRIORITAIRE;
    }

    @Override
    public String toString() {
        return "Agent{id='" + id + "', pos='" + position.getId()
             + "', dest='" + (destination != null ? destination.getId() : "—")
             + "', etat=" + etatDeplacement + "}";
    }
}
