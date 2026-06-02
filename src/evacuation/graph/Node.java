package evacuation.graph;

public class Node {

    private String  id;
    private String  nom;
    private boolean bloque;
    private int     x;
    private int     y;

    public Node(String id, String nom){
        this(id, nom, 0, 0);
    }

    public Node(String id, String nom, int x, int y){
        this.id    = id;
        this.nom   = nom;
        this.bloque = false;
        this.x     = x;
        this.y     = y;
    }

    public boolean isBloque(){
        return bloque;
    }

    public void bloquer(){
        this.bloque = true;
        System.out.println("[Node] " + nom + " est bloqué.");
    }

    public void debloquer(){
        this.bloque = false;
        System.out.println("[Node] " + nom + " est débloqué.");
    }

    //getters
    public String getId(){
        return id;
    }
    public String getNom(){
        return nom;
    }
    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }

    @Override
    public String toString(){
        return "Node{id='" + id + "', nom='" + nom + "', bloque=" + bloque + "}";
    }
}

