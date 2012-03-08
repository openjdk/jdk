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

package com.sun.tools.internal.xjc.addon.sync;

import java.io.IOException;

import com.sun.codemodel.internal.JMethod;
import com.sun.tools.internal.xjc.BadCommandLineException;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.Plugin;
import com.sun.tools.internal.xjc.outline.ClassOutline;
import com.sun.tools.internal.xjc.outline.Outline;

import org.xml.sax.ErrorHandler;

/**
 * Generates synchronized methods.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class SynchronizedMethodAddOn extends Plugin {

    public String getOptionName() {
        return "Xsync-methods";
    }

    public String getUsage() {
        return "  -Xsync-methods     :  generate accessor methods with the 'synchronized' keyword";
    }

    public int parseArgument(Options opt, String[] args, int i) throws BadCommandLineException, IOException {
        return 0;   // no option recognized
    }

    public boolean run( Outline model, Options opt, ErrorHandler errorHandler ) {

        for( ClassOutline co : model.getClasses() )
            augument(co);

        return true;
    }

    /**
     * Adds "synchoronized" to all the methods.
     */
    private void augument(ClassOutline co) {
        for (JMethod m : co.implClass.methods())
            m.getMods().setSynchronized(true);
    }

}
