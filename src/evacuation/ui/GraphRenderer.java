package evacuation.ui;

import evacuation.agent.Agent;
import evacuation.graph.DangerZone;
import evacuation.graph.Edge;
import evacuation.graph.Exit;
import evacuation.graph.Graph;
import evacuation.graph.Node;
import evacuation.simulation.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Swing panel that renders the evacuation graph and its agents.
 * Runs a 60 fps animation timer to smoothly move agents along edges.
 * Supports multiple interaction modes for editing the graph.
 */
public class GraphRenderer extends JPanel {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int    NODE_RADIUS    = 24;
    private static final int    AGENT_RADIUS   = 10;
    static final double         SCALE          = 90.0;
    static final int            OFFSET         = 70;
    private static final double MIN_DIST_NODE  = 0.7;

    // ── Color palette ─────────────────────────────────────────────────────────
    static final Color BG              = new Color(15, 20, 32);
    static final Color GRID            = new Color(30, 38, 55);
    static final Color NODE_NORMAL     = new Color(60, 120, 220);
    static final Color NODE_CONGESTION = new Color(20, 50, 160);
    static final Color NODE_EXIT_OPEN  = new Color(40, 200, 110);
    static final Color NODE_EXIT_CLOSED = new Color(90, 100, 120);
    static final Color NODE_DANGER_F   = new Color(220, 60, 30);
    static final Color NODE_DANGER_I   = new Color(40, 100, 210);
    static final Color NODE_DANGER_P   = new Color(180, 40, 180);
    static final Color NODE_THREATENED = new Color(220, 130, 20);
    static final Color EDGE_FREE       = new Color(70, 95, 140);
    static final Color EDGE_CONG       = new Color(220, 60, 30);
    static final Color EDGE_BLOCKED    = new Color(160, 30, 30);
    static final Color EDGE_FIRE       = new Color(230, 50, 20);
    static final Color EDGE_FLOOD      = new Color(100, 120, 150);
    static final Color AGENT_CALM      = new Color(255, 225, 50);
    static final Color AGENT_PANIC     = new Color(255, 120, 30);
    static final Color AGENT_MADNESS   = new Color(210, 50, 210);
    static final Color AGENT_BLOCKED   = new Color(190, 40, 40);
    static final Color AGENT_ARRIVED   = new Color(100, 220, 100);
    static final Color SEL_COLOR       = new Color(0, 215, 255);
    static final Color TEXT            = new Color(205, 215, 235);

    // Deprecated aliases for backward compatibility
    /** @deprecated Use NODE_EXIT_OPEN */
    @Deprecated static final Color NODE_SORTIE_O = NODE_EXIT_OPEN;
    /** @deprecated Use NODE_EXIT_CLOSED */
    @Deprecated static final Color NODE_SORTIE_F = NODE_EXIT_CLOSED;
    /** @deprecated Use AGENT_CALM */
    @Deprecated static final Color AGENT_CALME = AGENT_CALM;
    /** @deprecated Use AGENT_PANIC */
    @Deprecated static final Color AGENT_PANIQUE = AGENT_PANIC;
    /** @deprecated Use AGENT_MADNESS */
    @Deprecated static final Color AGENT_FOLIE = AGENT_MADNESS;
    /** @deprecated Use AGENT_BLOCKED */
    @Deprecated static final Color AGENT_BLOQUE = AGENT_BLOCKED;
    /** @deprecated Use AGENT_ARRIVED */
    @Deprecated static final Color AGENT_ARRIVE = AGENT_ARRIVED;
    /** @deprecated Use EDGE_FIRE */
    @Deprecated static final Color EDGE_FEU = EDGE_FIRE;
    /** @deprecated Use EDGE_FLOOD */
    @Deprecated static final Color EDGE_INONDATION = EDGE_FLOOD;
    /** @deprecated Use NODE_THREATENED */
    @Deprecated static final Color NODE_MENACE = NODE_THREATENED;

    // ── State ─────────────────────────────────────────────────────────────────
    private SimulationEngine engine;
    private Agent   selectedAgent;
    private Node    hoveredNode;
    private Edge    hoveredEdge;
    private Node    draggedNode;
    private Point   dragStart;

    /**
     * Interaction modes for the renderer.
     */
    public enum Mode {
        SELECTION, AJOUT_NOEUD, AJOUT_SORTIE, AJOUT_ARETE, AJOUT_DANGER,
        AJOUT_AGENT, SUPPRESSION, SUPPRESSION_AGENT, SUPPRESSION_ARETE
    }

    private Mode mode = Mode.SELECTION;
    private Node firstEdgeNode;
    private Point mousePos;

    private final Timer animTimer;

    // ── Callbacks ─────────────────────────────────────────────────────────────
    private Consumer<Agent>        onAgentSelected;
    private Consumer<Node>         onNodeSelected;
    private BiConsumer<Node, Node> onEdgeAdded;
    private Consumer<Node>         onNodeAdded;
    private Consumer<Node>         onNodeRemoved;
    private Consumer<Edge>         onEdgeRemoved;

