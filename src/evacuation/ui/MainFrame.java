package evacuation.ui;

import evacuation.agent.Agent;
import evacuation.graph.DangerZone;
import evacuation.graph.Edge;
import evacuation.graph.Exit;
import evacuation.graph.Graph;
import evacuation.graph.Node;
import evacuation.simulation.SimulationEngine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The main application window for the evacuation simulation.
 * Contains the toolbar, graph renderer, right info panel, and log panel.
 */
public class MainFrame extends JFrame {

    private static final Color BG_PANEL = new Color(22, 28, 44);
    private static final Color BG_DARK  = new Color(15, 20, 32);
    private static final Color ACCENT   = new Color(60, 130, 220);
    private static final Color ACCENT2  = new Color(40, 200, 110);
    private static final Color TXT      = new Color(205, 215, 235);
    private static final Color TXT_DIM  = new Color(130, 145, 175);

    private SimulationEngine engine;
    private GraphRenderer    renderer;
    private JDialog legendDialog;

    private JButton  btnInit, btnTick, btnPlay;
    private JSlider  sliderSpeed;
    private JLabel   lblSpeed;
    private Timer    autoTimer;

    private JLabel lblTick, lblMovement, lblPsycho, lblAgentInfo, lblNodeInfo;

    private ButtonGroup   toolGroup;
    private JToggleButton btnSel, btnNode, btnExit, btnEdge, btnDanger,
                          btnAddAgent, btnDelete, btnDeleteAgent, btnDeleteEdge;

    private JTextArea logArea;
    private Agent selectedAgent;
    private Node  selectedNode;

