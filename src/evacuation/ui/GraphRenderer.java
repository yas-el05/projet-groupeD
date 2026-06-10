package evacuation.ui;

import evacuation.agent.Agent;
import evacuation.graph.Edge;
import evacuation.graph.Graph;
import evacuation.graph.Node;
import evacuation.graph.Sortie;
import evacuation.graph.ZoneDanger;
import evacuation.simulation.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Panneau de rendu du graphe.
 *
 * Deux timers :
 *  - animTimer (60 fps) : anime le glissement des agents sur les arêtes
 *  - (le tick logique est piloté par MainFrame)
 */
public class GraphRenderer extends JPanel {

    // ── Constantes ─────────────────────────────────────────────────────────────
    private static final int    NODE_RADIUS    = 24;
    private static final int    AGENT_RADIUS   = 10;
    static final double         SCALE          = 90.0;
    static final int            OFFSET         = 70;
    private static final double MIN_DIST_NOEUD = 0.7;
    /** Vitesse d'animation par frame (proportion de l'arête parcourue). */
    private static final double ANIM_SPEED     = 0.10; // 10 frames pour traverser une arête

    // ── Palette ────────────────────────────────────────────────────────────────
    static final Color BG              = new Color(15, 20, 32);
    static final Color GRID            = new Color(30, 38, 55);
    static final Color NODE_NORMAL     = new Color(60, 120, 220);
    static final Color NODE_CONGESTION = new Color(20, 50, 160);
    static final Color NODE_SORTIE_O   = new Color(40, 200, 110);
    static final Color NODE_SORTIE_F   = new Color(90, 100, 120);
    static final Color NODE_DANGER_F   = new Color(220, 60, 30);
    static final Color NODE_DANGER_I   = new Color(40, 100, 210);
    static final Color NODE_DANGER_P   = new Color(180, 40, 180);
    static final Color EDGE_FREE       = new Color(70, 95, 140);
    static final Color EDGE_CONG       = new Color(220, 60, 30);
    static final Color EDGE_BLOCKED    = new Color(160, 30, 30);
    static final Color AGENT_CALME     = new Color(255, 225, 50);
    static final Color AGENT_PANIQUE   = new Color(255, 120, 30);
    static final Color AGENT_FOLIE     = new Color(210, 50, 210);
    static final Color AGENT_BLOQUE    = new Color(190, 40, 40);
    static final Color AGENT_ARRIVE    = new Color(100, 220, 100);
    static final Color SEL_COLOR       = new Color(0, 215, 255);
    static final Color TEXT            = new Color(205, 215, 235);

    // ── État ───────────────────────────────────────────────────────────────────
    private SimulationEngine engine;
    private Agent   agentSelectionne;
    private Node    noeudSurvole;
    private Edge    areteSurvolee;
    private Node    noeudDragged;
    private Point   dragStart;

    public enum Mode { SELECTION, AJOUT_NOEUD, AJOUT_SORTIE, AJOUT_ARETE, AJOUT_DANGER,
                       AJOUT_AGENT, SUPPRESSION, SUPPRESSION_AGENT }
    private Mode mode = Mode.SELECTION;
    private Node premierNoeudArete;
    private Point souris;

    /** Timer d'animation — 60 fps, indépendant du timer de simulation. */
    private final Timer animTimer;

    // ── Callbacks ──────────────────────────────────────────────────────────────
    private Consumer<Agent>        onAgentSel;
    private Consumer<Node>         onNoeudSel;
    private BiConsumer<Node, Node> onAreteAjoutee;
    private Consumer<Node>         onNoeudAjoute;
    private Consumer<Node>         onNoeudSupprime;
    private Consumer<Edge>         onAreteSupprimee;

    // ── Constructeur ───────────────────────────────────────────────────────────
    public GraphRenderer(SimulationEngine engine) {
        this.engine = engine;
        setBackground(BG);
        setPreferredSize(new Dimension(950, 680));
        setFocusable(true);
        initMouse();
        initKeys();

        // Timer d'animation 60 fps
        animTimer = new Timer(16, e -> {
            boolean besoinRepaint = false;
            for (Agent a : engine.getAgents()) {
                if (a.animerDeplacement(ANIM_SPEED)) besoinRepaint = true;
            }
            repaint(); // toujours repeindre pour que le drag soit fluide aussi
        });
        animTimer.start();
    }

    // ── Setters ────────────────────────────────────────────────────────────────
    public void setOnAgentSelectionne(Consumer<Agent> cb)        { this.onAgentSel = cb; }
    public void setOnNoeudSelectionne(Consumer<Node> cb)         { this.onNoeudSel = cb; }
    public void setOnAreteAjoutee(BiConsumer<Node, Node> cb)     { this.onAreteAjoutee = cb; }
    public void setOnNoeudAjoute(Consumer<Node> cb)              { this.onNoeudAjoute = cb; }
    public void setOnNoeudSupprime(Consumer<Node> cb)            { this.onNoeudSupprime = cb; }
    public void setOnAreteSupprimee(Consumer<Edge> cb)           { this.onAreteSupprimee = cb; }

