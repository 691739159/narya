//
// $Id: TileManager.java,v 1.34 2003/06/18 05:48:45 mdb Exp $

package com.threerings.media.tile;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import com.samskivert.io.PersistenceException;
import com.samskivert.util.HashIntMap;
import com.samskivert.util.IntTuple;
import com.samskivert.util.LRUHashMap;

import com.threerings.media.image.Colorization;
import com.threerings.media.image.ImageDataProvider;
import com.threerings.media.image.ImageManager;
import com.threerings.media.image.Mirage;

import com.threerings.media.Log;
import com.threerings.media.tile.TileUtil;

/**
 * The tile manager provides a simplified interface for retrieving and
 * caching tiles. Tiles can be loaded in two different ways. An
 * application can load a tileset by hand, specifying the path to the
 * tileset image and all of the tileset metadata necessary for extracting
 * the image tiles, or it can provide a tileset repository which loads up
 * tilesets using whatever repository mechanism is implemented by the
 * supplied repository. In the latter case, tilesets are loaded by a
 * unique identifier.
 *
 * <p> Loading tilesets by hand is intended for things like toolbar icons
 * or games with a single set of tiles (think Stratego, for example).
 * Loading tilesets from a repository supports games with vast numbers of
 * tiles to which more tiles may be added on the fly (think the tiles for
 * an isometric-display graphical MUD).
 */
public class TileManager
{
    /**
     * Creates a tile manager and provides it with a reference to the
     * image manager from which it will load tileset images.
     *
     * @param imgr the image manager via which the tile manager will
     * decode and cache images.
     */
    public TileManager (ImageManager imgr)
    {
        _imgr = imgr;
        _defaultProvider = new IMImageProvider(_imgr, (String)null);
    }

    /**
     * Loads up a tileset from the specified image with the specified
     * metadata parameters.
     */
    public UniformTileSet loadTileSet (String imgPath, int width, int height)
    {
        return loadCachedTileSet("", imgPath, width, height);
    }

    /**
     * Loads up a tileset from the specified image (located in the
     * specified resource set) with the specified metadata parameters.
     */
    public UniformTileSet loadTileSet (
        String rset, String imgPath, int width, int height)
    {
        return loadTileSet(
            getImageProvider(rset), rset, imgPath, width, height);
    }

    /**
     */
    public UniformTileSet loadTileSet (
        ImageProvider improv, String improvKey, String imgPath,
        int width, int height)
    {
        UniformTileSet uts = loadCachedTileSet(
            improvKey, imgPath, width, height);
        uts.setImageProvider(improv);
        return uts;
    }

    /**
     * Returns an image provider that will load images from the specified
     * resource set.
     */
    public ImageProvider getImageProvider (String rset)
    {
        return new IMImageProvider(_imgr, rset);
    }

    /**
     * Used to load and cache tilesets loaded via {@link #loadTileSet}.
     */
    protected UniformTileSet loadCachedTileSet (
        String bundle, String imgPath, int width, int height)
    {
        String key = bundle + "::" + imgPath;
        UniformTileSet uts = (UniformTileSet)_handcache.get(key);
        if (uts == null) {
            uts = new UniformTileSet();
            uts.setImageProvider(_defaultProvider);
            uts.setImagePath(imgPath);
            uts.setWidth(width);
            uts.setHeight(height);
            _handcache.put(key, uts);
        }
        return uts;
    }

    /**
     * Sets the tileset repository that will be used by the tile manager
     * when tiles are requested by tileset id.
     */
    public void setTileSetRepository (TileSetRepository setrep)
    {
        _setrep = setrep;
    }

    /**
     * Returns the tileset repository currently in use.
     */
    public TileSetRepository getTileSetRepository ()
    {
	return _setrep;
    }

    /**
     * Returns the tileset with the specified id. Tilesets are fetched
     * from the tileset repository supplied via {@link
     * #setTileSetRepository}, and are subsequently cached.
     *
     * @param tileSetId the unique identifier for the desired tileset.
     *
     * @exception NoSuchTileSetException thrown if no tileset exists with
     * the specified id or if an underlying error occurs with the tileset
     * repository's persistence mechanism.
     */
    public TileSet getTileSet (int tileSetId)
        throws NoSuchTileSetException
    {
        // make sure we have a repository configured
        if (_setrep == null) {
            throw new NoSuchTileSetException(tileSetId);
        }

        try {
            return _setrep.getTileSet(tileSetId);
        } catch (PersistenceException pe) {
            Log.warning("Failure loading tileset [id=" + tileSetId +
                        ", error=" + pe + "].");
            throw new NoSuchTileSetException(tileSetId);
        }
    }

    /**
     * Returns the tileset with the specified name.
     *
     * @throws NoSuchTileSetException if no tileset with the specified
     * name is available via our configured tile set repository.
     */
    public TileSet getTileSet (String name)
        throws NoSuchTileSetException
    {
        // make sure we have a repository configured
        if (_setrep == null) {
            throw new NoSuchTileSetException(name);
        }

        try {
            return _setrep.getTileSet(name);
        } catch (PersistenceException pe) {
            Log.warning("Failure loading tileset [name=" + name +
                        ", error=" + pe + "].");
            throw new NoSuchTileSetException(name);
        }
    }

    /**
     * Returns the {@link Tile} object with the specified fully qualified
     * tile id.
     *
     * @see TileUtil#getFQTileId
     */
    public Tile getTile (int fqTileId)
	throws NoSuchTileSetException, NoSuchTileException
    {
        return getTile(TileUtil.getTileSetId(fqTileId),
                       TileUtil.getTileIndex(fqTileId), null);
    }

    /**
     * Returns the {@link Tile} object with the specified fully qualified
     * tile id. The supplied colorizer will be used to recolor the tile.
     *
     * @see TileUtil#getFQTileId
     */
    public Tile getTile (int fqTileId, TileSet.Colorizer rizer)
	throws NoSuchTileSetException, NoSuchTileException
    {
        return getTile(TileUtil.getTileSetId(fqTileId),
                       TileUtil.getTileIndex(fqTileId), rizer);
    }

    /**
     * Returns the {@link Tile} object from the specified tileset at the
     * specified index.
     *
     * @param tileSetId the tileset id.
     * @param tileIndex the index of the tile to be retrieved.
     *
     * @return the tile object.
     */
    public Tile getTile (int tileSetId, int tileIndex, TileSet.Colorizer rizer)
	throws NoSuchTileSetException, NoSuchTileException
    {
        TileSet set = getTileSet(tileSetId);
        return set.getTile(tileIndex, rizer);
    }

    /** The entity through which we decode and cache images. */
    protected ImageManager _imgr;

    /** A cache of tilesets that have been loaded by hand. */
    protected HashMap _handcache = new HashMap();

    /** The tile set repository. */
    protected TileSetRepository _setrep;

    /** Used to load tileset images from the default resource source. */
    protected ImageProvider _defaultProvider;
}