    /**
     * Constructs the main application window.
     *
     * @param engine the simulation engine to display
     */
    public MainFrame(SimulationEngine engine) {
        this.engine   = engine;
        this.renderer = new GraphRenderer(engine);
        renderer.setOnAgentSelected(this::onAgentSelected);
        renderer.setOnNodeSelected(this::onNodeSelected);
        renderer.setOnEdgeAdded(this::onEdgeAdded);
        renderer.setOnNodeAdded(n    -> { log("Nœud ajouté : " + n.getId()); renderer.rafraichir(); });
        renderer.setOnNodeRemoved(n  -> { log("Nœud supprimé : " + n.getId()); renderer.rafraichir(); });
        renderer.setOnEdgeRemoved(e  -> { log("Arête supprimée."); renderer.rafraichir(); });

        setTitle("Simulation d'Évacuation");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 0));

        add(buildToolbar(),    BorderLayout.NORTH);
        add(renderer,          BorderLayout.CENTER);
        add(buildRightPanel(), BorderLayout.EAST);
        add(buildBottomPanel(), BorderLayout.SOUTH);
        buildMenuBar();

        pack();
        setMinimumSize(new Dimension(1150, 720));
        setLocationRelativeTo(null);
        setVisible(true);
        redirectLog();
        renderer.requestFocusInWindow();
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private JPanel buildToolbar() {
        // Wrapper: two rows stacked vertically
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(BG_PANEL);
        wrapper.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 55, 85)));

        // ── Row 1: tool buttons ───────────────────────────────────────────────
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        row1.setBackground(BG_PANEL);

        toolGroup = new ButtonGroup();

        btnSel        = toolBtn("Sel.",          "Selectionner / deplacer noeuds",              GraphRenderer.NODE_NORMAL.darker());
        btnNode       = toolBtn("Noeud",     "Ajouter un noeud",                             new Color(60, 180, 100));
        btnExit       = toolBtn("Sortie",    "Ajouter une sortie",                          GraphRenderer.NODE_EXIT_OPEN);
        btnEdge       = toolBtn("Arete",     "Relier 2 noeuds",                             GraphRenderer.SEL_COLOR.darker());
        btnDanger     = toolBtn("Danger",    "Ajouter un danger (fonctionne en direct !)",  GraphRenderer.NODE_DANGER_F);
        btnAddAgent   = toolBtn("Agent",     "Ajouter un agent",                            GraphRenderer.AGENT_CALM.darker());
        btnDelete      = toolBtn("Noeud",   "Supprimer un noeud (ou arete)",                new Color(180, 40, 40));
        btnDeleteAgent = toolBtn("Agent",   "Supprimer un agent",                           new Color(200, 80, 30));
        btnDeleteEdge  = toolBtn("Arete",   "Supprimer une arete",                          new Color(200, 100, 30));

        for (JToggleButton b : new JToggleButton[]{
                btnSel, btnNode, btnExit, btnEdge, btnDanger,
                btnAddAgent, btnDelete, btnDeleteAgent, btnDeleteEdge}) {
            toolGroup.add(b);
            row1.add(b);
        }
        btnSel.setSelected(true);

        btnSel.addActionListener(e         -> renderer.setMode(GraphRenderer.Mode.SELECTION));
        btnNode.addActionListener(e        -> renderer.setMode(GraphRenderer.Mode.AJOUT_NOEUD));
        btnExit.addActionListener(e        -> renderer.setMode(GraphRenderer.Mode.AJOUT_SORTIE));
        btnEdge.addActionListener(e        -> renderer.setMode(GraphRenderer.Mode.AJOUT_ARETE));
        btnDanger.addActionListener(e      -> renderer.setMode(GraphRenderer.Mode.AJOUT_DANGER));
        btnAddAgent.addActionListener(e    -> renderer.setMode(GraphRenderer.Mode.AJOUT_AGENT));
        btnDelete.addActionListener(e      -> renderer.setMode(GraphRenderer.Mode.SUPPRESSION));
        btnDeleteAgent.addActionListener(e -> renderer.setMode(GraphRenderer.Mode.SUPPRESSION_AGENT));
        btnDeleteEdge.addActionListener(e  -> renderer.setMode(GraphRenderer.Mode.SUPPRESSION_ARETE));

        wrapper.add(row1);

        // ── Row 2: simulation controls ────────────────────────────────────────
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));
        row2.setBackground(BG_PANEL);

        btnInit = simBtn("Init",       ACCENT);
        btnTick = simBtn("Tick +1",    new Color(80, 160, 220));
        btnPlay = simBtn("Demarrer",   ACCENT2);
        btnTick.setEnabled(false);
        btnPlay.setEnabled(false);

        btnInit.addActionListener(this::onInit);
        btnTick.addActionListener(e -> doTick());
        btnPlay.addActionListener(this::onPlay);

        row2.add(btnInit); row2.add(btnTick); row2.add(btnPlay);

        sliderSpeed = new JSlider(100, 3000, 1200);
        sliderSpeed.setInverted(true);
        sliderSpeed.setPreferredSize(new Dimension(110, 26));
        sliderSpeed.setBackground(BG_PANEL);
        lblSpeed = styledLabel("1200ms");
        sliderSpeed.addChangeListener(ev -> {
            int v = sliderSpeed.getValue();
            lblSpeed.setText(v + "ms");
            if (autoTimer != null) autoTimer.setDelay(v);
        });
        row2.add(styledLabel("Vitesse:")); row2.add(sliderSpeed); row2.add(lblSpeed);

        row2.add(separator());

        JButton btnDistrib = simBtn("Repartir agents", new Color(160, 100, 220));
        btnDistrib.addActionListener(e -> { engine.distributeAgentsToExits(); renderer.rafraichir(); });
        row2.add(btnDistrib);

        row2.add(separator());

        JButton btnAddNodes = simBtn("+ X Noeuds", new Color(80, 200, 150));
        btnAddNodes.addActionListener(e -> showAddRandomNodesDialog());
        row2.add(btnAddNodes);

        JButton btnAddAgents = simBtn("+ X Agents", new Color(200, 160, 60));
        btnAddAgents.addActionListener(e -> showAddRandomAgentsDialog());
        row2.add(btnAddAgents);

        wrapper.add(row2);

        autoTimer = new Timer(1200, ev -> doTick());
        return wrapper;
    }

    private JToggleButton toolBtn(String txt, String tip, Color accent) {
        JToggleButton btn = new JToggleButton(txt);
        btn.setToolTipText(tip);
        btn.setFocusable(false);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btn.setBackground(BG_PANEL); btn.setForeground(TXT);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 65, 100), 1),
                new EmptyBorder(4, 8, 4, 8)));
        btn.addItemListener(ev -> {
            btn.setBackground(btn.isSelected() ? accent.darker() : BG_PANEL);
            btn.setForeground(btn.isSelected() ? Color.WHITE : TXT);
        });
        return btn;
    }

    private JButton simBtn(String txt, Color fg) {
        JButton btn = new JButton(txt);
        btn.setFocusable(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setBackground(BG_PANEL); btn.setForeground(fg);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg.darker(), 1),
                new EmptyBorder(4, 10, 4, 10)));
        return btn;
    }

    private JLabel styledLabel(String txt) {
        JLabel l = new JLabel(txt); l.setForeground(TXT_DIM);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11)); return l;
    }

    private JSeparator separator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 28));
        sep.setForeground(new Color(50, 65, 100));
        return sep;
    }

    // ── Right panel ───────────────────────────────────────────────────────────

    private JPanel buildRightPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setPreferredSize(new Dimension(235, 0));
        p.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(40, 55, 85)));
        p.add(section("📊 Simulation",        buildStatsContent()));
        p.add(section("👤 Agent sélectionné", buildAgentContent()));
        p.add(section("🔵 Nœud sélectionné",  buildNodeContent()));
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel section(String title, JComponent content) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 55, 85)),
                new EmptyBorder(8, 10, 8, 10)));
        JLabel t = new JLabel(title);
        t.setFont(new Font("SansSerif", Font.BOLD, 12));
        t.setForeground(GraphRenderer.SEL_COLOR);
        t.setAlignmentX(LEFT_ALIGNMENT);
        p.add(t); p.add(Box.createVerticalStrut(6)); p.add(content);
        return p;
    }

    private JPanel buildStatsContent() {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        lblTick      = lbl("Tick : 0");
        lblMovement  = lbl("<html>—</html>");
        lblPsycho    = lbl("<html>—</html>");
        p.add(lblTick); p.add(Box.createVerticalStrut(4));
        p.add(lblMovement); p.add(Box.createVerticalStrut(4));
        p.add(lblPsycho);
        return p;
    }

    private JPanel buildAgentContent() {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        lblAgentInfo = lbl("<html><i>Cliquez sur un agent</i></html>");
        p.add(lblAgentInfo); p.add(Box.createVerticalStrut(6));

        JButton btnEdit = new JButton("✏ Modifier agent");
        btnEdit.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnEdit.setForeground(GraphRenderer.SEL_COLOR);
        btnEdit.setBackground(BG_PANEL);
        btnEdit.setBorder(BorderFactory.createLineBorder(GraphRenderer.SEL_COLOR, 1));
        btnEdit.setFocusable(false); btnEdit.setAlignmentX(LEFT_ALIGNMENT);
        btnEdit.addActionListener(e -> showEditAgentDialog());
        p.add(btnEdit); p.add(Box.createVerticalStrut(4));

        JButton btnDelAgent = new JButton("✖ Supprimer agent sélectionné");
        styleDestructBtn(btnDelAgent);
        btnDelAgent.addActionListener(e -> deleteSelectedAgent());
        p.add(btnDelAgent); p.add(Box.createVerticalStrut(4));

        JButton btnAddMode = new JButton("👤 Ajouter un agent (mode)");
        btnAddMode.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnAddMode.setForeground(GraphRenderer.AGENT_CALM.darker());
        btnAddMode.setBackground(BG_PANEL);
        btnAddMode.setBorder(BorderFactory.createLineBorder(GraphRenderer.AGENT_CALM.darker(), 1));
        btnAddMode.setFocusable(false); btnAddMode.setAlignmentX(LEFT_ALIGNMENT);
        btnAddMode.addActionListener(e -> {
            btnAddAgent.setSelected(true);
            renderer.setMode(GraphRenderer.Mode.AJOUT_AGENT);
        });
        p.add(btnAddMode);
        return p;
    }

    private JPanel buildNodeContent() {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        lblNodeInfo = lbl("<html><i>Cliquez sur un nœud</i></html>");
        p.add(lblNodeInfo);
        return p;
    }

    private void styleDestructBtn(JButton b) {
        b.setFont(new Font("SansSerif", Font.PLAIN, 10));
        b.setForeground(new Color(200, 80, 80)); b.setBackground(BG_PANEL);
        b.setBorder(BorderFactory.createLineBorder(new Color(200, 80, 80), 1));
        b.setFocusable(false); b.setAlignmentX(LEFT_ALIGNMENT);
    }

    private JLabel lbl(String html) {
        JLabel l = new JLabel(html);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(TXT); l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    // ── Bottom log panel ──────────────────────────────────────────────────────

    private JScrollPane buildBottomPanel() {
        logArea = new JTextArea(5, 60);
        logArea.setEditable(false);
        logArea.setBackground(BG_DARK); logArea.setForeground(TXT_DIM);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        logArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        JScrollPane sc = new JScrollPane(logArea);
        sc.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(40, 55, 85)));
        sc.setPreferredSize(new Dimension(0, 100));
        return sc;
    }

    // ── Menu bar ──────────────────────────────────────────────────────────────

    private void buildMenuBar() {
        JMenuBar mb = new JMenuBar(); mb.setBackground(BG_PANEL); mb.setBorder(null);
        JMenu mF = menu("Fichier");
        mF.add(mItem("💾 Sauvegarder…", e -> saveSimulation()));
        mF.add(mItem("📂 Charger…",     e -> loadSimulation()));
        mb.add(mF);
        JMenu mG = menu("Graphe");
        mG.add(mItem("🗑 Vider tout", e -> clearGraph()));
        mb.add(mG);
        JMenu mL = menu("📖 Légende");
        mL.add(mItem("Afficher / masquer la légende", e -> toggleLegend()));
        mb.add(mL);
        setJMenuBar(mb);
    }

    private void toggleLegend() {
        if (legendDialog != null && legendDialog.isVisible()) {
            legendDialog.dispose();
            legendDialog = null;
            return;
        }
        legendDialog = new JDialog(this, "Légende", false);
        legendDialog.setContentPane(buildLegendPanel());
        legendDialog.pack();
        legendDialog.setResizable(false);
        legendDialog.setLocation(getX() + 10, getY() + 60);
        legendDialog.setVisible(true);
    }

    private JPanel buildLegendPanel() {
        JPanel main = new JPanel(new GridLayout(1, 3, 18, 0));
        main.setBackground(new Color(22, 28, 44));
        main.setBorder(new EmptyBorder(14, 18, 14, 18));

        main.add(legendColumn("NŒUDS", new Object[][]{
            {GraphRenderer.NODE_NORMAL,      "Normal"},
            {GraphRenderer.NODE_EXIT_OPEN,   "Sortie ouverte"},
            {GraphRenderer.NODE_EXIT_CLOSED, "Sortie fermée"},
            {GraphRenderer.NODE_DANGER_F,    "Danger — Feu 🔥"},
            {GraphRenderer.NODE_DANGER_I,    "Danger — Inondation 🌊"},
            {GraphRenderer.NODE_DANGER_P,    "Personne dangereuse"},
            {new Color(55, 55, 65),          "Nœud bloqué par feu"},
            {GraphRenderer.NODE_THREATENED,  "Nœud menacé (prochain)"},
        }));

        main.add(legendColumn("ARÊTES", new Object[][]{
            {GraphRenderer.EDGE_FREE,        "Libre"},
            {new Color(145, 77, 85),         "Mi-chargée"},
            {GraphRenderer.EDGE_CONG,        "Saturée"},
            {GraphRenderer.EDGE_FIRE,        "Bloquée — Feu 🔴"},
            {GraphRenderer.EDGE_FLOOD,       "Bloquée — Inondation ⚫"},
            {GraphRenderer.SEL_COLOR,        "Trajet agent sélectionné"},
        }));

        main.add(legendColumn("AGENTS", new Object[][]{
            {GraphRenderer.AGENT_CALM,       "Calme  (lent)"},
            {GraphRenderer.AGENT_PANIC,      "Panique  (rapide)"},
            {GraphRenderer.AGENT_MADNESS,    "Folie  (très rapide, aléatoire)"},
            {GraphRenderer.AGENT_BLOCKED,    "Bloqué — cherche chemin"},
            {GraphRenderer.AGENT_ARRIVED,    "Arrivé à destination ✓"},
            {new Color(170, 180, 205),       "En attente (non initialisé)"},
        }));

        return main;
    }

    private JPanel legendColumn(String title, Object[][] items) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBackground(new Color(22, 28, 44));

        JLabel t = new JLabel(title);
        t.setFont(new Font("SansSerif", Font.BOLD, 12));
        t.setForeground(GraphRenderer.SEL_COLOR);
        t.setAlignmentX(LEFT_ALIGNMENT);
        col.add(t);
        col.add(Box.createVerticalStrut(8));

        for (Object[] item : items) {
            Color c   = (Color) item[0];
            String lb = (String) item[1];

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            row.setBackground(new Color(22, 28, 44));
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

            JLabel swatch = new JLabel();
            swatch.setOpaque(true);
            swatch.setBackground(c);
            swatch.setPreferredSize(new Dimension(14, 14));
            swatch.setBorder(BorderFactory.createLineBorder(c.darker(), 1));

            JLabel text = new JLabel(lb);
            text.setFont(new Font("SansSerif", Font.PLAIN, 11));
            text.setForeground(TXT);

            row.add(swatch);
            row.add(text);
            col.add(row);
        }
        return col;
    }

    private JMenu menu(String t) {
        JMenu m = new JMenu(t); m.setForeground(TXT);
        m.setFont(new Font("SansSerif", Font.PLAIN, 12)); return m;
    }

    private JMenuItem mItem(String t, java.awt.event.ActionListener al) {
        JMenuItem it = new JMenuItem(t);
        it.setBackground(BG_PANEL); it.setForeground(TXT);
        it.setFont(new Font("SansSerif", Font.PLAIN, 11));
        it.addActionListener(al); return it;
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private void onAgentSelected(Agent a) {
        selectedAgent = a;
        if (a == null) { lblAgentInfo.setText("<html><i>Aucun agent sélectionné</i></html>"); return; }
        String dest = a.getDestination() != null ? a.getDestination().getId() : "—";
        int    rest = Math.max(0, a.getPath().size() - a.getPath().indexOf(a.getPosition()) - 1);
        lblAgentInfo.setText(String.format(
            "<html><b>%s</b><br>Pos : %s<br>Dest : %s<br>Étapes rest. : %d<br>" +
            "Vitesse : %d<br>Psycho : %s<br>Comportement : %s<br>État : %s<br>" +
            "Passages : %d<br>À l'arrivée : %s</html>",
            a.getId(), a.getPosition().getId(), dest, rest,
            a.getSpeed(), a.getPsychologicalState(), a.getBehavior(),
            a.getMovementState(), a.getAgentsPassed(), a.getArrivalBehavior()));
    }

    private void onNodeSelected(Node n) {
        selectedNode = n;
        log("Nœud : " + n.getId() + " (" + n.getClass().getSimpleName() + ")");
        String extra = "";
        if (n instanceof Exit) {
            extra = "<br>Sortie — ouvert: " + ((Exit) n).isOpen() + " | cap: " + ((Exit) n).getCapacity();
        } else if (n instanceof DangerZone) {
            extra = "<br>Danger: " + ((DangerZone) n).getDangerType()
                    + " | risque: " + ((DangerZone) n).getRiskLevel();
        }
        lblNodeInfo.setText(String.format(
            "<html><b>%s</b><br>Type: %s<br>Pos: (%.2f, %.2f)<br>Capacité: %d<br>Passages: %d<br>Bloqué: %b%s</html>",
            n.getId(), n.getClass().getSimpleName(), n.getX(), n.getY(),
            n.getCapacity(), n.getAgentsPassed(), n.isBlocked(), extra));
    }

    private void onEdgeAdded(Node src, Node dst) {
        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.setBackground(new Color(22, 28, 44));
        JLabel lWeight   = new JLabel("Poids :"); lWeight.setForeground(TXT);
        JLabel lCap      = new JLabel("Capacité (max agents) :"); lCap.setForeground(TXT);
        JLabel lSpeed    = new JLabel("Modificateur vitesse :"); lSpeed.setForeground(TXT);
        JSpinner spinWeight = new JSpinner(new SpinnerNumberModel(2, 1, 99, 1));
        JSpinner spinCap    = new JSpinner(new SpinnerNumberModel(3, 1, 99, 1));
        JSpinner spinSpeed  = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 5.0, 0.1));
        panel.add(lWeight); panel.add(spinWeight);
        panel.add(lCap);    panel.add(spinCap);
        panel.add(lSpeed);  panel.add(spinSpeed);

        int res = JOptionPane.showConfirmDialog(this, panel,
                "Arête " + src.getId() + " ↔ " + dst.getId(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        double weight = ((Number) spinWeight.getValue()).doubleValue();
        int    cap    = ((Number) spinCap.getValue()).intValue();
        double speed  = ((Number) spinSpeed.getValue()).doubleValue();
        Edge edge = new Edge(src, dst, weight);
        edge.setCapacity(cap);
        edge.setSpeedModifier(speed);
        engine.getGraph().addEdge(edge);
        log("Arête : " + src.getId() + " ↔ " + dst.getId()
                + " (poids=" + weight + ", cap=" + cap + ", speed×" + speed + ")");
        renderer.rafraichir();
    }

    private void deleteSelectedAgent() {
        if (selectedAgent == null) {
            JOptionPane.showMessageDialog(this, "Aucun agent sélectionné.", "Info",
                    JOptionPane.INFORMATION_MESSAGE); return;
        }
        engine.removeAgent(selectedAgent);
        selectedAgent = null;
        onAgentSelected(null);
        renderer.rafraichir();
    }

    // ── Edit agent dialog ─────────────────────────────────────────────────────

    private void showEditAgentDialog() {
        if (selectedAgent == null) {
            JOptionPane.showMessageDialog(this, "Aucun agent sélectionné.", "Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Agent a = selectedAgent;

        JDialog dlg = new JDialog(this, "Modifier Agent " + a.getId(), true);
        dlg.setLayout(new GridBagLayout());
        dlg.getContentPane().setBackground(new Color(22, 28, 44));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 10, 5, 10);
        gc.fill   = GridBagConstraints.HORIZONTAL;

        JSpinner spinSpeed = new JSpinner(new SpinnerNumberModel(a.getSpeed(), 1, 5, 1));
        JComboBox<String> comboPsycho = new JComboBox<>(new String[]{"CALM","PANIC","MADNESS"});
        comboPsycho.setSelectedItem(a.getPsychologicalState().name());
        JComboBox<String> comboBehav = new JComboBox<>(new String[]{"YIELD","PRIORITY","FOLLOW"});
        comboBehav.setSelectedItem(a.getBehavior().name());
        JComboBox<String> comboArrival = new JComboBox<>(new String[]{"STOP","RANDOM_DESTINATION","DELETE"});
        comboArrival.setSelectedItem(a.getArrivalBehavior().name());
        JComboBox<String> comboMode = new JComboBox<>(new String[]{
            "FIXED","RANDOM_WALK","FLEE_DESTINATION","TOWARD_DENSE","FLEE_DENSITY"});
        comboMode.setSelectedItem(a.getDestinationMode().name());

        Object[][] rows = {
            {"Vitesse :", spinSpeed},
            {"État psycho :", comboPsycho},
            {"Comportement :", comboBehav},
            {"Mode destination :", comboMode},
            {"À l'arrivée :", comboArrival},
        };
        for (int i = 0; i < rows.length; i++) {
            gc.gridx=0; gc.gridy=i;
            JLabel lbl = new JLabel((String) rows[i][0]); lbl.setForeground(TXT);
            dlg.add(lbl, gc); gc.gridx=1;
            dlg.add((Component) rows[i][1], gc);
        }

        JButton ok = new JButton("Appliquer"), cancel = new JButton("Annuler");
        JPanel btns = new JPanel(); btns.setBackground(new Color(22, 28, 44));
        btns.add(ok); btns.add(cancel);
        gc.gridx=0; gc.gridy=rows.length; gc.gridwidth=2;
        dlg.add(btns, gc);

        ok.addActionListener(e -> {
            a.setSpeed((int) spinSpeed.getValue());
            a.setPsychologicalState(Agent.PsychologicalState.valueOf((String) comboPsycho.getSelectedItem()));
            a.setBehavior(Agent.Behavior.valueOf((String) comboBehav.getSelectedItem()));
            a.setDestinationMode(Agent.DestinationMode.valueOf((String) comboMode.getSelectedItem()));
            a.setArrivalBehavior(Agent.ArrivalBehavior.valueOf((String) comboArrival.getSelectedItem()));
            onAgentSelected(a);
            dlg.dispose();
            renderer.rafraichir();
        });
        cancel.addActionListener(e -> dlg.dispose());
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ── Add random nodes dialog ───────────────────────────────────────────────

    private void showAddRandomNodesDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 6));
        panel.setBackground(new Color(22, 28, 44));
        JLabel lCount  = new JLabel("Nombre de nœuds :"); lCount.setForeground(TXT);
        JLabel lEdges  = new JLabel("Arêtes par nœud :"); lEdges.setForeground(TXT);
        JLabel lMaxX   = new JLabel("Max X :"); lMaxX.setForeground(TXT);
        JSpinner spinCount  = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        JSpinner spinEdges  = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
        JSpinner spinMaxX   = new JSpinner(new SpinnerNumberModel(8.0, 1.0, 20.0, 0.5));
        panel.add(lCount); panel.add(spinCount);
        panel.add(lEdges); panel.add(spinEdges);
        panel.add(lMaxX);  panel.add(spinMaxX);

        int res = JOptionPane.showConfirmDialog(this, panel,
                "Ajouter des nœuds aléatoires", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        int count  = ((Number) spinCount.getValue()).intValue();
        int edges  = ((Number) spinEdges.getValue()).intValue();
        double maxX = ((Number) spinMaxX.getValue()).doubleValue();
        double maxY = 5.0;

        List<Node> added = engine.getGraph().addRandomNodes(count, maxX, maxY);
        engine.getGraph().addRandomEdges(added, edges);
        log("Ajout de " + added.size() + " nœud(s) aléatoire(s) avec ~" + edges + " arête(s) chacun.");
        renderer.rafraichir();
    }

    // ── Add random agents dialog ──────────────────────────────────────────────

    private void showAddRandomAgentsDialog() {
        JPanel panel = new JPanel(new GridLayout(5, 2, 6, 6));
        panel.setBackground(new Color(22, 28, 44));
        JLabel lCount   = new JLabel("Nombre d'agents :"); lCount.setForeground(TXT);
        JLabel lMinSpeed = new JLabel("Vitesse min :"); lMinSpeed.setForeground(TXT);
        JLabel lMaxSpeed = new JLabel("Vitesse max :"); lMaxSpeed.setForeground(TXT);
        JLabel lMinTol   = new JLabel("Tolérance min :"); lMinTol.setForeground(TXT);
        JLabel lMaxTol   = new JLabel("Tolérance max :"); lMaxTol.setForeground(TXT);
        JSpinner spinCount    = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
        JSpinner spinMinSpeed = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
        JSpinner spinMaxSpeed = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
        JSpinner spinMinTol   = new JSpinner(new SpinnerNumberModel(0.3, 0.0, 1.0, 0.1));
        JSpinner spinMaxTol   = new JSpinner(new SpinnerNumberModel(1.0, 0.0, 1.0, 0.1));
        panel.add(lCount);    panel.add(spinCount);
        panel.add(lMinSpeed); panel.add(spinMinSpeed);
        panel.add(lMaxSpeed); panel.add(spinMaxSpeed);
        panel.add(lMinTol);   panel.add(spinMinTol);
        panel.add(lMaxTol);   panel.add(spinMaxTol);

        int res = JOptionPane.showConfirmDialog(this, panel,
                "Ajouter des agents aléatoires", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        int count     = ((Number) spinCount.getValue()).intValue();
        int minSpeed  = ((Number) spinMinSpeed.getValue()).intValue();
        int maxSpeed  = ((Number) spinMaxSpeed.getValue()).intValue();
        double minTol = ((Number) spinMinTol.getValue()).doubleValue();
        double maxTol = ((Number) spinMaxTol.getValue()).doubleValue();

        engine.addRandomAgents(count, minSpeed, maxSpeed, minTol, maxTol);
        log("Ajout de " + count + " agent(s) aléatoire(s).");
        renderer.rafraichir();
    }

    // ── Simulation control ────────────────────────────────────────────────────

    private void onInit(ActionEvent e) {
        engine.initialize();
        renderer.rafraichir(); updateStats();
        btnTick.setEnabled(true);
        btnPlay.setEnabled(true);
        btnInit.setEnabled(false);
        log("=== Simulation initialisée — les agents recommencent depuis leur position de départ ===");
    }

    private void onPlay(ActionEvent e) {
        if (autoTimer.isRunning()) {
            autoTimer.stop(); btnPlay.setText("▶ Démarrer"); btnPlay.setForeground(ACCENT2);
        } else {
            autoTimer.setDelay(sliderSpeed.getValue()); autoTimer.start();
            btnPlay.setText("⏸ Pause"); btnPlay.setForeground(new Color(220, 160, 40));
        }
    }

    private void doTick() {
        if (!engine.isRunning()) return;
        engine.tick(); renderer.rafraichir(); updateStats();
        if (!engine.isRunning()) {
            autoTimer.stop();
            btnPlay.setText("▶ Démarrer"); btnPlay.setForeground(ACCENT2);
            btnTick.setEnabled(false);
            btnPlay.setEnabled(false);
            btnInit.setEnabled(true);
            log("=== Simulation terminée — cliquez sur ⚙ Initialiser pour recommencer ===");
        }
    }

    private void updateStats() {
        int total = engine.getAgents().size();
        int[] d = engine.getMovementStats();
        int[] p = engine.getPsychologicalStats();
        lblTick.setText("Tick : " + engine.getTick());
        lblMovement.setText(String.format(
            "<html>Agents : %d<br>" +
            "<font color='#78C8FF'>▶ En mvt : %d</font><br>" +
            "<font color='#50D080'>✔ Arrivés : %d</font><br>" +
            "<font color='#E05050'>✖ Bloqués : %d</font></html>",
            total, d[1], d[2], d[3]));
        lblPsycho.setText(String.format(
            "<html><font color='#FFE032'>Calme : %d</font> " +
            "<font color='#FF8020'>Panique : %d</font> " +
            "<font color='#D030D0'>Folie : %d</font></html>",
            p[0], p[1], p[2]));
    }

    // ── Graph operations ──────────────────────────────────────────────────────

    private void clearGraph() {
        int r = JOptionPane.showConfirmDialog(this, "Vider tout le graphe et les agents ?",
                "Confirmation", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        engine.getAgents().clear();
        engine.getGraph().getEdges().clear();
        engine.getGraph().getNodes().clear();
        btnInit.setEnabled(true); btnTick.setEnabled(false); btnPlay.setEnabled(false);
        renderer.rafraichir(); log("Graphe vidé.");
    }

    // ── Save / Load ───────────────────────────────────────────────────────────

    private void saveSimulation() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Simulation (*.sim)", "sim"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (!f.getName().endsWith(".sim")) f = new File(f.getPath() + ".sim");
            try {
                engine.save(f);
                JOptionPane.showMessageDialog(this, "Simulation sauvegardée : " + f.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Erreur lors de la sauvegarde : " + ex.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadSimulation() {
        if (autoTimer.isRunning()) autoTimer.stop();
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Simulation (*.sim)", "sim"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                renderer.stopAnimation();
                engine   = SimulationEngine.load(fc.getSelectedFile());
                renderer = new GraphRenderer(engine);
                renderer.setOnAgentSelected(this::onAgentSelected);
                renderer.setOnNodeSelected(this::onNodeSelected);
                renderer.setOnEdgeAdded(this::onEdgeAdded);
                renderer.setOnNodeAdded(n    -> { log("Nœud ajouté : "   + n.getId()); renderer.rafraichir(); });
                renderer.setOnNodeRemoved(n  -> { log("Nœud supprimé : " + n.getId()); renderer.rafraichir(); });
                renderer.setOnEdgeRemoved(e  -> { log("Arête supprimée."); renderer.rafraichir(); });
                getContentPane().removeAll();
                setLayout(new BorderLayout());
                add(buildToolbar(),     BorderLayout.NORTH);
                add(renderer,           BorderLayout.CENTER);
                add(buildRightPanel(),  BorderLayout.EAST);
                add(buildBottomPanel(), BorderLayout.SOUTH);
                buildMenuBar(); revalidate(); repaint();
                redirectLog(); updateStats();
                JOptionPane.showMessageDialog(this, "Simulation chargée.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur lors du chargement : " + ex.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            if (logArea != null) {
                logArea.append(msg + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    private void redirectLog() {
        java.io.PrintStream ps = new java.io.PrintStream(System.out) {
            @Override public void println(String x) { super.println(x); log(x); }
        };
        System.setOut(ps);
    }
}
