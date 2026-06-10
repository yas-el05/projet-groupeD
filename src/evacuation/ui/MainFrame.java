package evacuation.ui;

import evacuation.agent.Agent;
import evacuation.graph.Edge;
import evacuation.graph.Graph;
import evacuation.graph.Node;
import evacuation.graph.Sortie;
import evacuation.graph.ZoneDanger;
import evacuation.simulation.SimulationEngine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

public class MainFrame extends JFrame {

    private static final Color BG_PANEL = new Color(22, 28, 44);
    private static final Color BG_DARK  = new Color(15, 20, 32);
    private static final Color ACCENT   = new Color(60, 130, 220);
    private static final Color ACCENT2  = new Color(40, 200, 110);
    private static final Color TXT      = new Color(205, 215, 235);
    private static final Color TXT_DIM  = new Color(130, 145, 175);

    private SimulationEngine engine;
    private GraphRenderer    renderer;

    // Simulation
    private JButton  btnInit, btnTick, btnPlay;
    private JSlider  sliderVit;
    private JLabel   lblVit;
    private Timer    autoTimer;

    // Stats
    private JLabel lblTick, lblDeplacement, lblPsycho, lblAgentInfo;

    // Outils
    private ButtonGroup  toolGroup;
    private JToggleButton btnSel, btnNoeud, btnSortie, btnArete, btnDanger,
                          btnAjoutAgent, btnSuppr, btnSupprAgent;

    // Journal
    private JTextArea logArea;

    private Agent agentSel;

