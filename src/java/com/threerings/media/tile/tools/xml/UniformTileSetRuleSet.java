//
// $Id: UniformTileSetRuleSet.java,v 1.9 2004/02/25 14:43:17 mdb Exp $

package com.threerings.media.tile.tools.xml;

import org.apache.commons.digester.Digester;

import com.threerings.media.Log;
import com.threerings.media.tile.UniformTileSet;

/**
 * Parses {@link UniformTileSet} instances from a tileset description. A
 * uniform tileset description looks like so:
 *
 * <pre>
 * &lt;tileset name="Sample Uniform Tileset"&gt;
 *   &lt;imagePath&gt;path/to/image.png&lt;/imagePath&gt;
 *   &lt;!-- the width of each tile in pixels --&gt;
 *   &lt;width&gt;64&lt;/width&gt;
 *   &lt;!-- the height of each tile in pixels --&gt;
 *   &lt;height&gt;48&lt;/height&gt;
 * &lt;/tileset&gt;
 * </pre>
 */
public class UniformTileSetRuleSet extends TileSetRuleSet
{
    // documentation inherited
    public void addRuleInstances (Digester digester)
    {
        super.addRuleInstances(digester);

        digester.addCallMethod(
            _prefix + TILESET_PATH + "/width", "setWidth", 0,
            new Class[] { java.lang.Integer.TYPE });
        digester.addCallMethod(
            _prefix + TILESET_PATH + "/height", "setHeight", 0,
            new Class[] { java.lang.Integer.TYPE });
    }

    // documentation inherited
    public boolean isValid (Object target)
    {
        UniformTileSet set = (UniformTileSet)target;
        boolean valid = super.isValid(target);

        // check for a <width> element
        if (set.getWidth() == 0) {
            Log.warning("Tile set definition missing valid <width> " +
                        "element [set=" + set + "].");
            valid = false;
        }

        // check for a <height> element
        if (set.getHeight() == 0) {
            Log.warning("Tile set definition missing valid <height> " +
                        "element [set=" + set + "].");
            valid = false;
        }

        return valid;
    }

    // documentation inherited
    protected Class getTileSetClass ()
    {
        return UniformTileSet.class;
    }
}