    public Mode  getMode()      { return mode; }
    public Agent getAgentSel()  { return agentSelectionne; }

    public void setMode(Mode m) {
        this.mode = m;
        this.premierNoeudArete = null;
        requestFocusInWindow();
        repaint();
    }

    /** Appelé par MainFrame après un tick logique. */
    public void rafraichir() { repaint(); }

    public void stopAnimation() { animTimer.stop(); }

    // ── Dessin principal ───────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,       RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,  RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,          RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,     RenderingHints.VALUE_STROKE_PURE);

        dessinerGrille(g2);
        dessinerAretes(g2);
        dessinerPreviewArete(g2);
        dessinerCheminAgent(g2);
        dessinerNoeuds(g2);
        dessinerAgents(g2);     // dessine TOUS les agents (y compris arrivés)
        dessinerLegende(g2);
        dessinerBandeauMode(g2);
    }

    // ── Grille ─────────────────────────────────────────────────────────────────
    private void dessinerGrille(Graphics2D g) {
        g.setColor(GRID);
        g.setStroke(new BasicStroke(0.5f));
        for (int x = OFFSET % 40; x < getWidth();  x += 40) g.drawLine(x, 0, x, getHeight());
        for (int y = OFFSET % 40; y < getHeight(); y += 40) g.drawLine(0, y, getWidth(), y);
    }

    // ── Arêtes (gradient couleur + épaisseur selon congestion) ────────────────
    private void dessinerAretes(Graphics2D g) {
        int maxCong = Math.max(1, engine.getCongestionMaxArete());
        for (Edge e : engine.getGraph().getEdges()) {
            int x1 = sx(e.getSource().getX()),      y1 = sy(e.getSource().getY());
            int x2 = sx(e.getDestination().getX()), y2 = sy(e.getDestination().getY());

            if (!e.isDisponible()) {
                g.setColor(EDGE_BLOCKED);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1, new float[]{6, 4}, 0));
                g.drawLine(x1, y1, x2, y2);
                g.setStroke(new BasicStroke(1f));
                continue;
            }

            float ratio = Math.min(1f, (float) e.getAgentsEnTransit() / maxCong);
            Color couleur = e.equals(areteSurvolee) ? SEL_COLOR : blend(EDGE_FREE, EDGE_CONG, ratio);
            float ep = 3.5f + ratio * 5f;

            g.setColor(couleur);
            g.setStroke(new BasicStroke(ep, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x1, y1, x2, y2);
            g.setStroke(new BasicStroke(1f));

            // Poids au milieu
            labelBg(g, String.format("%.0f", e.getPoidsBase()), (x1+x2)/2+5, (y1+y2)/2-5,
                    new Font("SansSerif", Font.PLAIN, 10), TEXT, new Color(15, 20, 32, 180));
        }
    }

    // ── Preview arête ──────────────────────────────────────────────────────────
    private void dessinerPreviewArete(Graphics2D g) {
        if (mode != Mode.AJOUT_ARETE || premierNoeudArete == null || souris == null) return;
        g.setColor(new Color(0, 215, 255, 100));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1, new float[]{9, 5}, 0));
        g.drawLine(sx(premierNoeudArete.getX()), sy(premierNoeudArete.getY()), souris.x, souris.y);
        g.setStroke(new BasicStroke(1f));
    }

    // ── Chemin de l'agent sélectionné ─────────────────────────────────────────
    private void dessinerCheminAgent(Graphics2D g) {
        if (agentSelectionne == null) return;
        List<Node> ch = agentSelectionne.getChemin();
        if (ch == null || ch.size() < 2) return;
        g.setColor(new Color(0, 215, 255, 140));
        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int debut = Math.max(0, ch.indexOf(agentSelectionne.getPosition()));
        for (int i = debut; i < ch.size() - 1; i++)
            g.drawLine(sx(ch.get(i).getX()), sy(ch.get(i).getY()),
                       sx(ch.get(i+1).getX()), sy(ch.get(i+1).getY()));
        g.setStroke(new BasicStroke(1f));
    }

    // ── Nœuds ─────────────────────────────────────────────────────────────────
    private void dessinerNoeuds(Graphics2D g) {
        int[] densite = engine.getDensiteParNoeud();
        int maxDen = Math.max(1, engine.getDensiteMax());
        List<Node> nodes = engine.getGraph().getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            Node n   = nodes.get(i);
            int  x   = sx(n.getX()), y = sy(n.getY());
            int  d   = i < densite.length ? densite[i] : 0;
            float ratio = Math.min(1f, (float) d / maxDen);

            boolean survole = n.equals(noeudSurvole);
            boolean premier = n.equals(premierNoeudArete);

            // Couleur de base selon type
            Color base;
            if (n instanceof ZoneDanger) {
                ZoneDanger.TypeDanger t = ((ZoneDanger) n).getTypeDanger();
                base = t == ZoneDanger.TypeDanger.FEU        ? NODE_DANGER_F
                     : t == ZoneDanger.TypeDanger.INONDATION ? NODE_DANGER_I : NODE_DANGER_P;
            } else if (n instanceof Sortie)  base = ((Sortie) n).isEstOuverte() ? NODE_SORTIE_O : NODE_SORTIE_F;
            else if (n.isBloque())            base = new Color(55, 55, 65);
            else                              base = blend(NODE_NORMAL, NODE_CONGESTION, ratio * 0.85f);

            // Ombre
            g.setColor(new Color(0, 0, 0, 70));
            g.fillOval(x-NODE_RADIUS+3, y-NODE_RADIUS+4, NODE_RADIUS*2, NODE_RADIUS*2);

            // Corps dégradé
            Paint old = g.getPaint();
            g.setPaint(new GradientPaint(x-NODE_RADIUS, y-NODE_RADIUS, base.brighter(),
                                          x+NODE_RADIUS, y+NODE_RADIUS, base.darker().darker()));
            g.fillOval(x-NODE_RADIUS, y-NODE_RADIUS, NODE_RADIUS*2, NODE_RADIUS*2);
            g.setPaint(old);

            // Bordure
            float sw; Color bc;
            if      (premier)  { bc = SEL_COLOR;  sw = 3.5f; }
            else if (survole)  { bc = Color.WHITE; sw = 2.5f; }
            else               { bc = new Color(0, 0, 0, 100); sw = 1.5f; }
            g.setColor(bc); g.setStroke(new BasicStroke(sw));
            g.drawOval(x-NODE_RADIUS, y-NODE_RADIUS, NODE_RADIUS*2, NODE_RADIUS*2);
            g.setStroke(new BasicStroke(1f));

            // Icône
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            String sym = null;
            if (n instanceof ZoneDanger) {
                ZoneDanger.TypeDanger t = ((ZoneDanger) n).getTypeDanger();
                sym = t == ZoneDanger.TypeDanger.FEU ? "🔥"
                    : t == ZoneDanger.TypeDanger.INONDATION ? "🌊" : "⚠";
            } else if (n instanceof Sortie) sym = "⬖";
            if (sym != null) {
                g.setColor(Color.WHITE);
                g.drawString(sym, x - fm.stringWidth(sym)/2, y + fm.getAscent()/2 - 2);
            }

            // Label
            labelBg(g, n.getId(), x, y+NODE_RADIUS+14,
                    new Font("SansSerif", Font.BOLD, 11), TEXT, new Color(15, 20, 32, 160));

            // Badge nombre d'agents (en mouvement + arrivés sur ce nœud)
            int total = compterAgentsSurNoeud(n);
            if (total > 0) {
                int bx = x+NODE_RADIUS-7, by = y-NODE_RADIUS-7;
                g.setColor(new Color(220, 50, 50));
                g.fillOval(bx, by, 16, 16);
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                fm = g.getFontMetrics();
                String cnt = String.valueOf(total);
                g.drawString(cnt, bx + (16-fm.stringWidth(cnt))/2, by+fm.getAscent()+1);
            }
        }
    }

    /** Compte tous les agents présents sur un nœud (y compris arrivés). */
    private int compterAgentsSurNoeud(Node n) {
        int count = 0;
        for (Agent a : engine.getAgents())
            if (a.getPosition().equals(n)) count++;
        return count;
    }

    // ── Agents — TOUS dessinés, y compris arrivés ─────────────────────────────
    private void dessinerAgents(Graphics2D g) {
        List<Agent> agents = engine.getAgents();
        for (int idx = 0; idx < agents.size(); idx++) {
            Agent a = agents.get(idx);

            // Position d'affichage interpolée
            int ax = sx(a.getDisplayX());
            int ay = sy(a.getDisplayY());

            // Décalage pour plusieurs agents au même emplacement
            int slot = idx % 8;
            int dx = (int)(Math.cos(slot * Math.PI / 4) * 15);
            int dy = (int)(Math.sin(slot * Math.PI / 4) * 15);

            Color col;
            if (a.getEtatDeplacement() == Agent.EtatDeplacement.ARRIVE) {
                col = AGENT_ARRIVE;  // vert = arrivé, reste visible dans la sortie
            } else {
                col = switch (a.getEtatPsychologique()) {
                    case PANIQUE -> AGENT_PANIQUE;
                    case FOLIE   -> AGENT_FOLIE;
                    default -> switch (a.getEtatDeplacement()) {
                        case EN_MOUVEMENT -> AGENT_CALME;
                        case BLOQUE       -> AGENT_BLOQUE;
                        default           -> new Color(170, 180, 205);
                    };
                };
            }

            boolean sel = a.equals(agentSelectionne);
            int r = sel ? AGENT_RADIUS + 4 : AGENT_RADIUS;

            // Halo sélection
            if (sel) {
                g.setColor(new Color(0, 215, 255, 50));
                g.fillOval(ax+dx-r-5, ay+dy-r-5, (r+5)*2, (r+5)*2);
            }

            // Ombre
            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval(ax+dx-r+2, ay+dy-r+3, r*2, r*2);

            // Corps dégradé
            Paint old = g.getPaint();
            g.setPaint(new GradientPaint(ax+dx-r, ay+dy-r, col.brighter(),
                                          ax+dx+r, ay+dy+r, col.darker()));
            g.fillOval(ax+dx-r, ay+dy-r, r*2, r*2);
            g.setPaint(old);

            // Bordure
            g.setColor(sel ? SEL_COLOR : new Color(0, 0, 0, 120));
            g.setStroke(new BasicStroke(sel ? 2.5f : 1f));
            g.drawOval(ax+dx-r, ay+dy-r, r*2, r*2);
            g.setStroke(new BasicStroke(1f));

            // Petite icône ✓ pour les arrivés
            if (a.getEtatDeplacement() == Agent.EtatDeplacement.ARRIVE) {
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                g.drawString("✓", ax+dx-3, ay+dy+4);
            }

            // Label ID
            labelBg(g, a.getId(), ax+dx+r+3, ay+dy+4,
                    new Font("SansSerif", Font.BOLD, 9), TEXT, new Color(15, 20, 32, 160));
        }
    }

    // ── Légende en 3 colonnes ─────────────────────────────────────────────────
    private void dessinerLegende(Graphics2D g) {
        int bx = 10, by = getHeight() - 230;
        int colW = 158, rowH = 18, padX = 12, padY = 16;
        int rows = 8;
        int totalW = colW * 3 + padX;
        int totalH = rows * rowH + padY * 2 + 18;

        g.setColor(new Color(15, 20, 32, 215));
        g.fillRoundRect(bx, by, totalW, totalH, 12, 12);
        g.setColor(new Color(70, 95, 140, 120));
        g.drawRoundRect(bx, by, totalW, totalH, 12, 12);

        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(SEL_COLOR);
        g.drawString("NŒUDS",  bx+padX,        by+padY);
        g.drawString("ARÊTES", bx+padX+colW,   by+padY);
        g.drawString("AGENTS", bx+padX+colW*2, by+padY);

        g.setColor(new Color(70, 95, 140, 70));
        g.drawLine(bx+colW+padX/2,   by+5, bx+colW+padX/2,   by+totalH-5);
        g.drawLine(bx+colW*2+padX/2, by+5, bx+colW*2+padX/2, by+totalH-5);

        int y0 = by + padY + 14;
        // Nœuds
        ligne(g, bx+padX, y0,         NODE_NORMAL,              "Normal");
        ligne(g, bx+padX, y0+rowH,    NODE_SORTIE_O,            "Sortie ouverte");
        ligne(g, bx+padX, y0+rowH*2,  NODE_SORTIE_F,            "Sortie fermée");
        ligne(g, bx+padX, y0+rowH*3,  NODE_DANGER_F,            "Feu");
        ligne(g, bx+padX, y0+rowH*4,  NODE_DANGER_I,            "Inondation");
        ligne(g, bx+padX, y0+rowH*5,  NODE_DANGER_P,            "Pers. dangereuse");
        ligne(g, bx+padX, y0+rowH*6,  new Color(55,55,65),      "Bloqué");
        // Arêtes
        ligne(g, bx+padX+colW, y0,        EDGE_FREE,                    "Libre");
        ligne(g, bx+padX+colW, y0+rowH,   blend(EDGE_FREE,EDGE_CONG,.5f),"Mi-chargée");
        ligne(g, bx+padX+colW, y0+rowH*2, EDGE_CONG,                    "Saturée");
        ligne(g, bx+padX+colW, y0+rowH*3, EDGE_BLOCKED,                 "Bloquée");
        ligne(g, bx+padX+colW, y0+rowH*4, SEL_COLOR,                    "Trajet sél.");
        // Agents
        ligne(g, bx+padX+colW*2, y0,        AGENT_CALME,               "Calme / En mvt");
        ligne(g, bx+padX+colW*2, y0+rowH,   AGENT_PANIQUE,             "Panique");
        ligne(g, bx+padX+colW*2, y0+rowH*2, AGENT_FOLIE,               "Folie");
        ligne(g, bx+padX+colW*2, y0+rowH*3, AGENT_BLOQUE,              "Bloqué");
        ligne(g, bx+padX+colW*2, y0+rowH*4, AGENT_ARRIVE,              "Arrivé (✓)");
        ligne(g, bx+padX+colW*2, y0+rowH*5, new Color(170,180,205),    "En attente");
    }

    private void ligne(Graphics2D g, int x, int y, Color c, String lbl) {
        g.setColor(c);
        g.fillRoundRect(x, y-9, 13, 13, 4, 4);
        g.setColor(new Color(0, 0, 0, 70));
        g.drawRoundRect(x, y-9, 13, 13, 4, 4);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(TEXT);
        g.drawString(lbl, x+17, y);
    }

    // ── Bandeau mode ───────────────────────────────────────────────────────────
    private void dessinerBandeauMode(Graphics2D g) {
        String txt;
        Color accent;
        switch (mode) {
            case AJOUT_NOEUD       -> { txt = "✚ NŒUD — clic pour placer";                      accent = new Color(60,180,100); }
            case AJOUT_SORTIE      -> { txt = "🚪 SORTIE — clic pour placer";                    accent = NODE_SORTIE_O; }
            case AJOUT_ARETE       -> { txt = premierNoeudArete == null
                                            ? "─ ARÊTE — clic sur le 1er nœud"
                                            : "─ ARÊTE — clic sur le 2e nœud";                  accent = SEL_COLOR; }
            case AJOUT_DANGER      -> { txt = "⚠ DANGER — clic pour placer";                    accent = NODE_DANGER_F; }
            case AJOUT_AGENT       -> { txt = "👤 AGENT — clic droit sur un nœud pour ajouter"; accent = AGENT_CALME; }
            case SUPPRESSION       -> { txt = "✖ SUPPRIMER — clic sur nœud ou arête";           accent = AGENT_BLOQUE; }
            case SUPPRESSION_AGENT -> { txt = "✖ SUPPRIMER AGENT — clic sur un agent";          accent = AGENT_PANIQUE; }
            default                -> { txt = "SÉLECTION — clic droit sur nœud pour options";   accent = TEXT; }
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(txt);
        int px = getWidth()/2 - tw/2 - 14, py = 8;
        g.setColor(new Color(15, 20, 32, 215));
        g.fillRoundRect(px, py, tw+28, 26, 12, 12);
        g.setColor(accent.darker());
        g.drawRoundRect(px, py, tw+28, 26, 12, 12);
        g.setColor(accent);
        g.drawString(txt, px+14, py+18);
    }

    // ── Utilitaires ────────────────────────────────────────────────────────────
    int    sx(double gx) { return (int)(gx * SCALE + OFFSET); }
    int    sy(double gy) { return (int)(gy * SCALE + OFFSET); }
    double gx(int   sx)  { return (sx - OFFSET) / SCALE; }
    double gy(int   sy)  { return (sy - OFFSET) / SCALE; }

    Color blend(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
            (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    private void labelBg(Graphics2D g, String txt, int cx, int cy,
                          Font font, Color fg, Color bg) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(txt), th = fm.getAscent();
        g.setColor(bg);
        g.fillRoundRect(cx-tw/2-2, cy-th, tw+4, th+2, 4, 4);
        g.setColor(fg);
        g.drawString(txt, cx-tw/2, cy);
    }

    // ── Hit-tests ──────────────────────────────────────────────────────────────
    Node nodeAt(int mx, int my) {
        int r2 = (NODE_RADIUS+5) * (NODE_RADIUS+5);
        for (Node n : engine.getGraph().getNodes()) {
            int dx = mx - sx(n.getX()), dy = my - sy(n.getY());
            if (dx*dx + dy*dy <= r2) return n;
        }
        return null;
    }

    /** Cherche un agent à la position de la souris (tous états, y compris ARRIVE). */
    Agent agentAt(int mx, int my) {
        int threshold2 = (AGENT_RADIUS+8) * (AGENT_RADIUS+8);
        for (Agent a : engine.getAgents()) {
            int dx = mx - sx(a.getDisplayX()), dy = my - sy(a.getDisplayY());
            if (dx*dx + dy*dy <= threshold2) return a;
        }
        return null;
    }

    Edge edgeAt(int mx, int my) {
        for (Edge e : engine.getGraph().getEdges()) {
            double d = ptSegDist(mx, my,
                sx(e.getSource().getX()),       sy(e.getSource().getY()),
                sx(e.getDestination().getX()),  sy(e.getDestination().getY()));
            if (d < 10) return e;
        }
        return null;
    }

    private double ptSegDist(double px, double py,
                              double x1, double y1, double x2, double y2) {
        double dx = x2-x1, dy = y2-y1;
        if (dx==0 && dy==0) return Math.hypot(px-x1, py-y1);
        double t = Math.max(0, Math.min(1, ((px-x1)*dx+(py-y1)*dy)/(dx*dx+dy*dy)));
        return Math.hypot(px-(x1+t*dx), py-(y1+t*dy));
    }

    // ── Menu contextuel nœud ───────────────────────────────────────────────────
    private void showContextMenu(Node n, int mx, int my) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(25, 32, 50));

        JLabel titre = new JLabel("  " + n.getId() + "  (" + n.getClass().getSimpleName() + ")  ");
        titre.setForeground(SEL_COLOR);
        titre.setFont(new Font("SansSerif", Font.BOLD, 12));
        menu.add(titre);
        menu.addSeparator();

        // Supprimer le nœud
        JMenuItem del = item("✖ Supprimer ce nœud", AGENT_BLOQUE);
        del.addActionListener(e -> {
            engine.onNoeudSupprime(n);
            engine.getGraph().supprimerNode(n);
            if (onNoeudSupprime != null) onNoeudSupprime.accept(n);
            repaint();
        });
        menu.add(del);
        menu.addSeparator();

        // Conversions de type
        if (!(n instanceof Sortie) && !(n instanceof ZoneDanger)) {
            JMenuItem toSortie = item("🚪 Convertir en Sortie", NODE_SORTIE_O);
            toSortie.addActionListener(e -> convertirEnSortie(n));
            menu.add(toSortie);

            JMenu dm = new JMenu("⚠ Convertir en danger…");
            dm.setForeground(NODE_DANGER_F);
            for (ZoneDanger.TypeDanger t : ZoneDanger.TypeDanger.values()) {
                JMenuItem di = new JMenuItem(t.name());
                di.addActionListener(ev -> convertirEnDanger(n, t));
                dm.add(di);
            }
            menu.add(dm);
        }
        if (n instanceof Sortie) {
            Sortie s = (Sortie) n;
            JMenuItem tog = item(s.isEstOuverte() ? "🔒 Fermer" : "🔓 Ouvrir",
                                  s.isEstOuverte() ? AGENT_BLOQUE : NODE_SORTIE_O);
            tog.addActionListener(e -> { if (s.isEstOuverte()) s.fermer(); else s.ouvrir(); repaint(); });
            menu.add(tog);
            menu.add(itemAction("↩ En nœud normal", NODE_NORMAL, () -> convertirEnNormal(n)));
        }
        if (n instanceof ZoneDanger) {
            menu.add(itemAction("↩ Supprimer le danger", NODE_NORMAL, () -> convertirEnNormal(n)));
        }

        menu.addSeparator();
        // Ajouter un agent
        JMenuItem addAg = item("👤 Ajouter un agent ici", AGENT_CALME);
        addAg.addActionListener(e -> dialogAjoutAgent(n));
        menu.add(addAg);

        menu.show(this, mx, my);
    }

    /** Menu contextuel sur agent (clic droit). */
    private void showContextMenuAgent(Agent a, int mx, int my) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(25, 32, 50));
        JLabel titre = new JLabel("  Agent " + a.getId() + "  ");
        titre.setForeground(AGENT_CALME);
        titre.setFont(new Font("SansSerif", Font.BOLD, 12));
        menu.add(titre);
        menu.addSeparator();
        menu.add(itemAction("✖ Supprimer cet agent", AGENT_BLOQUE, () -> {
            engine.supprimerAgent(a);
            if (agentSelectionne == a) { agentSelectionne = null; if (onAgentSel != null) onAgentSel.accept(null); }
            repaint();
        }));
        menu.show(this, mx, my);
    }

    private JMenuItem item(String txt, Color fg) {
        JMenuItem it = new JMenuItem(txt);
        it.setForeground(fg);
        it.setBackground(new Color(25, 32, 50));
        it.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return it;
    }

    private JMenuItem itemAction(String txt, Color fg, Runnable action) {
        JMenuItem it = item(txt, fg);
        it.addActionListener(e -> action.run());
        return it;
    }

    // ── Conversions de type nœud ───────────────────────────────────────────────
    private void convertirEnSortie(Node n) {
        remplacerNoeud(n, new Sortie(engine.getGraph().genererIdUnique("S"), n.getX(), n.getY(), 10));
    }
    private void convertirEnDanger(Node n, ZoneDanger.TypeDanger type) {
        remplacerNoeud(n, new ZoneDanger(engine.getGraph().genererIdUnique("Z"), n.getX(), n.getY(), type, 8));
    }
    private void convertirEnNormal(Node n) {
        remplacerNoeud(n, new Node(engine.getGraph().genererIdUnique("N"), n.getX(), n.getY()));
    }

    private void remplacerNoeud(Node ancien, Node nouveau) {
        Graph g = engine.getGraph();
        List<Edge> aretes = new java.util.ArrayList<>(g.getEdgesDuNoeud(ancien));
        for (Edge e : aretes) g.supprimerEdge(e);
        g.supprimerNode(ancien);
        g.ajouterNode(nouveau);
        for (Edge e : aretes) {
            Node src  = e.getSource().equals(ancien)      ? nouveau : e.getSource();
            Node dest = e.getDestination().equals(ancien) ? nouveau : e.getDestination();
            g.ajouterEdge(new Edge(src, dest, e.getPoidsBase()));
        }
        for (Agent a : engine.getAgents()) {
            if (a.getPosition().equals(ancien))    a.teleporterVers(nouveau);
            if (ancien.equals(a.getDestination())) { a.setDestination(nouveau); a.recalculerChemin(); }
        }
        repaint();
    }

    // ── Dialogue ajout d'agent ─────────────────────────────────────────────────
    void dialogAjoutAgent(Node posDep) {
        List<Node> sorties = new java.util.ArrayList<>();
        for (Node n : engine.getGraph().getNodes())
            if (n instanceof Sortie && !n.isBloque()) sorties.add(n);
        if (sorties.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Aucune sortie disponible.", "Impossible", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this),
                "Ajouter un agent", true);
        dlg.setLayout(new GridBagLayout());
        dlg.getContentPane().setBackground(new Color(22, 28, 44));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 10, 5, 10);
        gc.fill = GridBagConstraints.HORIZONTAL;

        String[] destLabels = sorties.stream().map(Node::getId).toArray(String[]::new);
        JComboBox<String> comboDest   = new JComboBox<>(destLabels);
        JComboBox<String> comboPsycho = new JComboBox<>(new String[]{"CALME","PANIQUE","FOLIE"});
        JComboBox<String> comboComp   = new JComboBox<>(new String[]{"LAISSE_PASSER","PRIORITAIRE","SUIT_AGENT"});
        JSpinner spinVit = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));

        Object[][] rows = {
            {"Départ :", new JLabel(posDep.getId())},
            {"Destination :", comboDest},
            {"État psycho :", comboPsycho},
            {"Comportement :", comboComp},
            {"Vitesse :", spinVit},
        };
        for (int i = 0; i < rows.length; i++) {
            gc.gridx=0; gc.gridy=i;
            JLabel lbl = new JLabel((String) rows[i][0]);
            lbl.setForeground(TEXT);
            dlg.add(lbl, gc);
            gc.gridx=1;
            dlg.add((Component) rows[i][1], gc);
        }

        JButton ok = new JButton("Ajouter"), cancel = new JButton("Annuler");
        JPanel btns = new JPanel(); btns.setBackground(new Color(22, 28, 44));
        btns.add(ok); btns.add(cancel);
        gc.gridx=0; gc.gridy=rows.length; gc.gridwidth=2;
        dlg.add(btns, gc);

        ok.addActionListener(e -> {
            Node dest = sorties.get(comboDest.getSelectedIndex());
            Agent.EtatPsychologique psycho = Agent.EtatPsychologique.valueOf(
                    (String) comboPsycho.getSelectedItem());
            Agent.Comportement comp = Agent.Comportement.valueOf(
                    (String) comboComp.getSelectedItem());
            String agId = engine.getGraph().genererIdUnique("AG");
            Agent ag = new Agent(agId, posDep, dest, engine.getGraph(),
                    (int) spinVit.getValue(), 1.0, comp, psycho, Agent.ModeDestination.FIXE);
            engine.ajouterAgent(ag);
            if (engine.isEnCours()) ag.initialiser();
            System.out.println("[UI] Agent " + agId + " ajouté en " + posDep.getId());
            dlg.dispose();
            repaint();
        });
        cancel.addActionListener(e -> dlg.dispose());
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ── Souris ─────────────────────────────────────────────────────────────────
    private void initMouse() {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e)  { handleClick(e); }
            @Override public void mousePressed(MouseEvent e)  {
                if (mode == Mode.SELECTION && !SwingUtilities.isRightMouseButton(e)) {
                    // Priorité drag agent > drag nœud ?
                    // On ne drag que les nœuds pour éviter confusion
                    noeudDragged = nodeAt(e.getX(), e.getY());
                    if (noeudDragged != null) dragStart = e.getPoint();
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                noeudDragged = null; dragStart = null;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                souris = e.getPoint();
                Node nn = nodeAt(e.getX(), e.getY());
                Edge ne = nn == null ? edgeAt(e.getX(), e.getY()) : null;
                if (nn != noeudSurvole || ne != areteSurvolee) {
                    noeudSurvole  = nn;
                    areteSurvolee = ne;
                }
                // toujours repaint géré par animTimer
            }

            @Override public void mouseDragged(MouseEvent e) {
                souris = e.getPoint();
                if (mode == Mode.SELECTION && noeudDragged != null && dragStart != null) {
                    double nx = gx(e.getX()), ny = gy(e.getY());
                    // Anti-superposition
                    boolean conflit = false;
                    for (Node other : engine.getGraph().getNodes()) {
                        if (other.equals(noeudDragged)) continue;
                        if (Math.hypot(other.getX()-nx, other.getY()-ny) < MIN_DIST_NOEUD) {
                            conflit = true; break;
                        }
                    }
                    if (!conflit) {
                        noeudDragged.setX(nx);
                        noeudDragged.setY(ny);
                        // Les agents sur ce nœud suivent visuellement
                        for (Agent a : engine.getAgents()) {
                            if (a.getPosition().equals(noeudDragged)
                                    && a.getNoeudCible() == null) {
                                a.syncAffichageAvecNoeud();
                            }
                        }
                    }
                }
            }
        });
    }

    private void handleClick(MouseEvent e) {
        int mx = e.getX(), my = e.getY();

        // Clic droit : menu contextuel sur agent d'abord, puis nœud
        if (SwingUtilities.isRightMouseButton(e)) {
            Agent a = agentAt(mx, my);
            if (a != null) { showContextMenuAgent(a, mx, my); return; }
            Node  n = nodeAt(mx, my);
            if (n != null) { showContextMenu(n, mx, my); return; }
            return;
        }

        switch (mode) {
            case SELECTION -> {
                Agent a = agentAt(mx, my);
                if (a != null) {
                    agentSelectionne = a.equals(agentSelectionne) ? null : a;
                    if (onAgentSel != null) onAgentSel.accept(agentSelectionne);
                } else {
                    Node n = nodeAt(mx, my);
                    if (n != null) { if (onNoeudSel != null) onNoeudSel.accept(n); }
                    else { agentSelectionne = null; if (onAgentSel != null) onAgentSel.accept(null); }
                }
            }

            case AJOUT_NOEUD -> {
                double nx = gx(mx), ny = gy(my);
                if (engine.getGraph().tropProche(nx, ny, MIN_DIST_NOEUD)) {
                    JOptionPane.showMessageDialog(this, "Trop proche d'un nœud existant.", "Invalide", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Node n = new Node(engine.getGraph().genererIdUnique("N"), nx, ny);
                engine.getGraph().ajouterNode(n);
                if (onNoeudAjoute != null) onNoeudAjoute.accept(n);
            }

            case AJOUT_SORTIE -> {
                double nx = gx(mx), ny = gy(my);
                if (engine.getGraph().tropProche(nx, ny, MIN_DIST_NOEUD)) {
                    JOptionPane.showMessageDialog(this, "Trop proche d'un nœud existant.", "Invalide", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Sortie s = new Sortie(engine.getGraph().genererIdUnique("S"), nx, ny, 10);
                engine.getGraph().ajouterNode(s);
                if (onNoeudAjoute != null) onNoeudAjoute.accept(s);
            }

            case AJOUT_DANGER -> {
                double nx = gx(mx), ny = gy(my);
                if (engine.getGraph().tropProche(nx, ny, MIN_DIST_NOEUD)) {
                    JOptionPane.showMessageDialog(this, "Trop proche d'un nœud existant.", "Invalide", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String[] types = {"FEU — se propage", "INONDATION — bloque arêtes", "PERSONNE DANGEREUSE — ralentit"};
                int choix = JOptionPane.showOptionDialog(this, "Type de danger ?", "Zone de danger",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, types, types[0]);
                if (choix < 0) return;
                ZoneDanger z = new ZoneDanger(engine.getGraph().genererIdUnique("Z"),
                        nx, ny, ZoneDanger.TypeDanger.values()[choix], 8);
                engine.getGraph().ajouterNode(z);
                if (onNoeudAjoute != null) onNoeudAjoute.accept(z);
            }

            case AJOUT_AGENT -> {
                // En mode AJOUT_AGENT, le clic gauche sur un nœud ouvre le dialogue
                Node n = nodeAt(mx, my);
                if (n != null) dialogAjoutAgent(n);
                else JOptionPane.showMessageDialog(this,
                        "Cliquez sur un nœud existant pour placer un agent.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
            }

            case AJOUT_ARETE -> {
                Node clique = nodeAt(mx, my);
                if (clique == null) return;
                if (premierNoeudArete == null) {
                    premierNoeudArete = clique;
                } else if (!premierNoeudArete.equals(clique)) {
                    if (!engine.getGraph().areteExiste(premierNoeudArete, clique)) {
                        if (onAreteAjoutee != null) onAreteAjoutee.accept(premierNoeudArete, clique);
                    } else {
                        JOptionPane.showMessageDialog(this, "Cette arête existe déjà.", "Info",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                    premierNoeudArete = null;
                } else {
                    premierNoeudArete = null;
                }
            }

            case SUPPRESSION -> {
                Node n = nodeAt(mx, my);
                if (n != null) {
                    engine.onNoeudSupprime(n);
                    engine.getGraph().supprimerNode(n);
                    if (onNoeudSupprime != null) onNoeudSupprime.accept(n);
                } else {
                    Edge ar = edgeAt(mx, my);
                    if (ar != null) {
                        engine.getGraph().supprimerEdge(ar);
                        if (onAreteSupprimee != null) onAreteSupprimee.accept(ar);
                    }
                }
            }

            case SUPPRESSION_AGENT -> {
                Agent a = agentAt(mx, my);
                if (a != null) {
                    engine.supprimerAgent(a);
                    if (a.equals(agentSelectionne)) {
                        agentSelectionne = null;
                        if (onAgentSel != null) onAgentSel.accept(null);
                    }
                }
            }
        }
        repaint();
    }

    // ── Clavier ────────────────────────────────────────────────────────────────
    private void initKeys() {
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) setMode(Mode.SELECTION);
                if (e.getKeyCode() == KeyEvent.VK_DELETE
                        || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) setMode(Mode.SUPPRESSION);
            }
        });
    }
}
