package evacuation.graph;

public class Node {

    private String id;
    private boolean bloque;
    private double x;
    private double y;

    public Node(String id) {
        this(id, 0, 0);
    }

    public Node(String id, double x, double y) {
        this.id = id;
        this.bloque = false;
        this.x = x;
        this.y = y;
    }

    public boolean isBloque() { return bloque; }

    public void bloquer() {
        this.bloque = true;
        System.out.println("[Node] " + id + " est bloqué.");
    }

    public void debloquer() {
        this.bloque = false;
        System.out.println("[Node] " + id + " est débloqué.");
    }

    public String getId() { return id; }

    public double getX() { return x; }
    public double getY() { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }

    @Override
    public String toString() {
        return "Node{id='" + id + "', bloque=" + bloque + "}";
    }
}
