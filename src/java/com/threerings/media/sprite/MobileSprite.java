//
// $Id: MobileSprite.java,v 1.3 2001/08/02 00:42:02 shaper Exp $

package com.threerings.miso.sprite;

import com.threerings.miso.Log;
import com.threerings.miso.tile.Tile;
import com.threerings.miso.tile.TileManager;

/**
 * A MobileSprite is a sprite that can face in one of eight compass
 * directions and that can be animated moving from one location to
 * another (e.g., a human's legs move and arms swing.)
 */
public class MobileSprite extends Sprite
{
    /**
     * Construct a MobileSprite object, loading the tiles used to
     * display the sprite from specified tileset via the given tile
     * manager.
     *
     * @param x the sprite x-position in pixels.
     * @param y the sprite y-position in pixels.
     * @param tilemgr the tile manager to retrieve tiles from.
     * @param tsid the tileset id containing the sprite tiles.
     */
    public MobileSprite (SpriteManager spritemgr, int x, int y,
                         TileManager tilemgr, int tsid)
    {
        super(spritemgr, x, y);

        _charTiles = getTiles(tilemgr, tsid);
        _dir = Path.DIR_SOUTH;

        setTiles(_charTiles[0]);
    }

    /**
     * Returns a two-dimensional array of tiles corresponding to the
     * frames of animation used to render the mobile sprite in each of
     * the directions it may face.  The tileset id referenced must
     * contain <code>Path.NUM_DIRECTIONS</code> rows of tiles, with each
     * row containing <code>NUM_DIR_FRAMES</code> tiles.
     *
     * @param tilemgr the tile manager to retrieve tiles from.
     * @param tsid the tileset id containing the sprite tiles.
     *
     * @return the two-dimensional array of sprite tiles.
     */
    protected Tile[][] getTiles (TileManager tilemgr, int tsid)
    {
        Tile[][] tiles =
            new Tile[Path.NUM_DIRECTIONS][NUM_DIR_FRAMES];

        for (int ii = 0; ii < Path.NUM_DIRECTIONS; ii++) {
            for (int jj = 0; jj < NUM_DIR_FRAMES; jj++) {
                int idx = (ii * NUM_DIR_FRAMES) + jj;
                tiles[ii][jj] = tilemgr.getTile(tsid, idx);
            }
        }

        return tiles;
    }

    /**
     * Alter the sprite's direction to reflect the direction the
     * destination point lies in before calling the superclass's
     * <code>setDestination</code> method.
     *
     * @param x the destination x-position.
     * @param y the destination y-position.
     */
    public void setDestination (int x, int y)
    {
        // update the sprite tiles to reflect the direction
        setTiles(_charTiles[_dir = getDirection(x, y)]);

        // call superclass to effect the beginnings of the move
        super.setDestination(x, y);

        if (_state == STATE_MOVING) {
            setAnimationDelay(0);
        }
    }

    /**
     * Return the directional constant corresponding to the direction
     * the specified point is in from the sprite.
     */
    protected int getDirection (int x, int y)
    {
        if (x >= this.x - DIR_BUFFER && x <= this.x + DIR_BUFFER) {
            return (y < this.y) ? Path.DIR_NORTH : Path.DIR_SOUTH;

        } else if (y >= this.y - DIR_BUFFER && y <= this.y + DIR_BUFFER) {
            return (x >= this.x) ? Path.DIR_EAST : Path.DIR_WEST;

        } else if (x > this.x) {
            return (y < this.y) ? Path.DIR_NORTHEAST : Path.DIR_SOUTHEAST;

        } else {
            return (y < this.y) ? Path.DIR_NORTHWEST : Path.DIR_SOUTHWEST;
        }
    }

    /** The number of frames of animation for each direction. */
    protected static final int NUM_DIR_FRAMES = 8;

    /**
     * The buffer space in pixels allowed for horizontal or vertical
     * selection of movement north/south or east/west, respectively.
     */ 
    protected static final int DIR_BUFFER = 20;

    /** The animation frames for the sprite facing each direction. */
    protected Tile[][] _charTiles;

    /** The direction the sprite is currently facing. */
    protected int _dir;
}
