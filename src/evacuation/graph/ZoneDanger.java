package evacuation.graph;

/**
 * @deprecated Use {@link DangerZone} instead.
 * Deprecated backward-compatibility wrapper that extends DangerZone.
 */
@Deprecated
class ZoneDanger extends DangerZone {

    private static final long serialVersionUID = 1L;

    /**
     * @deprecated Use {@link DangerZone#DangerZone(String, DangerZone.DangerType, int)} instead.
     */
    @Deprecated
    ZoneDanger(String id, DangerType type, int riskLevel) {
        super(id, type, riskLevel);
    }

    /**
     * @deprecated Use {@link DangerZone#DangerZone(String, double, double, DangerZone.DangerType, int)} instead.
     */
    @Deprecated
    ZoneDanger(String id, double x, double y, DangerType type, int riskLevel) {
        super(id, x, y, type, riskLevel);
    }
}