    /**
     * Constructs a GraphRenderer for the given simulation engine.
     *
     * @param engine the simulation engine to render
     */
    public GraphRenderer(SimulationEngine engine) {
        this.engine = engine;
        setBackground(BG);
        setPreferredSize(new Dimension(950, 680));
        setFocusable(true);
        initMouse();
        initKeys();

        animTimer = new Timer(16, ev -> {
            for (Agent a : engine.getAgents()) {
                a.animateMovement(a.getAnimationSpeed());
            }
            repaint();
        });
        animTimer.start();
    }

    // ── Setters for callbacks ─────────────────────────────────────────────────

    //** @param cb callback invoked when an agent is selected *
    public void setOnAgentSelected(Consumer<Agent> cb) { this.onAgentSelected = cb; }
    public void setOnNodeSelected(Consumer<Node> cb) { this.onNodeSelected = cb; }
    public void setOnEdgeAdded(BiConsumer<Node, Node> cb) { this.onEdgeAdded = cb; }
    public void setOnNodeAdded(Consumer<Node> cb) { this.onNodeAdded = cb; }
    public void setOnNodeRemoved(Consumer<Node> cb) { this.onNodeRemoved = cb; }
    public void setOnEdgeRemoved(Consumer<Edge> cb) { this.onEdgeRemoved = cb; }

    // Deprecated French-named setters
    /** @deprecated Use {@link #setOnAgentSelected(Consumer)} */
    @Deprecated public void setOnAgentSelectionne(Consumer<Agent> cb) { setOnAgentSelected(cb); }
    /** @deprecated Use {@link #setOnNodeSelected(Consumer)} */
    @Deprecated public void setOnNoeudSelectionne(Consumer<Node> cb) { setOnNodeSelected(cb); }
    /** @deprecated Use {@link #setOnEdgeAdded(BiConsumer)} */
    @Deprecated public void setOnAreteAjoutee(BiConsumer<Node, Node> cb) { setOnEdgeAdded(cb); }
    /** @deprecated Use {@link #setOnNodeAdded(Consumer)} */
    @Deprecated public void setOnNoeudAjoute(Consumer<Node> cb) { setOnNodeAdded(cb); }
    /** @deprecated Use {@link #setOnNodeRemoved(Consumer)} */
    @Deprecated public void setOnNoeudSupprime(Consumer<Node> cb) { setOnNodeRemoved(cb); }
    /** @deprecated Use {@link #setOnEdgeRemoved(Consumer)} */
    @Deprecated public void setOnAreteSupprimee(Consumer<Edge> cb) { setOnEdgeRemoved(cb); }

    /** @return the current interaction mode */
    public Mode getMode() { return mode; }
    /** @return the currently selected agent, or null */
    public Agent getSelectedAgent() { return selectedAgent; }
    /** @deprecated Use {@link #getSelectedAgent()} */
    @Deprecated public Agent getAgentSel() { return selectedAgent; }

    /**
     * Sets the interaction mode and resets edge-building state.
     *
     * @param m the new mode
     */
    public void setMode(Mode m) {
        this.mode           = m;
        this.firstEdgeNode  = null;
        requestFocusInWindow();
        repaint();
    }

    /** Requests a repaint. */
    public void rafraichir() { repaint(); }

    /** Stops the animation timer. */
    public void stopAnimation() { animTimer.stop(); }

    // ── Main rendering ────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);