    public MainFrame(SimulationEngine engine) {
        this.engine   = engine;
        this.renderer = new GraphRenderer(engine);
        renderer.setOnAgentSelectionne(this::onAgentSel);
        renderer.setOnNoeudSelectionne(this::onNoeudSel);
        renderer.setOnAreteAjoutee(this::onAreteAjoutee);
        renderer.setOnNoeudAjoute(n    -> { log("Nœud ajouté : "   + n.getId()); renderer.rafraichir(); });
        renderer.setOnNoeudSupprime(n  -> { log("Nœud supprimé : " + n.getId()); renderer.rafraichir(); });
        renderer.setOnAreteSupprimee(e -> { log("Arête supprimée."); renderer.rafraichir(); });

        setTitle("Simulation d'Évacuation");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 0));

        add(creerBarreOutils(),  BorderLayout.NORTH);
        add(renderer,            BorderLayout.CENTER);
        add(creerPanneauDroit(), BorderLayout.EAST);
        add(creerPanneauBas(),   BorderLayout.SOUTH);
        creerMenuBar();

        pack();
        setMinimumSize(new Dimension(1150, 720));
        setLocationRelativeTo(null);
        setVisible(true);
        redirectLog();
        renderer.requestFocusInWindow();
    }

    // ── Barre d'outils ─────────────────────────────────────────────────────────
    private JPanel creerBarreOutils() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        bar.setBackground(BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 55, 85)));

        toolGroup = new ButtonGroup();

        btnSel       = outil("⬡ Sélection",   "Sélectionner / déplacer nœuds (glisser)",      GraphRenderer.NODE_NORMAL.darker());
        btnNoeud     = outil("✚ Nœud",         "Ajouter un nœud (clic gauche)",                 new Color(60, 180, 100));
        btnSortie    = outil("🚪 Sortie",       "Ajouter une sortie",                            GraphRenderer.NODE_SORTIE_O);
        btnArete     = outil("─ Arête",         "Relier 2 nœuds (clic 1er → 2e nœud)",         GraphRenderer.SEL_COLOR.darker());
        btnDanger    = outil("⚠ Danger",        "Ajouter une zone de danger",                   GraphRenderer.NODE_DANGER_F);
        btnAjoutAgent= outil("👤+ Agent",        "Ajouter un agent sur un nœud (clic gauche)",  GraphRenderer.AGENT_CALME.darker());
        btnSuppr     = outil("✖ Suppr. nœud",  "Supprimer un nœud ou une arête (clic gauche)", new Color(180, 40, 40));
        btnSupprAgent= outil("✖ Suppr. agent", "Supprimer un agent (clic gauche sur agent)",   new Color(200, 80, 30));

        for (JToggleButton b : new JToggleButton[]{
                btnSel, btnNoeud, btnSortie, btnArete, btnDanger,
                btnAjoutAgent, btnSuppr, btnSupprAgent}) {
            toolGroup.add(b);
            bar.add(b);
        }
        btnSel.setSelected(true);

        btnSel.addActionListener(e        -> renderer.setMode(GraphRenderer.Mode.SELECTION));
        btnNoeud.addActionListener(e      -> renderer.setMode(GraphRenderer.Mode.AJOUT_NOEUD));
        btnSortie.addActionListener(e     -> renderer.setMode(GraphRenderer.Mode.AJOUT_SORTIE));
        btnArete.addActionListener(e      -> renderer.setMode(GraphRenderer.Mode.AJOUT_ARETE));
        btnDanger.addActionListener(e     -> renderer.setMode(GraphRenderer.Mode.AJOUT_DANGER));
        btnAjoutAgent.addActionListener(e -> renderer.setMode(GraphRenderer.Mode.AJOUT_AGENT));
        btnSuppr.addActionListener(e      -> renderer.setMode(GraphRenderer.Mode.SUPPRESSION));
        btnSupprAgent.addActionListener(e -> renderer.setMode(GraphRenderer.Mode.SUPPRESSION_AGENT));

        bar.add(separateur());

        // Contrôles simulation
        btnInit = simBtn("⚙ Initialiser", ACCENT);
        btnTick = simBtn("⏭ Tick +1",      new Color(80, 160, 220));
        btnPlay = simBtn("▶ Démarrer",     ACCENT2);
        btnTick.setEnabled(false);
        btnPlay.setEnabled(false);

        btnInit.addActionListener(this::onInit);
        btnTick.addActionListener(e -> doTick());
        btnPlay.addActionListener(this::onPlay);

        bar.add(btnInit); bar.add(btnTick); bar.add(btnPlay);

        sliderVit = new JSlider(50, 2000, 800);
        sliderVit.setInverted(true);
        sliderVit.setPreferredSize(new Dimension(110, 28));
        sliderVit.setBackground(BG_PANEL);
        lblVit = styledLabel("800ms");
        sliderVit.addChangeListener(ev -> {
            int v = sliderVit.getValue();
            lblVit.setText(v + "ms");
            if (autoTimer != null) autoTimer.setDelay(v);
        });
        bar.add(styledLabel("⏱")); bar.add(sliderVit); bar.add(lblVit);

        bar.add(separateur());

        JButton btnRep = simBtn("⟳ Répartir agents", new Color(160, 100, 220));
        btnRep.addActionListener(e -> { engine.repartirAgentsSurSorties(); renderer.rafraichir(); });
        bar.add(btnRep);

        autoTimer = new Timer(800, ev -> doTick());
        return bar;
    }

    private JToggleButton outil(String txt, String tip, Color accent) {
        JToggleButton btn = new JToggleButton(txt);
        btn.setToolTipText(tip);
        btn.setFocusable(false);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btn.setBackground(BG_PANEL);
        btn.setForeground(TXT);
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
        btn.setBackground(BG_PANEL);
        btn.setForeground(fg);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg.darker(), 1),
                new EmptyBorder(4, 10, 4, 10)));
        return btn;
    }

    private JLabel styledLabel(String txt) {
        JLabel l = new JLabel(txt); l.setForeground(TXT_DIM);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11)); return l;
    }

    private JSeparator separateur() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 28));
        sep.setForeground(new Color(50, 65, 100));
        return sep;
    }

    // ── Panneau droit ──────────────────────────────────────────────────────────
    private JPanel creerPanneauDroit() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setPreferredSize(new Dimension(225, 0));
        p.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(40, 55, 85)));
        p.add(section("📊 Simulation",       creerContenuStats()));
        p.add(section("👤 Agent sélectionné", creerContenuAgent()));
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel section(String titre, JComponent contenu) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 55, 85)),
                new EmptyBorder(8, 10, 8, 10)));
        JLabel t = new JLabel(titre);
        t.setFont(new Font("SansSerif", Font.BOLD, 12));
        t.setForeground(GraphRenderer.SEL_COLOR);
        t.setAlignmentX(LEFT_ALIGNMENT);
        p.add(t); p.add(Box.createVerticalStrut(6)); p.add(contenu);
        return p;
    }

    private JPanel creerContenuStats() {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        lblTick        = lbl("Tick : 0");
        lblDeplacement = lbl("<html>—</html>");
        lblPsycho      = lbl("<html>—</html>");
        p.add(lblTick); p.add(Box.createVerticalStrut(4));
        p.add(lblDeplacement); p.add(Box.createVerticalStrut(4));
        p.add(lblPsycho);
        return p;
    }

    private JPanel creerContenuAgent() {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_PANEL);
        lblAgentInfo = lbl("<html><i>Cliquez sur un agent</i></html>");
        p.add(lblAgentInfo); p.add(Box.createVerticalStrut(6));

        JButton btnSuppAg = new JButton("✖ Supprimer agent sélectionné");
        styleDestructBtn(btnSuppAg);
        btnSuppAg.addActionListener(e -> supprimerAgentSel());
        p.add(btnSuppAg);
        p.add(Box.createVerticalStrut(4));

        JButton btnAjout = new JButton("👤 Ajouter un agent (mode)");
        btnAjout.setFont(new Font("SansSerif", Font.PLAIN, 10));
        btnAjout.setForeground(GraphRenderer.AGENT_CALME.darker());
        btnAjout.setBackground(BG_PANEL);
        btnAjout.setBorder(BorderFactory.createLineBorder(GraphRenderer.AGENT_CALME.darker(), 1));
        btnAjout.setFocusable(false);
        btnAjout.setAlignmentX(LEFT_ALIGNMENT);
        btnAjout.setToolTipText("Active le mode Ajout Agent — puis cliquez sur un nœud");
        btnAjout.addActionListener(e -> {
            btnAjoutAgent.setSelected(true);
            renderer.setMode(GraphRenderer.Mode.AJOUT_AGENT);
        });
        p.add(btnAjout);
        return p;
    }

    private void styleDestructBtn(JButton b) {
        b.setFont(new Font("SansSerif", Font.PLAIN, 10));
        b.setForeground(new Color(200, 80, 80));
        b.setBackground(BG_PANEL);
        b.setBorder(BorderFactory.createLineBorder(new Color(200, 80, 80), 1));
        b.setFocusable(false); b.setAlignmentX(LEFT_ALIGNMENT);
    }

    private JLabel lbl(String html) {
        JLabel l = new JLabel(html);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(TXT); l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    // ── Panneau bas (journal) ──────────────────────────────────────────────────
    private JScrollPane creerPanneauBas() {
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

    // ── Menu bar ───────────────────────────────────────────────────────────────
    private void creerMenuBar() {
        JMenuBar mb = new JMenuBar(); mb.setBackground(BG_PANEL); mb.setBorder(null);
        JMenu mF = menu("Fichier");
        mF.add(mItem("💾 Sauvegarder…", e -> sauvegarder()));
        mF.add(mItem("📂 Charger…",     e -> charger()));
        mb.add(mF);
        JMenu mG = menu("Graphe");
        mG.add(mItem("🗑 Vider tout", e -> viderGraphe()));
        mb.add(mG);
        setJMenuBar(mb);
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

    // ── Callbacks renderer ─────────────────────────────────────────────────────
    private void onAgentSel(Agent a) {
        agentSel = a;
        if (a == null) { lblAgentInfo.setText("<html><i>Aucun agent sélectionné</i></html>"); return; }
        String dest  = a.getDestination() != null ? a.getDestination().getId() : "—";
        int    rest  = Math.max(0, a.getChemin().size() - a.getChemin().indexOf(a.getPosition()) - 1);
        lblAgentInfo.setText(String.format(
            "<html><b>%s</b><br>Pos : %s<br>Dest : %s<br>Étapes restantes : %d<br>" +
            "Vitesse : %d<br>Psycho : %s<br>Comportement : %s<br>État : %s</html>",
            a.getId(), a.getPosition().getId(), dest, rest,
            a.getVitesse(), a.getEtatPsychologique(), a.getComportement(), a.getEtatDeplacement()));
    }

    private void onNoeudSel(Node n) {
        log("Nœud : " + n.getId() + " (" + n.getClass().getSimpleName() + ")");
    }

    private void onAreteAjoutee(Node src, Node dst) {
        String pStr = JOptionPane.showInputDialog(this,
                "Poids de l'arête " + src.getId() + " ↔ " + dst.getId() + " :", "2");
        double poids = 2;
        try { if (pStr != null) poids = Double.parseDouble(pStr.trim()); }
        catch (NumberFormatException ignored) {}
        engine.getGraph().ajouterEdge(new Edge(src, dst, poids));
        log("Arête : " + src.getId() + " ↔ " + dst.getId() + " (poids=" + poids + ")");
        renderer.rafraichir();
    }

    private void supprimerAgentSel() {
        if (agentSel == null) {
            JOptionPane.showMessageDialog(this, "Aucun agent sélectionné.", "Info",
                    JOptionPane.INFORMATION_MESSAGE); return;
        }
        engine.supprimerAgent(agentSel);
        agentSel = null;
        onAgentSel(null);
        renderer.rafraichir();
    }

    // ── Simulation ─────────────────────────────────────────────────────────────
    private void onInit(ActionEvent e) {
        engine.initialiser();
        renderer.rafraichir(); majStats();
        btnTick.setEnabled(true); btnPlay.setEnabled(true); btnInit.setEnabled(false);
        log("=== Simulation initialisée ===");
    }

    private void onPlay(ActionEvent e) {
        if (autoTimer.isRunning()) {
            autoTimer.stop(); btnPlay.setText("▶ Démarrer"); btnPlay.setForeground(ACCENT2);
        } else {
            autoTimer.setDelay(sliderVit.getValue()); autoTimer.start();
            btnPlay.setText("⏸ Pause"); btnPlay.setForeground(new Color(220, 160, 40));
        }
    }

    private void doTick() {
        if (!engine.isEnCours()) return;
        engine.tick(); renderer.rafraichir(); majStats();
        if (!engine.isEnCours()) {
            autoTimer.stop(); btnPlay.setText("▶ Démarrer"); btnPlay.setForeground(ACCENT2);
            btnTick.setEnabled(false); btnPlay.setEnabled(false);
            log("=== Simulation terminée ===");
        }
    }

    private void majStats() {
        int total = engine.getAgents().size();
        int[] d = engine.getStatsDeplacement();
        int[] p = engine.getStatsPsychologiques();
        lblTick.setText("Tick : " + engine.getTick());
        lblDeplacement.setText(String.format(
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

    // ── Graphe ─────────────────────────────────────────────────────────────────
    private void viderGraphe() {
        int r = JOptionPane.showConfirmDialog(this, "Vider tout le graphe et les agents ?",
                "Confirmation", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        engine.getAgents().clear();
        engine.getGraph().getEdges().clear();
        engine.getGraph().getNodes().clear();
        btnInit.setEnabled(true); btnTick.setEnabled(false); btnPlay.setEnabled(false);
        renderer.rafraichir(); log("Graphe vidé.");
    }

    // ── Sauvegarde / Chargement ────────────────────────────────────────────────
    private void sauvegarder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Simulation (*.sim)", "sim"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (!f.getName().endsWith(".sim")) f = new File(f.getPath() + ".sim");
            try { engine.sauvegarder(f); JOptionPane.showMessageDialog(this, "Sauvegardé."); }
            catch (IOException ex) { JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE); }
        }
    }

    private void charger() {
        if (autoTimer.isRunning()) autoTimer.stop();
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Simulation (*.sim)", "sim"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                renderer.stopAnimation();
                engine   = SimulationEngine.charger(fc.getSelectedFile());
                renderer = new GraphRenderer(engine);
                renderer.setOnAgentSelectionne(this::onAgentSel);
                renderer.setOnNoeudSelectionne(this::onNoeudSel);
                renderer.setOnAreteAjoutee(this::onAreteAjoutee);
                renderer.setOnNoeudAjoute(n    -> { log("Nœud ajouté : "   + n.getId()); renderer.rafraichir(); });
                renderer.setOnNoeudSupprime(n  -> { log("Nœud supprimé : " + n.getId()); renderer.rafraichir(); });
                renderer.setOnAreteSupprimee(e -> { log("Arête supprimée."); renderer.rafraichir(); });
                getContentPane().removeAll();
                setLayout(new BorderLayout());
                add(creerBarreOutils(),  BorderLayout.NORTH);
                add(renderer,            BorderLayout.CENTER);
                add(creerPanneauDroit(), BorderLayout.EAST);
                add(creerPanneauBas(),   BorderLayout.SOUTH);
                creerMenuBar(); revalidate(); repaint();
                redirectLog(); majStats();
                JOptionPane.showMessageDialog(this, "Simulation chargée.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Journal ────────────────────────────────────────────────────────────────
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