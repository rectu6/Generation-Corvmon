package generations.gg.generations.core.generationscore.common.client.render.rarecandy;

/** Size of the RareCandy model drawn for an entity (see RareCandyBone), 0 when not drawn yet. */
public interface RareCandyModelSize {
    /** Half size of the drawn RareCandy model in blocks, 0 when not drawn yet. */
    float generations$modelRadius();

    /** Height of the model's top in the coordinates it was last drawn with (camera-relative). */
    float generations$modelTopY();

    /** Half width of the drawn model in blocks, 0 when not drawn yet. */
    float generations$modelHalfWidth();

    /** Height of the drawn model in blocks, 0 when not drawn yet. */
    float generations$modelHeight();
}