        drawGrid(g2);
        drawEdges(g2);
        drawEdgePreview(g2);
        drawAgentPath(g2);
        drawNodes(g2);
        drawAgents(g2);
        drawModeBar(g2);
    }

    // ── Grid ──────────────────────────────────────────────────────────────────

    private void drawGrid(Graphics2D g) {
        g.setColor(GRID);
        g.setStroke(new BasicStroke(0.5f));
        for (int x = OFFSET % 40; x < getWidth();  x += 40) g.drawLine(x, 0, x, getHeight());
        for (int y = OFFSET % 40; y < getHeight(); y += 40) g.drawLine(0, y, getWidth(), y);
    }

    // ── Edges ─────────────────────────────────────────────────────────────────

    private void drawEdges(Graphics2D g) {
        int maxCong = Math.max(1, engine.getMaxEdgeCongestion());
        for (Edge e : engine.getGraph().getEdges()) {
            int x1 = sx(e.getSource().getX()),      y1 = sy(e.getSource().getY());
            int x2 = sx(e.getDestination().getX()), y2 = sy(e.getDestination().getY());

            if (!e.isAvailable()) {
                Color blockColor;
                switch (e.getBlockType()) {
                    case FIRE  -> blockColor = EDGE_FIRE;
                    case FLOOD -> blockColor = EDGE_FLOOD;
                    default    -> blockColor = EDGE_BLOCKED;
                }
                g.setColor(blockColor);
                g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1, new float[]{6, 4}, 0));
                g.drawLine(x1, y1, x2, y2);
                g.setStroke(new BasicStroke(1f));

                String sym = e.getBlockType() == Edge.BlockType.FIRE ? "🔥" : "🌊";
                g.setFont(new Font("SansSerif", Font.PLAIN, 11));
                FontMetrics fm = g.getFontMetrics();
                int mx = (x1+x2)/2, my = (y1+y2)/2;
                g.setColor(new Color(15,20,32,180));
                g.fillRoundRect(mx-10, my-12, 20, 16, 4, 4);
                g.setColor(blockColor);
                g.drawString(sym, mx - fm.stringWidth(sym)/2, my);
                continue;
            }

            float ratio   = Math.min(1f, (float) e.getAgentsInTransit() / e.getCapacity());
            Color color   = e.equals(hoveredEdge) ? SEL_COLOR : blend(EDGE_FREE, EDGE_CONG, ratio);
            float stroke  = 3.5f + ratio * 5f;

            g.setColor(color);
            g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x1, y1, x2, y2);
            g.setStroke(new BasicStroke(1f));

            // Draw arrow for directed edges
            if (e.isDirected()) {
                drawArrow(g, x1, y1, x2, y2, color);
            }

            // Draw speed modifier indicator
            if (e.getSpeedModifier() != 1.0) {
                String speedStr = String.format("×%.1f", e.getSpeedModifier());
                int mx = (x1+x2)/2, my = (y1+y2)/2 + 12;
                labelBg(g, speedStr, mx, my, new Font("SansSerif", Font.PLAIN, 9),
                        e.getSpeedModifier() > 1.0 ? AGENT_ARRIVED : AGENT_PANIC,
                        new Color(15, 20, 32, 180));
            }

            labelBg(g, String.format("%.0f", e.getBaseWeight()), (x1+x2)/2+5, (y1+y2)/2-5,
                    new Font("SansSerif", Font.PLAIN, 10), TEXT, new Color(15, 20, 32, 180));
        }
    }

    private void drawArrow(Graphics2D g, int x1, int y1, int x2, int y2, Color color) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int mx = (x1 + x2) / 2;
        int my = (y1 + y2) / 2;
        int arrowLen = 10;
        double arrowAngle = Math.PI / 6;
        int ax1 = mx - (int)(arrowLen * Math.cos(angle - arrowAngle));
        int ay1 = my - (int)(arrowLen * Math.sin(angle - arrowAngle));
        int ax2 = mx - (int)(arrowLen * Math.cos(angle + arrowAngle));
        int ay2 = my - (int)(arrowLen * Math.sin(angle + arrowAngle));
        g.setColor(color);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(mx, my, ax1, ay1);
        g.drawLine(mx, my, ax2, ay2);
        g.setStroke(new BasicStroke(1f));
    }

    // ── Edge preview (during AJOUT_ARETE) ────────────────────────────────────

    private void drawEdgePreview(Graphics2D g) {
        if (mode != Mode.AJOUT_ARETE || firstEdgeNode == null || mousePos == null) return;
        g.setColor(new Color(0, 215, 255, 100));
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1, new float[]{9, 5}, 0));
        g.drawLine(sx(firstEdgeNode.getX()), sy(firstEdgeNode.getY()), mousePos.x, mousePos.y);
        g.setStroke(new BasicStroke(1f));
    }

    // ── Selected agent path ───────────────────────────────────────────────────

    private void drawAgentPath(Graphics2D g) {
        if (selectedAgent == null) return;
        List<Node> ch = selectedAgent.getPath();
        if (ch == null || ch.size() < 2) return;
        g.setColor(new Color(0, 215, 255, 140));
        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int start = Math.max(0, ch.indexOf(selectedAgent.getPosition()));
        for (int i = start; i < ch.size() - 1; i++)
            g.drawLine(sx(ch.get(i).getX()), sy(ch.get(i).getY()),
                       sx(ch.get(i+1).getX()), sy(ch.get(i+1).getY()));
        g.setStroke(new BasicStroke(1f));
    }

    // ── Nodes ─────────────────────────────────────────────────────────────────

    private void drawNodes(Graphics2D g) {
        int[] density  = engine.getDensityPerNode();
        int   maxDen   = Math.max(1, engine.getMaxDensity());
        List<Node> nodes = engine.getGraph().getNodes();

        // Collect threatened nodes
        java.util.Set<Node> threatened = new java.util.HashSet<>();
        for (Node n : nodes) {
            if (n instanceof DangerZone) {
                threatened.addAll(((DangerZone) n).getNodesToBlock());
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            Node n   = nodes.get(i);
            int  x   = sx(n.getX()), y = sy(n.getY());
            int  d   = i < density.length ? density[i] : 0;
            float ratio = Math.min(1f, (float) d / maxDen);

            boolean hovered = n.equals(hoveredNode);
            boolean first   = n.equals(firstEdgeNode);

            Color base;
            if (n instanceof DangerZone) {
                DangerZone.DangerType t = ((DangerZone) n).getDangerType();
                base = t == DangerZone.DangerType.FIRE         ? NODE_DANGER_F
                     : t == DangerZone.DangerType.FLOOD        ? NODE_DANGER_I : NODE_DANGER_P;
            } else if (n instanceof Exit) {
                base = ((Exit) n).isOpen() ? NODE_EXIT_OPEN : NODE_EXIT_CLOSED;
            } else if (n.isBlocked()) {
                base = new Color(55, 55, 65);
            } else if (threatened.contains(n)) {
                base = NODE_THREATENED;
            } else {
                base = blend(NODE_NORMAL, NODE_CONGESTION, ratio * 0.85f);
            }

            g.setColor(new Color(0, 0, 0, 70));
            g.fillOval(x-NODE_RADIUS+3, y-NODE_RADIUS+4, NODE_RADIUS*2, NODE_RADIUS*2);

            Paint old = g.getPaint();
            g.setPaint(new GradientPaint(x-NODE_RADIUS, y-NODE_RADIUS, base.brighter(),
                                          x+NODE_RADIUS, y+NODE_RADIUS, base.darker().darker()));
            g.fillOval(x-NODE_RADIUS, y-NODE_RADIUS, NODE_RADIUS*2, NODE_RADIUS*2);
            g.setPaint(old);

            float sw; Color bc;
            if      (first)   { bc = SEL_COLOR;  sw = 3.5f; }
            else if (hovered) { bc = Color.WHITE; sw = 2.5f; }
            else              { bc = new Color(0, 0, 0, 100); sw = 1.5f; }
            g.setColor(bc); g.setStroke(new BasicStroke(sw));
            g.drawOval(x-NODE_RADIUS, y-NODE_RADIUS, NODE_RADIUS*2, NODE_RADIUS*2);
            g.setStroke(new BasicStroke(1f));

            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            String sym = null;
            if (n instanceof DangerZone) {
                DangerZone.DangerType t = ((DangerZone) n).getDangerType();
                sym = t == DangerZone.DangerType.FIRE ? "🔥"
                    : t == DangerZone.DangerType.FLOOD ? "🌊" : "⚠";
            } else if (n instanceof Exit) sym = "⬖";
            if (sym != null) {
                g.setColor(Color.WHITE);
                g.drawString(sym, x - fm.stringWidth(sym)/2, y + fm.getAscent()/2 - 2);
            }

            labelBg(g, n.getId(), x, y+NODE_RADIUS+14,
                    new Font("SansSerif", Font.BOLD, 11), TEXT, new Color(15, 20, 32, 160));

            // Tooltip with stats when hovered
            if (hovered) {
                String stats = String.format("cap:%d pass:%d", n.getCapacity(), n.getAgentsPassed());
                labelBg(g, stats, x, y - NODE_RADIUS - 8,
                        new Font("SansSerif", Font.PLAIN, 9), SEL_COLOR, new Color(15, 20, 32, 200));
            }

            int total = countAgentsAtNode(n);
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

    private int countAgentsAtNode(Node n) {
        int count = 0;
        for (Agent a : engine.getAgents())
            if (a.getPosition().equals(n)) count++;
        return count;
    }

    // ── Agents ────────────────────────────────────────────────────────────────

    private void drawAgents(Graphics2D g) {
        List<Agent> agents = engine.getAgents();
        for (int idx = 0; idx < agents.size(); idx++) {
            Agent a = agents.get(idx);

            int ax = sx(a.getDisplayX());
            int ay = sy(a.getDisplayY());

            int slot = idx % 8;
            int dx = (int)(Math.cos(slot * Math.PI / 4) * 15);
            int dy = (int)(Math.sin(slot * Math.PI / 4) * 15);

            Color col;
            if (a.getMovementState() == Agent.MovementState.ARRIVED) {
                col = AGENT_ARRIVED;
            } else {
                col = switch (a.getPsychologicalState()) {
                    case PANIC   -> AGENT_PANIC;
                    case MADNESS -> AGENT_MADNESS;
                    default -> switch (a.getMovementState()) {
                        case MOVING  -> AGENT_CALM;
                        case BLOCKED -> AGENT_BLOCKED;
                        default      -> new Color(170, 180, 205);
                    };
                };
            }

            boolean sel = a.equals(selectedAgent);
            int r = sel ? AGENT_RADIUS + 4 : AGENT_RADIUS;

            if (sel) {
                g.setColor(new Color(0, 215, 255, 50));
                g.fillOval(ax+dx-r-5, ay+dy-r-5, (r+5)*2, (r+5)*2);
            }

            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval(ax+dx-r+2, ay+dy-r+3, r*2, r*2);

            Paint old = g.getPaint();
            g.setPaint(new GradientPaint(ax+dx-r, ay+dy-r, col.brighter(),
                                          ax+dx+r, ay+dy+r, col.darker()));
            g.fillOval(ax+dx-r, ay+dy-r, r*2, r*2);
            g.setPaint(old);

            g.setColor(sel ? SEL_COLOR : new Color(0, 0, 0, 120));
            g.setStroke(new BasicStroke(sel ? 2.5f : 1f));
            g.drawOval(ax+dx-r, ay+dy-r, r*2, r*2);
            g.setStroke(new BasicStroke(1f));

            if (a.getMovementState() == Agent.MovementState.ARRIVED) {
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                g.drawString("✓", ax+dx-3, ay+dy+4);
            }

            labelBg(g, a.getId(), ax+dx+r+3, ay+dy+4,
                    new Font("SansSerif", Font.BOLD, 9), TEXT, new Color(15, 20, 32, 160));
        }
    }

    // ── Mode banner ───────────────────────────────────────────────────────────

    private void drawModeBar(Graphics2D g) {
        String txt;
        Color accent;
        switch (mode) {
            case AJOUT_NOEUD       -> { txt = "NOEUD - clic pour placer";                         accent = new Color(60,180,100); }
            case AJOUT_SORTIE      -> { txt = "SORTIE - clic pour placer";                        accent = NODE_EXIT_OPEN; }
            case AJOUT_ARETE       -> { txt = firstEdgeNode == null
                                             ? "ARETE - clic sur le 1er noeud"
                                             : "ARETE - clic sur le 2e noeud";                    accent = SEL_COLOR; }
            case AJOUT_DANGER      -> { txt = "DANGER - clic pour placer (actif en simulation)";  accent = NODE_DANGER_F; }
            case AJOUT_AGENT       -> { txt = "AGENT - clic sur un noeud pour ajouter";           accent = AGENT_CALM; }
            case SUPPRESSION       -> { txt = "SUPPRIMER - clic sur noeud ou arete";              accent = AGENT_BLOCKED; }
            case SUPPRESSION_AGENT -> { txt = "SUPPRIMER AGENT - clic sur un agent";              accent = AGENT_PANIC; }
            case SUPPRESSION_ARETE -> { txt = "SUPPRIMER ARETE - clic sur une arete";             accent = EDGE_FIRE; }
            default                -> { txt = "SELECTION - clic droit sur noeud pour options";        accent = TEXT; }
        }
        Font bannerFont = new Font("Dialog", Font.BOLD, 12);
        g.setFont(bannerFont);
        FontMetrics fm = g.getFontMetrics(bannerFont);
        int tw = fm.stringWidth(txt);
        int px = getWidth()/2 - tw/2 - 14, py = 8;
        g.setColor(new Color(15, 20, 32, 215));
        g.fillRoundRect(px, py, tw+28, 26, 12, 12);
        g.setColor(accent.darker());
        g.drawRoundRect(px, py, tw+28, 26, 12, 12);
        g.setColor(accent);
        g.drawString(txt, px+14, py+18);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    int    sx(double gx) { return (int)(gx * SCALE + OFFSET); }
    int    sy(double gy) { return (int)(gy * SCALE + OFFSET); }
    double gx(int   sx)  { return (sx - OFFSET) / SCALE; }
    double gy(int   sy)  { return (sy - OFFSET) / SCALE; }

    /**
     * Blends two colors by a ratio t (0.0=a, 1.0=b).
     *
     * @param a first color
     * @param b second color
     * @param t blend factor [0,1]
     * @return blended color
     */
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

    // ── Hit tests ─────────────────────────────────────────────────────────────

    Node nodeAt(int mx, int my) {
        int r2 = (NODE_RADIUS+5) * (NODE_RADIUS+5);
        for (Node n : engine.getGraph().getNodes()) {
            int ddx = mx - sx(n.getX()), ddy = my - sy(n.getY());
            if (ddx*ddx + ddy*ddy <= r2) return n;
        }
        return null;
    }

    Agent agentAt(int mx, int my) {
        int threshold2 = (AGENT_RADIUS+8) * (AGENT_RADIUS+8);
        for (Agent a : engine.getAgents()) {
            int ddx = mx - sx(a.getDisplayX()), ddy = my - sy(a.getDisplayY());
            if (ddx*ddx + ddy*ddy <= threshold2) return a;
        }
        return null;
    }

    Edge edgeAt(int mx, int my) {
        for (Edge e : engine.getGraph().getEdges()) {
            double dist = ptSegDist(mx, my,
                sx(e.getSource().getX()),       sy(e.getSource().getY()),
                sx(e.getDestination().getX()),  sy(e.getDestination().getY()));
            if (dist < 10) return e;
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

    // ── Context menus ─────────────────────────────────────────────────────────

    private void showNodeContextMenu(Node n, int mx, int my) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(25, 32, 50));

        JLabel title = new JLabel("  " + n.getId() + "  (" + n.getClass().getSimpleName() + ")  ");
        title.setForeground(SEL_COLOR);
        title.setFont(new Font("SansSerif", Font.BOLD, 12));
        menu.add(title);
        menu.addSeparator();

        // Stats display
        JLabel statsLabel = new JLabel("  Passages: " + n.getAgentsPassed()
                + " | Cap: " + n.getCapacity() + "  ");
        statsLabel.setForeground(TEXT);
        statsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        menu.add(statsLabel);
        menu.addSeparator();

        JMenuItem del = menuItem("✖ Supprimer ce nœud", AGENT_BLOCKED);
        del.addActionListener(e -> {
            engine.onNodeRemoved(n);
            engine.getGraph().removeNode(n);
            if (onNodeRemoved != null) onNodeRemoved.accept(n);
            repaint();
        });
        menu.add(del);
        menu.addSeparator();

        if (!(n instanceof Exit) && !(n instanceof DangerZone)) {
            JMenuItem toExit = menuItem("🚪 Convertir en Sortie", NODE_EXIT_OPEN);
            toExit.addActionListener(e -> convertToExit(n));
            menu.add(toExit);
            JMenu dm = new JMenu("⚠ Convertir en danger…");
            dm.setForeground(NODE_DANGER_F);
            for (DangerZone.DangerType t : DangerZone.DangerType.values()) {
                JMenuItem di = new JMenuItem(t.name());
                di.addActionListener(ev -> convertToDanger(n, t));
                dm.add(di);
            }
            menu.add(dm);
        }
        if (n instanceof Exit) {
            Exit ex = (Exit) n;
            JMenuItem tog = menuItem(ex.isOpen() ? "🔒 Fermer" : "🔓 Ouvrir",
                                     ex.isOpen() ? AGENT_BLOCKED : NODE_EXIT_OPEN);
            tog.addActionListener(e -> { if (ex.isOpen()) ex.close(); else ex.open(); repaint(); });
            menu.add(tog);
            menu.add(menuItemAction("↩ En nœud normal", NODE_NORMAL, () -> convertToNormal(n)));
        }
        if (n instanceof DangerZone) {
            menu.add(menuItemAction("↩ Supprimer le danger", NODE_NORMAL, () -> convertToNormal(n)));
        }

        menu.addSeparator();
        JMenuItem addAg = menuItem("👤 Ajouter un agent ici", AGENT_CALM);
        addAg.addActionListener(e -> showAddAgentDialog(n));
        menu.add(addAg);

        menu.show(this, mx, my);
    }

    private void showAgentContextMenu(Agent a, int mx, int my) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(new Color(25, 32, 50));
        JLabel title = new JLabel("  Agent " + a.getId() + "  ");
        title.setForeground(AGENT_CALM);
        title.setFont(new Font("SansSerif", Font.BOLD, 12));
        menu.add(title);
        menu.addSeparator();
        menu.add(menuItemAction("✖ Supprimer cet agent", AGENT_BLOCKED, () -> {
            engine.removeAgent(a);
            if (selectedAgent == a) {
                selectedAgent = null;
                if (onAgentSelected != null) onAgentSelected.accept(null);
            }
            repaint();
        }));
        menu.show(this, mx, my);
    }

    private JMenuItem menuItem(String txt, Color fg) {
        JMenuItem it = new JMenuItem(txt);
        it.setForeground(fg); it.setBackground(new Color(25, 32, 50));
        it.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return it;
    }

    private JMenuItem menuItemAction(String txt, Color fg, Runnable action) {
        JMenuItem it = menuItem(txt, fg);
        it.addActionListener(e -> action.run());
        return it;
    }

    // ── Node type conversions ─────────────────────────────────────────────────

    private void convertToExit(Node n) {
        replaceNode(n, new Exit(engine.getGraph().generateUniqueId("S"), n.getX(), n.getY(), 10));
    }

    private void convertToDanger(Node n, DangerZone.DangerType type) {
        replaceNode(n, new DangerZone(engine.getGraph().generateUniqueId("Z"), n.getX(), n.getY(), type, 8));
        if (engine.isRunning()) engine.notifyGraphChanged();
    }

    private void convertToNormal(Node n) {
        replaceNode(n, new Node(engine.getGraph().generateUniqueId("N"), n.getX(), n.getY()));
    }

    private void replaceNode(Node old, Node replacement) {
        Graph gr = engine.getGraph();
        List<Edge> edges = new java.util.ArrayList<>(gr.getEdgesOfNode(old));
        for (Edge e : edges) gr.removeEdge(e);
        gr.removeNode(old);
        gr.addNode(replacement);
        for (Edge e : edges) {
            Node src  = e.getSource().equals(old)      ? replacement : e.getSource();
            Node dest = e.getDestination().equals(old) ? replacement : e.getDestination();
            gr.addEdge(new Edge(src, dest, e.getBaseWeight()));
        }
        for (Agent a : engine.getAgents()) {
            if (a.getPosition().equals(old))    a.teleportTo(replacement);
            if (old.equals(a.getDestination())) { a.setDestination(replacement); a.recalculatePath(); }
        }
        repaint();
    }

    // ── Add agent dialog ──────────────────────────────────────────────────────

    /**
     * Opens a dialog to add an agent at the specified starting node.
     *
     * @param startNode the node where the agent will start
     */
    void showAddAgentDialog(Node startNode) {
        List<Node> exits = new java.util.ArrayList<>();
        for (Node n : engine.getGraph().getNodes())
            if (n instanceof Exit && !n.isBlocked()) exits.add(n);
        if (exits.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Aucune sortie disponible.", "Impossible",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this),
                "Ajouter un agent", true);
        dlg.setLayout(new GridBagLayout());
        dlg.getContentPane().setBackground(new Color(22, 28, 44));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 10, 5, 10);
        gc.fill   = GridBagConstraints.HORIZONTAL;

        String[] destLabels = exits.stream().map(Node::getId).toArray(String[]::new);
        JComboBox<String> comboDest    = new JComboBox<>(destLabels);
        JComboBox<String> comboPsycho  = new JComboBox<>(new String[]{"CALM","PANIC","MADNESS"});
        JComboBox<String> comboBehav   = new JComboBox<>(new String[]{"YIELD","PRIORITY","FOLLOW"});
        JComboBox<String> comboMode    = new JComboBox<>(new String[]{"FIXED","RANDOM_WALK",
                "FLEE_DESTINATION","TOWARD_DENSE","FLEE_DENSITY"});
        JComboBox<String> comboArrival = new JComboBox<>(new String[]{"STOP","RANDOM_DESTINATION","DELETE"});
        JSpinner spinVit = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));

        Object[][] rows = {
            {"Départ :", new JLabel(startNode.getId())},
            {"Destination :", comboDest},
            {"État psycho :", comboPsycho},
            {"Comportement :", comboBehav},
            {"Mode destination :", comboMode},
            {"À l'arrivée :", comboArrival},
            {"Vitesse :", spinVit},
        };
        for (int i = 0; i < rows.length; i++) {
            gc.gridx=0; gc.gridy=i;
            JLabel lbl = new JLabel((String) rows[i][0]); lbl.setForeground(TEXT);
            dlg.add(lbl, gc); gc.gridx=1;
            dlg.add((Component) rows[i][1], gc);
        }

        JButton ok = new JButton("Ajouter"), cancel = new JButton("Annuler");
        JPanel btns = new JPanel(); btns.setBackground(new Color(22, 28, 44));
        btns.add(ok); btns.add(cancel);
        gc.gridx=0; gc.gridy=rows.length; gc.gridwidth=2;
        dlg.add(btns, gc);

        ok.addActionListener(e -> {
            Node dest = exits.get(comboDest.getSelectedIndex());
            Agent.PsychologicalState psycho = Agent.PsychologicalState.valueOf(
                    (String) comboPsycho.getSelectedItem());
            Agent.Behavior behav = Agent.Behavior.valueOf(
                    (String) comboBehav.getSelectedItem());
            Agent.DestinationMode destMode = Agent.DestinationMode.valueOf(
                    (String) comboMode.getSelectedItem());
            Agent.ArrivalBehavior arrival = Agent.ArrivalBehavior.valueOf(
                    (String) comboArrival.getSelectedItem());
            String agId = engine.getGraph().generateUniqueId("AG");
            Agent ag = new Agent(agId, startNode, dest, engine.getGraph(),
                    (int) spinVit.getValue(), 1.0, behav, psycho, destMode);
            ag.setArrivalBehavior(arrival);
            engine.addAgent(ag);
            if (engine.isRunning()) ag.initialize();
            System.out.println("[UI] Agent " + agId + " added at " + startNode.getId());
            dlg.dispose();
            repaint();
        });
        cancel.addActionListener(e -> dlg.dispose());
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // Deprecated alias
    /** @deprecated Use {@link #showAddAgentDialog(Node)} */
    @Deprecated void dialogAjoutAgent(Node n) { showAddAgentDialog(n); }

    // ── Mouse handling ────────────────────────────────────────────────────────

    private void initMouse() {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e)  { handleClick(e); }
            @Override public void mousePressed(MouseEvent e)  {
                if (mode == Mode.SELECTION && !SwingUtilities.isRightMouseButton(e)) {
                    draggedNode = nodeAt(e.getX(), e.getY());
                    if (draggedNode != null) dragStart = e.getPoint();
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                draggedNode = null; dragStart = null;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                mousePos = e.getPoint();
                Node nn = nodeAt(e.getX(), e.getY());
                Edge ne = nn == null ? edgeAt(e.getX(), e.getY()) : null;
                hoveredNode = nn;
                hoveredEdge = ne;
            }

            @Override public void mouseDragged(MouseEvent e) {
                mousePos = e.getPoint();
                if (mode == Mode.SELECTION && draggedNode != null && dragStart != null) {
                    double nx = gx(e.getX()), ny = gy(e.getY());
                    boolean conflict = false;
                    for (Node other : engine.getGraph().getNodes()) {
                        if (other.equals(draggedNode)) continue;
                        if (Math.hypot(other.getX()-nx, other.getY()-ny) < MIN_DIST_NODE) {
                            conflict = true; break;
                        }
                    }
                    if (!conflict) {
                        draggedNode.setX(nx);
                        draggedNode.setY(ny);
                        for (Agent a : engine.getAgents()) {
                            if (a.getPosition().equals(draggedNode) && a.getTargetNode() == null)
                                a.syncDisplayWithNode();
                        }
                    }
                }
            }
        });
    }

    private void handleClick(MouseEvent e) {
        int mx = e.getX(), my = e.getY();

        if (SwingUtilities.isRightMouseButton(e)) {
            Agent a = agentAt(mx, my);
            if (a != null) { showAgentContextMenu(a, mx, my); return; }
            Node n = nodeAt(mx, my);
            if (n != null) { showNodeContextMenu(n, mx, my); return; }
            if (mode == Mode.SELECTION) {
                Edge edge = edgeAt(mx, my);
                if (edge != null && onEdgeRemoved != null) {
                    // Show edge stats in tooltip area (log)
                    System.out.println("[Edge] " + edge.getSource().getId() + "→"
                            + edge.getDestination().getId()
                            + " | passages=" + edge.getAgentsPassed()
                            + " | speed=" + edge.getSpeedModifier()
                            + " | directed=" + edge.isDirected());
                }
            }
            return;
        }

        switch (mode) {
            case SELECTION -> {
                Agent a = agentAt(mx, my);
                if (a != null) {
                    selectedAgent = a.equals(selectedAgent) ? null : a;
                    if (onAgentSelected != null) onAgentSelected.accept(selectedAgent);
                } else {
                    Node n = nodeAt(mx, my);
                    if (n != null) {
                        if (onNodeSelected != null) onNodeSelected.accept(n);
                    } else {
                        selectedAgent = null;
                        if (onAgentSelected != null) onAgentSelected.accept(null);
                    }
                }
            }

            case AJOUT_NOEUD -> {
                double nx = gx(mx), ny = gy(my);
                if (engine.getGraph().isTooClose(nx, ny, MIN_DIST_NODE)) {
                    JOptionPane.showMessageDialog(this, "Trop proche d'un nœud existant.",
                            "Invalide", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String capStr = JOptionPane.showInputDialog(this,
                        "Capacité du nœud (nombre max d'agents, défaut 3) :", "3");
                if (capStr == null) return;
                int cap = 3;
                try { cap = Math.max(1, Integer.parseInt(capStr.trim())); } catch (NumberFormatException ignored) {}
                Node n = new Node(engine.getGraph().generateUniqueId("N"), nx, ny);
                n.setCapacity(cap);
                engine.getGraph().addNode(n);
                if (onNodeAdded != null) onNodeAdded.accept(n);
            }

            case AJOUT_SORTIE -> {
                double nx = gx(mx), ny = gy(my);
                if (engine.getGraph().isTooClose(nx, ny, MIN_DIST_NODE)) {
                    JOptionPane.showMessageDialog(this, "Trop proche d'un nœud existant.",
                            "Invalide", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Exit ex = new Exit(engine.getGraph().generateUniqueId("S"), nx, ny, 10);
                engine.getGraph().addNode(ex);
                if (onNodeAdded != null) onNodeAdded.accept(ex);
            }

            case AJOUT_DANGER -> {
                double nx = gx(mx), ny = gy(my);
                if (engine.getGraph().isTooClose(nx, ny, MIN_DIST_NODE)) {
                    JOptionPane.showMessageDialog(this, "Trop proche d'un nœud existant.",
                            "Invalide", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String[] types = {"FIRE — se propage", "FLOOD — bloque arêtes",
                                  "DANGEROUS_PERSON — ralentit"};
                int choice = JOptionPane.showOptionDialog(this, "Type de danger ?", "Zone de danger",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                        null, types, types[0]);
                if (choice < 0) return;
                DangerZone z = new DangerZone(engine.getGraph().generateUniqueId("Z"),
                        nx, ny, DangerZone.DangerType.values()[choice], 8);
                engine.getGraph().addNode(z);
                if (onNodeAdded != null) onNodeAdded.accept(z);
                if (engine.isRunning()) {
                    engine.notifyGraphChanged();
                    System.out.println("[UI] Danger added during simulation — path recalculation");
                }
            }

            case AJOUT_AGENT -> {
                Node n = nodeAt(mx, my);
                if (n != null) showAddAgentDialog(n);
                else JOptionPane.showMessageDialog(this,
                        "Cliquez sur un nœud existant.", "Info",
                        JOptionPane.INFORMATION_MESSAGE);
            }

            case AJOUT_ARETE -> {
                Node clicked = nodeAt(mx, my);
                if (clicked == null) return;
                if (firstEdgeNode == null) {
                    firstEdgeNode = clicked;
                } else if (!firstEdgeNode.equals(clicked)) {
                    if (!engine.getGraph().edgeExists(firstEdgeNode, clicked)) {
                        if (onEdgeAdded != null) onEdgeAdded.accept(firstEdgeNode, clicked);
                    } else {
                        JOptionPane.showMessageDialog(this, "Cette arête existe déjà.", "Info",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                    firstEdgeNode = null;
                } else {
                    firstEdgeNode = null;
                }
            }

            case SUPPRESSION -> {
                Node n = nodeAt(mx, my);
                if (n != null) {
                    engine.onNodeRemoved(n);
                    engine.getGraph().removeNode(n);
                    if (onNodeRemoved != null) onNodeRemoved.accept(n);
                    if (engine.isRunning()) engine.notifyGraphChanged();
                } else {
                    Edge ar = edgeAt(mx, my);
                    if (ar != null) {
                        engine.getGraph().removeEdge(ar);
                        if (onEdgeRemoved != null) onEdgeRemoved.accept(ar);
                        if (engine.isRunning()) engine.notifyGraphChanged();
                    }
                }
            }

            case SUPPRESSION_AGENT -> {
                Agent a = agentAt(mx, my);
                if (a != null) {
                    engine.removeAgent(a);
                    if (a.equals(selectedAgent)) {
                        selectedAgent = null;
                        if (onAgentSelected != null) onAgentSelected.accept(null);
                    }
                }
            }

            case SUPPRESSION_ARETE -> {
                Edge ar = edgeAt(mx, my);
                if (ar != null) {
                    engine.getGraph().removeEdge(ar);
                    if (onEdgeRemoved != null) onEdgeRemoved.accept(ar);
                    if (engine.isRunning()) engine.notifyGraphChanged();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Cliquez précisément sur une arête.", "Info",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
        repaint();
    }

    // ── Keyboard ─────────────────────────────────────────────────────────────

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
