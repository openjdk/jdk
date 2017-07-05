/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.rngom.binary;

final class PatternInterner {
    private static final int INIT_SIZE = 256;
    private static final float LOAD_FACTOR = 0.3f;
    private Pattern[] table;
    private int used;
    private int usedLimit;

    PatternInterner() {
        table = null;
        used = 0;
        usedLimit = 0;
    }

    PatternInterner(PatternInterner parent) {
        table = parent.table;
        if (table != null)
            table = (Pattern[]) table.clone();
        used = parent.used;
        usedLimit = parent.usedLimit;
    }

    Pattern intern(Pattern p) {
        int h;

        if (table == null) {
            table = new Pattern[INIT_SIZE];
            usedLimit = (int) (INIT_SIZE * LOAD_FACTOR);
            h = firstIndex(p);
        } else {
            for (h = firstIndex(p); table[h] != null; h = nextIndex(h)) {
                if (p.samePattern(table[h]))
                    return table[h];
            }
        }
        if (used >= usedLimit) {
            // rehash
            Pattern[] oldTable = table;
            table = new Pattern[table.length << 1];
            for (int i = oldTable.length; i > 0;) {
                --i;
                if (oldTable[i] != null) {
                    int j;
                    for (j = firstIndex(oldTable[i]);
                        table[j] != null;
                        j = nextIndex(j));
                    table[j] = oldTable[i];
                }
            }
            for (h = firstIndex(p); table[h] != null; h = nextIndex(h));
            usedLimit = (int) (table.length * LOAD_FACTOR);
        }
        used++;
        table[h] = p;
        return p;
    }

    private int firstIndex(Pattern p) {
        return p.patternHashCode() & (table.length - 1);
    }

    private int nextIndex(int i) {
        return i == 0 ? table.length - 1 : i - 1;
    }
}
