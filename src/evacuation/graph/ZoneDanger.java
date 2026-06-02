package evacuation.graph;

public class ZoneDanger extends Node {

    private String typeDanger;
    private int niveauRisque;

    public ZoneDanger(String id, String nom, String typeDanger, int niveauRisque){
        super(id, nom);
        this.typeDanger = typeDanger;
        this.niveauRisque = niveauRisque;
        bloquer();
    }

    public ZoneDanger(String id, String nom, int x, int y, String typeDanger, int niveauRisque){
        super(id, nom, x, y);
        this.typeDanger = typeDanger;
        this.niveauRisque = niveauRisque;
        bloquer();
    }

    public void signaler(){
        System.out.println("[ZoneDanger] ALERTE : " + getNom() + " — type=" + typeDanger + ", risque=" + niveauRisque + "/10");
    }

    public int getNiveauRisque(){
        return niveauRisque;
    }

    public String getTypeDanger(){
        return typeDanger;
    }

    @Override
    public String toString(){
        return "ZoneDanger{id='" + getId() + "', nom='" + getNom() + "', type='" + typeDanger + "', risque=" + niveauRisque + "}";
    }
}
