/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model.nav;

import com.sun.codemodel.internal.JClass;
import com.sun.tools.internal.xjc.outline.Aspect;
import com.sun.tools.internal.xjc.outline.Outline;

/**
 * Parameterized type.
 *
 * @author Kohsuke Kawaguchi
 */
final class NParameterizedType implements NClass {

    final NClass rawType;
    final NType[] args;

    NParameterizedType(NClass rawType, NType[] args) {
        this.rawType = rawType;
        this.args = args;
        assert args.length>0;
    }

    public JClass toType(Outline o, Aspect aspect) {
        JClass r = rawType.toType(o,aspect);

        for( NType arg : args )
            r = r.narrow(arg.toType(o,aspect).boxify());

        return r;
    }

    public boolean isAbstract() {
        return rawType.isAbstract();
    }

    public boolean isBoxedType() {
        return false;
    }


    public String fullName() {
        StringBuilder buf = new StringBuilder();
        buf.append(rawType.fullName());
        buf.append('<');
        for( int i=0; i<args.length; i++ ) {
            if(i!=0)
                buf.append(',');
            buf.append(args[i].fullName());
        }
        buf.append('>');
        return buf.toString();
    }
}
