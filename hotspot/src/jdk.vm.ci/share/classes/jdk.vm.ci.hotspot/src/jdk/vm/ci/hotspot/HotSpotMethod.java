/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.hotspot;

import static java.util.FormattableFlags.*;
import java.util.*;

import jdk.vm.ci.meta.*;

public abstract class HotSpotMethod implements JavaMethod, Formattable /* , JavaMethodContex */{

    public static String applyFormattingFlagsAndWidth(String s, int flags, int width) {
        if (flags == 0 && width < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);

        // apply width and justification
        int len = sb.length();
        if (len < width) {
            for (int i = 0; i < width - len; i++) {
                if ((flags & LEFT_JUSTIFY) == LEFT_JUSTIFY) {
                    sb.append(' ');
                } else {
                    sb.insert(0, ' ');
                }
            }
        }

        String res = sb.toString();
        if ((flags & UPPERCASE) == UPPERCASE) {
            res = res.toUpperCase();
        }
        return res;
    }

    protected String name;

    /**
     * Controls whether {@link #toString()} includes the qualified or simple name of the class in
     * which the method is declared.
     */
    public static final boolean FULLY_QUALIFIED_METHOD_NAME = false;

    protected HotSpotMethod(String name) {
        this.name = name;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String toString() {
        char h = FULLY_QUALIFIED_METHOD_NAME ? 'H' : 'h';
        String suffix = this instanceof ResolvedJavaMethod ? "" : ", unresolved";
        String fmt = String.format("HotSpotMethod<%%%c.%%n(%%p)%s>", h, suffix);
        return format(fmt);
    }

    public void formatTo(Formatter formatter, int flags, int width, int precision) {
        String base = (flags & ALTERNATE) == ALTERNATE ? getName() : toString();
        formatter.format(applyFormattingFlagsAndWidth(base, flags & ~ALTERNATE, width));
    }

    public JavaMethod asJavaMethod() {
        return this;
    }
}
