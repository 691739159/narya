//
// $Id: EditableMisoScene.java,v 1.5 2001/11/18 04:09:23 mdb Exp $

package com.threerings.miso.tools;

import com.threerings.media.tile.ObjectTile;
import com.threerings.media.tile.Tile;

import com.threerings.miso.tile.MisoTile;
import com.threerings.miso.scene.DisplayMisoScene;
import com.threerings.miso.scene.MisoSceneModel;

/**
 * The editable Miso scene interface is used in the offline scene building
 * tools as well as by the tools that load those prototype scenes into the
 * runtime database. Accordingly, it provides a means for modifying scene
 * values and for obtaining access to the underlying scene models that
 * represent the underlying scene information.
 *
 * @see DisplayMisoScene
 */
public interface EditableMisoScene
    extends DisplayMisoScene
{
    /**
     * Returns the default base tile.
     */
    public MisoTile getDefaultBaseTile ();

    /**
     * Sets the default base tile.
     *
     * @param defaultBaseTile the new default base tile.
     * @param fqTileId the fully-qualified tile id (@see
     * com.threerings.media.tile.TileUtil#getFQTileId}) of the new default
     * base tile.
     */
    public void setDefaultBaseTile (MisoTile defaultBaseTile, int fqTileId);

    /**
     * Updates the tile at the specified location in the base layer.
     *
     * @param x the x-coordinate of the tile to set.
     * @param y the y-coordinate of the tile to set.
     * @param tile the tile to set.
     * @param fqTileId the fully-qualified tile id (@see
     * com.threerings.media.tile.TileUtil#getFQTileId}) of the new default
     * base tile.
     */
    public void setBaseTile (int x, int y, MisoTile tile, int fqTileId);

    /**
     * Updates the tile at the specified location in the fringe layer.
     *
     * @param x the x-coordinate of the tile to set.
     * @param y the y-coordinate of the tile to set.
     * @param tile the tile to set.
     * @param fqTileId the fully-qualified tile id (@see
     * com.threerings.media.tile.TileUtil#getFQTileId}) of the new default
     * base tile.
     */
    public void setFringeTile (int x, int y, Tile tile, int fqTileId);

    /**
     * Updates the tile at the specified location in the object layer. Any
     * previous object tile at that location should be cleared out by the
     * implementation of this method before the new tile is set to ensure
     * that footprint tiles associated with the old object are properly
     * disposed of.
     *
     * @param x the x-coordinate of the tile to set.
     * @param y the y-coordinate of the tile to set.
     * @param tile the tile to set.
     * @param fqTileId the fully-qualified tile id (@see
     * com.threerings.media.tile.TileUtil#getFQTileId}) of the new default
     * base tile.
     */
    public void setObjectTile (int x, int y, ObjectTile tile, int fqTileId);

    /**
     * Clears out the tile at the specified location in the base layer.
     */
    public void clearBaseTile (int x, int y);

    /**
     * Clears out the tile at the specified location in the fringe layer.
     */
    public void clearFringeTile (int x, int y);

    /**
     * Clears out the tile at the specified location in the object layer.
     */
    public void clearObjectTile (int x, int y);

    /**
     * Returns a reference to the miso scene model that reflects the
     * changes that have been made to this editable miso scene.
     */
    public MisoSceneModel getModel ();
}
