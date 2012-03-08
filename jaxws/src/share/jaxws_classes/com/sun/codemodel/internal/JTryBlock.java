/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.codemodel.internal;

import java.util.ArrayList;
import java.util.List;


/**
 * Try statement with Catch and/or Finally clause
 */

public class JTryBlock implements JStatement {

    private JBlock body = new JBlock();
    private List<JCatchBlock> catches = new ArrayList<JCatchBlock>();
    private JBlock _finally = null;

    JTryBlock() {
    }

    public JBlock body() {
        return body;
    }

    public JCatchBlock _catch(JClass exception) {
        JCatchBlock cb = new JCatchBlock(exception);
        catches.add(cb);
        return cb;
    }

    public JBlock _finally() {
        if (_finally == null) _finally = new JBlock();
        return _finally;
    }

    public void state(JFormatter f) {
        f.p("try").g(body);
        for (JCatchBlock cb : catches)
            f.g(cb);
        if (_finally != null)
            f.p("finally").g(_finally);
        f.nl();
    }

}
