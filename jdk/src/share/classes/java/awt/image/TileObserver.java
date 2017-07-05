/*
 * Copyright 1997-1998 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.awt.image;

/**
  * An interface for objects that wish to be informed when tiles
  * of a WritableRenderedImage become modifiable by some writer via
  * a call to getWritableTile, and when they become unmodifiable via
  * the last call to releaseWritableTile.
  *
  * @see WritableRenderedImage
  *
  * @author Thomas DeWeese
  * @author Daniel Rice
  */
public interface TileObserver {

  /**
    * A tile is about to be updated (it is either about to be grabbed
    * for writing, or it is being released from writing).
    *
    * @param source the image that owns the tile.
    * @param tileX the X index of the tile that is being updated.
    * @param tileY the Y index of the tile that is being updated.
    * @param willBeWritable  If true, the tile will be grabbed for writing;
    *                        otherwise it is being released.
    */
    public void tileUpdate(WritableRenderedImage source,
                           int tileX, int tileY,
                           boolean willBeWritable);

}
