package evacuation.graph;

/**
 * @deprecated Use {@link Exit} instead.
 * Deprecated backward-compatibility wrapper that extends Exit.
 */
@Deprecated
class Sortie extends Exit {

    private static final long serialVersionUID = 1L;

    /**
     * @deprecated Use {@link Exit#Exit(String, int)} instead.
     */
    @Deprecated
    Sortie(String id, int capacity) {
        super(id, capacity);
    }

    /**
     * @deprecated Use {@link Exit#Exit(String, double, double, int)} instead.
     */
    @Deprecated
    Sortie(String id, double x, double y, int capacity) {
        super(id, x, y, capacity);
    }
}
