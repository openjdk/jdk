/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.tools.javac.file;

import java.io.IOException;
import javax.tools.JavaFileObject;

/**
 * Support for legacy ct.properties.
 */
public interface LegacyCtSymAccess {

    public static LegacyCtSymAccess NOOP = new LegacyCtSymAccess() {
        @Override
        public boolean isOnDefaultBootClassPath(JavaFileObject fo) {
            return false;
        }

        @Override
        public LegacyCtSymInfo getInfo(CharSequence packge) {
            throw new UnsupportedOperationException("Should not be called.");
        }
    };

    public boolean isOnDefaultBootClassPath(JavaFileObject fo);

    public LegacyCtSymInfo getInfo(CharSequence packge) throws IOException;

    /**
     * The info that used to be in ct.sym for classes in a package.
     */
    public static class LegacyCtSymInfo {
        /**
         * The classes in this package are internal and not visible.
         */
        public final boolean hidden;
        /**
         * The classes in this package are proprietary and will generate a warning.
         */
        public final boolean proprietary;
        /**
         * The minimum profile in which classes in this package are available.
         */
        public final String minProfile;

        LegacyCtSymInfo(boolean hidden, boolean proprietary, String minProfile) {
            this.hidden = hidden;
            this.proprietary = proprietary;
            this.minProfile = minProfile;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("CtSym[");
            boolean needSep = false;
            if (hidden) {
                sb.append("hidden");
                needSep = true;
            }
            if (proprietary) {
                if (needSep) sb.append(",");
                sb.append("proprietary");
                needSep = true;
            }
            if (minProfile != null) {
                if (needSep) sb.append(",");
                sb.append(minProfile);
            }
            sb.append("]");
            return sb.toString();
        }

        static final LegacyCtSymInfo EMPTY = new LegacyCtSymInfo(false, false, null);
    }

}
