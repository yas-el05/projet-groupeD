package evacuation.routing;

import evacuation.graph.Node;
import java.util.List;

public interface PathFinder {

    /**
     * Calcule le chemin le plus court entre src et dest
     * @return liste ordonnée de nœuds (src inclus, dest inclus),
     * ou liste vide si aucun chemin n'existe
     */
    List<Node> calculerChemin(Node src, Node dest); 

    // Recalcule les distances internes (appeler après modification du graphe)
    void recalculer();

    // Retourne le dernier chemin calculé
    List<Node> getPlusCourt();
}
