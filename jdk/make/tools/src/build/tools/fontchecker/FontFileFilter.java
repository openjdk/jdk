/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * <PRE>
 * This class filters TrueType font files from other file
 * found in the font path.
 *
 * </PRE>
 *
 * @author Ilya Bagrak
 */

package build.tools.fontchecker;

import java.awt.*;
import java.io.*;

public class FontFileFilter implements java.io.FileFilter, FontCheckerConstants {

    /**
     * Boolean flag indicating whether this filter filters out
     * non-TrueType fonts.
     */
    private boolean checkNonTTF;

    public FontFileFilter() {
        this(false);
    }

    public FontFileFilter(boolean checkNonTTF) {
        super();
        this.checkNonTTF = checkNonTTF;
    }

    /**
     * Checks whether a file is accepted by this filter.
     * <BR>
     * This method checks whehter a file is accepted by this filter.
     * This filter is made to accept all the file whose extension is
     * either .ttf or .TTF. These files are assumed to be TrueType fonts.
     * <BR><BR>
     * @return returns a boolean value indicating whether or not a file is
     * accepted
     */
    public boolean accept(File pathname) {

        String name = pathname.getName();
        return (name.endsWith(".ttf") ||
                name.endsWith(".TTF") ||
                name.endsWith(".ttc") ||
                name.endsWith(".TTC"))  ||
            (name.endsWith(".pfb") ||
             name.endsWith(".PFB") ||
             name.endsWith(".pfa") ||
             name.endsWith(".PFA") &&
             checkNonTTF == true);
    }

    public static int getFontType(String filename) {
        if (filename.endsWith(".ttf") ||
            filename.endsWith(".TTF") ||
            filename.endsWith(".ttc") ||
            filename.endsWith(".TTC"))
            return Font.TRUETYPE_FONT;
        else if (filename.endsWith(".pfb") ||
                 filename.endsWith(".PFB") ||
                 filename.endsWith(".pfa") ||
                 filename.endsWith(".PFA"))
            return Font.TYPE1_FONT;
        else
            return 999;
    }

}
