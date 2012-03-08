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

package com.sun.tools.internal.xjc.addon.locator;

import java.io.IOException;

import javax.xml.bind.annotation.XmlTransient;

import com.sun.codemodel.internal.JDefinedClass;
import com.sun.codemodel.internal.JMod;
import com.sun.codemodel.internal.JVar;
import com.sun.codemodel.internal.JMethod;
import com.sun.tools.internal.xjc.BadCommandLineException;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.Plugin;
import com.sun.tools.internal.xjc.outline.ClassOutline;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.xml.internal.bind.Locatable;
import com.sun.xml.internal.bind.annotation.XmlLocation;

import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;

/**
 * Generates JAXB objects that implement {@link Locatable}.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class SourceLocationAddOn extends Plugin {

    public String getOptionName() {
        return "Xlocator";
    }

    public String getUsage() {
        return "  -Xlocator          :  enable source location support for generated code";
    }

    public int parseArgument(Options opt, String[] args, int i) throws BadCommandLineException, IOException {
        return 0;   // no option recognized
    }

    private static final String fieldName = "locator";

    public boolean run(
        Outline outline,
        Options opt,
        ErrorHandler errorHandler ) {

        for( ClassOutline ci : outline.getClasses() ) {
            JDefinedClass impl = ci.implClass;
            if (ci.getSuperClass() == null) {
                JVar $loc = impl.field(JMod.PROTECTED, Locator.class, fieldName);
                $loc.annotate(XmlLocation.class);
                $loc.annotate(XmlTransient.class);

                impl._implements(Locatable.class);

                impl.method(JMod.PUBLIC, Locator.class, "sourceLocation").body()._return($loc);

                JMethod setter = impl.method(JMod.PUBLIC, Void.TYPE, "setSourceLocation");
                JVar $newLoc = setter.param(Locator.class, "newLocator");
                setter.body().assign($loc, $newLoc);
            }
        }

        return true;
    }
}
