//
// $Id: IsoUtil.java,v 1.31 2002/05/24 20:15:04 ray Exp $

package com.threerings.miso.scene.util;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;

import com.samskivert.swing.SmartPolygon;

import com.threerings.media.sprite.Sprite;
import com.threerings.media.tile.ObjectTile;
import com.threerings.media.util.MathUtil;

import com.threerings.util.DirectionCodes;

import com.threerings.miso.Log;
import com.threerings.miso.scene.IsoSceneViewModel;
import com.threerings.miso.scene.MisoCharacterSprite;

/**
 * The <code>IsoUtil</code> class is a holding place for miscellaneous
 * isometric-display-related utility routines.
 */
public class IsoUtil
    implements DirectionCodes
{
    /**
     * Sets the given character sprite's tile and fine coordinate
     * locations within the scene based on the sprite's current screen
     * pixel coordinates.  This method should be called whenever a
     * {@link MisoCharacterSprite} is first created, but after its
     * location has been set via {@link Sprite#setLocation}.
     */
    public static void setSpriteSceneLocation (
        IsoSceneViewModel model, MisoCharacterSprite sprite)
    {
        // get the sprite's position in full coordinates
        Point fpos = new Point();
        screenToFull(model, sprite.getX(), sprite.getY(), fpos);

        // set the sprite's tile and fine coordinates
        sprite.setTileLocation(
            IsoUtil.fullToTile(fpos.x), IsoUtil.fullToTile(fpos.y));
        sprite.setFineLocation(
            IsoUtil.fullToFine(fpos.x), IsoUtil.fullToFine(fpos.y));
    }

    /**
     * Returns a polygon bounding all footprint tiles of the given
     * object tile.
     *
     * @param model the scene view model.
     * @param root the polygon for the root tile at which the object
     * is located.
     * @param tile the object tile.
     *
     * @return the bounding polygon.
     */
    public static Polygon getObjectFootprint (
        IsoSceneViewModel model, Polygon root, ObjectTile tile)
    {
        Polygon boundsPoly = new SmartPolygon();
        Rectangle bounds = root.getBounds();

        int bwid = tile.getBaseWidth(), bhei = tile.getBaseHeight();
        int oox = bounds.x + model.tilehwid, ooy = bounds.y + model.tilehei;
        int rx = oox, ry = ooy;

        // bottom-center point
        boundsPoly.addPoint(rx, ry);

        // left point
        rx -= bwid * model.tilehwid;
        ry -= bwid * model.tilehhei;
        boundsPoly.addPoint(rx, ry);

        // top-center point
        rx += bhei * model.tilehwid;
        ry -= bhei * model.tilehhei;
        boundsPoly.addPoint(rx, ry);

        // right point
        rx += bwid * model.tilehwid;
        ry += bwid * model.tilehhei;
        boundsPoly.addPoint(rx, ry);

        // bottom-center point
        boundsPoly.addPoint(rx, ry);

        return boundsPoly;
    }

    /**
     * Returns a polygon bounding the given object tile display image,
     * with the bottom of the polygon shaped to conform to the known
     * object dimensions. This is currently not used.
     *
     * @param model the scene view model.
     * @param root the polygon for the root tile at which the object is
     * located.
     * @param tile the object tile.
     *
     * @return the bounding polygon.
     */
    public static Polygon getTightObjectBounds (
        IsoSceneViewModel model, Polygon root, ObjectTile tile)
    {
        Rectangle bounds = root.getBounds();

        // if the tile has an origin, use that, otherwise compute the
        // origin based on the tile footprint
        int tox = tile.getOriginX(), toy = tile.getOriginY();
        if (tox == -1 || toy == -1) {
            tox = tile.getBaseWidth() * model.tilehwid;
            toy = tile.getHeight();
        }

        float slope = (float)model.tilehei / (float)model.tilewid;
        int oox = bounds.x + model.tilehwid, ooy = bounds.y + model.tilehei;
        int sx = oox - tox, sy =  ooy - toy;

        Polygon boundsPoly = new SmartPolygon();
        int rx = sx, ry = sy;

        // top-left point
        boundsPoly.addPoint(rx, ry);

        // top-right point
        rx = sx + tile.getWidth();
        boundsPoly.addPoint(rx, ry);

        // bottom-right point
        ry = ooy - (int)((rx-oox) * slope);
        boundsPoly.addPoint(rx, ry);

        // bottom-middle point
        boundsPoly.addPoint(bounds.x + model.tilehwid,
                            bounds.y + model.tilehei);

        // bottom-left point
        rx = sx;
        ry = ooy - (int)(tox * slope);
        boundsPoly.addPoint(rx, ry);

        // top-left point
        boundsPoly.addPoint(sx, sy);

        return boundsPoly;
    }

    /**
     * Returns a rectangle that encloses the entire object image, with the
     * upper left set to the appropriate values for rendering the object
     * image.
     *
     * @param model the scene view model.
     * @param root the polygon for the root tile at which the object is
     * located.
     * @param tile the object tile.
     *
     * @return the bounding rectangle.
     */
    public static Rectangle getObjectBounds (
        IsoSceneViewModel model, Polygon root, ObjectTile tile)
    {
        Rectangle bounds = root.getBounds();

        // if the tile has an origin, use that, otherwise compute the
        // origin based on the tile footprint
        int tox = tile.getOriginX(), toy = tile.getOriginY();
        if (tox == -1 || toy == -1) {
            tox = tile.getBaseWidth() * model.tilehwid;
            toy = tile.getHeight();
        }

        int oox = bounds.x + model.tilehwid, ooy = bounds.y + model.tilehei;
        int sx = oox - tox, sy =  ooy - toy;

        return new Rectangle(sx, sy, tile.getWidth(), tile.getHeight());
    }

    /**
     * Returns true if the footprints of the two object tiles overlap when
     * the objects occupy the specified coordinates, false if not.
     */
    public static boolean objectFootprintsOverlap (
        ObjectTile tile1, int x1, int y1,
        ObjectTile tile2, int x2, int y2)
    {
        return (x2 > x1 - tile1.getBaseWidth() &&
                x1 > x2 - tile2.getBaseWidth() &&
                y2 > y1 - tile1.getBaseHeight() &&
                y1 > y2 - tile2.getBaseHeight());
    }

    /**
     * Given two points in screen pixel coordinates, return the
     * compass direction that point B lies in from point A from an
     * isometric perspective.
     *
     * @param ax the x-position of point A.
     * @param ay the y-position of point A.
     * @param bx the x-position of point B.
     * @param by the y-position of point B.
     *
     * @return the direction specified as one of the <code>Sprite</code>
     *         class's direction constants.
     */
    public static int getDirection (
        IsoSceneViewModel model, int ax, int ay, int bx, int by)
    {
        Point afpos = new Point(), bfpos = new Point();

        // convert screen coordinates to full coordinates to get both
        // tile coordinates and fine coordinates
        screenToFull(model, ax, ay, afpos);
        screenToFull(model, bx, by, bfpos);

        // pull out the tile coordinates for each point
        int tax = afpos.x / FULL_TILE_FACTOR;
        int tay = afpos.y / FULL_TILE_FACTOR;

        int tbx = bfpos.x / FULL_TILE_FACTOR;
        int tby = bfpos.y / FULL_TILE_FACTOR;

        // compare tile coordinates to determine direction
        int dir = getIsoDirection(tax, tay, tbx, tby);
        if (dir != DirectionCodes.NONE) return dir;

        // destination point is in the same tile as the
        // origination point, so consider fine coordinates

        // pull out the fine coordinates for each point
        int fax = afpos.x - (tax * FULL_TILE_FACTOR);
        int fay = afpos.y - (tay * FULL_TILE_FACTOR);

        int fbx = bfpos.x - (tbx * FULL_TILE_FACTOR);
        int fby = bfpos.y - (tby * FULL_TILE_FACTOR);

        // compare fine coordinates to determine direction
        dir = getIsoDirection(fax, fay, fbx, fby);

        // arbitrarily return southwest if fine coords were also equivalent
        return (dir == -1) ? SOUTHWEST : dir;
    }

    /**
     * Given two points in an isometric coordinate system, return the
     * compass direction that point B lies in from point A.  This method
     * is used to determine direction for both tile coordinates and fine
     * coordinates within a tile, since the coordinate systems are the
     * same.
     *
     * @param ax the x-position of point A.
     * @param ay the y-position of point A.
     * @param bx the x-position of point B.
     * @param by the y-position of point B.
     *
     * @return the direction specified as one of the <code>Sprite</code>
     * class's direction constants, or <code>DirectionCodes.NONE</code> if
     * point B is equivalent to point A.
     */
    public static int getIsoDirection (int ax, int ay, int bx, int by)
    {
        // head off a div by 0 at the pass..
        if (bx == ax) {
            if (by == ay) {
                return DirectionCodes.NONE;
            }
            return (by < ay) ? EAST : WEST;
        }

        // figure direction base on the slope of the line
        float slope = ((float) (ay - by)) / ((float) Math.abs(ax - bx));
        if (slope > 2f) {
            return EAST;
        }
        if (slope > .5f) {
            return (bx < ax) ? NORTHEAST : SOUTHEAST;
        }
        if (slope > -.5f) {
            return (bx < ax) ? NORTH : SOUTH;
        }
        if (slope > -2f) {
            return (bx < ax) ? NORTHWEST : SOUTHWEST;
        }
        return WEST;
    }

    /**
     * Given two points in screen (cartesian) coordinates, return the
     * isometrically projected compass direction that point B lies in from
     * point A.
     *
     * @param ax the x-position of point A.
     * @param ay the y-position of point A.
     * @param bx the x-position of point B.
     * @param by the y-position of point B.
     *
     * @return the direction specified as one of the <code>Sprite</code>
     * class's direction constants, or <code>DirectionCodes.NONE</code> if
     * point B is equivalent to point A.
     */
    public static int getProjectedIsoDirection (int ax, int ay, int bx, int by)
    {
        int dir = getIsoDirection(ax, ay, bx, by);
        if (dir != DirectionCodes.NONE) {
            // offset the direction by -1, ie change SOUTHWEST to SOUTH
            dir = (dir + 7) % 8;
        }
        return nonproj;
    }

    /**
     * Returns the tile coordinate of the given full coordinate.
     */
    public static int fullToTile (int val)
    {
        return (val / FULL_TILE_FACTOR);
    }

    /**
     * Returns the fine coordinate of the given full coordinate.
     */
    public static int fullToFine (int val)
    {
        return (val - ((val / FULL_TILE_FACTOR) * FULL_TILE_FACTOR));
    }

    /**
     * Convert the given screen-based pixel coordinates to their
     * corresponding tile-based coordinates.  Converted coordinates
     * are placed in the given point object.
     *
     * @param sx the screen x-position pixel coordinate.
     * @param sy the screen y-position pixel coordinate.
     * @param tpos the point object to place coordinates in.
     */
    public static void screenToTile (
        IsoSceneViewModel model, int sx, int sy, Point tpos)
    {
        // calculate line parallel to the y-axis (from mouse pos to x-axis)
        int bY = (int)(sy - (model.slopeY * sx));

        // determine intersection of x- and y-axis lines
        int crossx = (int)((bY - (model.bX + model.origin.y)) /
                           (model.slopeX - model.slopeY));
        int crossy = (int)((model.slopeY * crossx) + bY);

        // determine distance of mouse pos along the x axis
        int xdist = (int)MathUtil.distance(
            model.origin.x, model.origin.y, crossx, crossy);
        tpos.x = (int)(xdist / model.tilelen);

        // determine distance of mouse pos along the y-axis
        int ydist = (int)MathUtil.distance(sx, sy, crossx, crossy);
        tpos.y = (int)(ydist / model.tilelen);
    }

    /**
     * Convert the given tile-based coordinates to their corresponding
     * screen-based pixel coordinates.  Converted coordinates are
     * placed in the given point object.
     *
     * @param x the tile x-position coordinate.
     * @param y the tile y-position coordinate.
     * @param spos the point object to place coordinates in.
     */
    public static void tileToScreen (
        IsoSceneViewModel model, int x, int y, Point spos)
    {
        spos.x = model.origin.x + ((x - y - 1) * model.tilehwid);
        spos.y = model.origin.y + ((x + y) * model.tilehhei);
    }

    /**
     * Convert the given fine coordinates to pixel coordinates within
     * the containing tile.  Converted coordinates are placed in the
     * given point object.
     *
     * @param x the x-position fine coordinate.
     * @param y the y-position fine coordinate.
     * @param ppos the point object to place coordinates in.
     */
    public static void fineToPixel (
        IsoSceneViewModel model, int x, int y, Point ppos)
    {
        ppos.x = model.tilehwid + ((x - y) * model.finehwid);
        ppos.y = (x + y) * model.finehhei;
    }

    /**
     * Convert the given pixel coordinates, whose origin is at the
     * top-left of a tile's containing rectangle, to fine coordinates
     * within that tile.  Converted coordinates are placed in the
     * given point object.
     *
     * @param x the x-position pixel coordinate.
     * @param y the y-position pixel coordinate.
     * @param fpos the point object to place coordinates in.
     */
    public static void pixelToFine (
        IsoSceneViewModel model, int x, int y, Point fpos)
    {
        // calculate line parallel to the y-axis (from the given
        // x/y-pos to the x-axis)
        float bY = y - (model.fineSlopeY * x);

        // determine intersection of x- and y-axis lines
        int crossx = (int)((bY - model.fineBX) /
                           (model.fineSlopeX - model.fineSlopeY));
        int crossy = (int)((model.fineSlopeY * crossx) + bY);

        // TODO: final position should check distance between our
        // position and the surrounding fine coords and return the
        // actual closest fine coord, rather than just dividing.

        // determine distance along the x-axis
        float xdist = MathUtil.distance(model.tilehwid, 0, crossx, crossy);
        fpos.x = (int)(xdist / model.finelen);

        // determine distance along the y-axis
        float ydist = MathUtil.distance(x, y, crossx, crossy);
        fpos.y = (int)(ydist / model.finelen);
    }

    /**
     * Convert the given screen-based pixel coordinates to full
     * scene-based coordinates that include both the tile coordinates
     * and the fine coordinates in each dimension.  Converted
     * coordinates are placed in the given point object.
     *
     * @param sx the screen x-position pixel coordinate.
     * @param sy the screen y-position pixel coordinate.
     * @param fpos the point object to place coordinates in.
     */
    public static void screenToFull (
        IsoSceneViewModel model, int sx, int sy, Point fpos)
    {
        // get the tile coordinates
        Point tpos = new Point();
        screenToTile(model, sx, sy, tpos);

        // get the screen coordinates for the containing tile
        Point spos = new Point();
        tileToScreen(model, tpos.x, tpos.y, spos);

        // get the fine coordinates within the containing tile
        pixelToFine(model, sx - spos.x, sy - spos.y, fpos);

        // toss in the tile coordinates for good measure
        fpos.x += (tpos.x * FULL_TILE_FACTOR);
        fpos.y += (tpos.y * FULL_TILE_FACTOR);
    }

    /**
     * Convert the given full coordinates to screen-based pixel
     * coordinates.  Converted coordinates are placed in the given
     * point object.
     *
     * @param x the x-position full coordinate.
     * @param y the y-position full coordinate.
     * @param spos the point object to place coordinates in.
     */
    public static void fullToScreen (
        IsoSceneViewModel model, int x, int y, Point spos)
    {
        // get the tile screen position
        Point tspos = new Point();
        int tx = x / FULL_TILE_FACTOR, ty = y / FULL_TILE_FACTOR;
        tileToScreen(model, tx, ty, tspos);

        // get the pixel position of the fine coords within the tile
        Point ppos = new Point();
        int fx = x - (tx * FULL_TILE_FACTOR), fy = y - (ty * FULL_TILE_FACTOR);
        fineToPixel(model, fx, fy, ppos);

        // final position is tile position offset by fine position
        spos.x = tspos.x + ppos.x;
        spos.y = tspos.y + ppos.y;
    }

    /**
     * Return a polygon framing the specified tile.
     *
     * @param x the tile x-position coordinate.
     * @param y the tile y-position coordinate.
     */
    public static Polygon getTilePolygon (
        IsoSceneViewModel model, int x, int y)
    {
        // get the top-left screen coordinate for the tile
        Point spos = new Point();
        IsoUtil.tileToScreen(model, x, y, spos);

        // create a polygon framing the tile
        Polygon poly = new SmartPolygon();
        poly.addPoint(spos.x, spos.y + model.tilehhei);
        poly.addPoint(spos.x + model.tilehwid, spos.y);
        poly.addPoint(spos.x + model.tilewid, spos.y + model.tilehhei);
        poly.addPoint(spos.x + model.tilehwid, spos.y + model.tilehei);

        return poly;
    }

    /**
     * Return a screen-coordinates polygon framing the two specified
     * tile-coordinate points.
     */
    public static Polygon getMultiTilePolygon (IsoSceneViewModel model,
                                               Point sp1, Point sp2)
    {
        int minx, maxx, miny, maxy;
        Point[] p = new Point[4];

        // load in all possible screen coords
        IsoUtil.tileToScreen(model, sp1.x, sp1.y, p[0] = new Point());
        IsoUtil.tileToScreen(model, sp2.x, sp2.y, p[1] = new Point());
        IsoUtil.tileToScreen(model, sp1.x, sp2.y, p[2] = new Point());
        IsoUtil.tileToScreen(model, sp2.x, sp1.y, p[3] = new Point());

        // locate the indexes of min/max for x and y
        minx = maxx = miny = maxy = 0;
        for (int ii=1; ii < 4; ii++) {
            if (p[ii].x < p[minx].x) {
                minx = ii;
            } else if (p[ii].x > p[maxx].x) {
                maxx = ii;
            }

            if (p[ii].y < p[miny].y) {
                miny = ii;
            } else if (p[ii].y > p[maxy].y) {
                maxy = ii;
            }
        }

        // now make the polygon! Whoo!
        Polygon poly = new SmartPolygon();
        poly.addPoint(p[minx].x, p[minx].y + model.tilehhei);
        poly.addPoint(p[miny].x + model.tilehwid, p[miny].y);
        poly.addPoint(p[maxx].x + model.tilewid, p[maxx].y + model.tilehhei);
        poly.addPoint(p[maxy].x + model.tilehwid, p[maxy].y + model.tilehei);

        return poly;
    }

    /**
     * Turns x and y scene coordinates into an integer key.
     *
     * @return the hash key, given x and y.
     */
    public static final int coordsToKey (int x, int y)
    {
        return ((y << 16) & (0xFFFF0000)) | (x & 0xFFFF);
    }

    /**
     * Gets the x coordinate from an integer hash key.
     *
     * @return the x coordinate.
     */
    public static final int xCoordFromKey (int key)
    {
        return (key & 0xFFFF);
    }

    /**
     * Gets the y coordinate from an integer hash key.
     *
     * @return the y coordinate from the hash key.
     */
    public static final int yCoordFromKey (int key)
    {
        return ((key >> 16) & 0xFFFF);
    }

    /** Multiplication factor to embed tile coords in full coords. */
    protected static final int FULL_TILE_FACTOR = 100;
}
