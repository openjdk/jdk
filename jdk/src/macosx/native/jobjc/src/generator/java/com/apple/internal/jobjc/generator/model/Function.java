/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.model;

import java.util.*;

import org.w3c.dom.*;

import com.apple.internal.jobjc.generator.RestrictedKeywords;

public class Function extends Element<Framework> {
    public final boolean variadic;
    public final List<Arg> args;
    public final ReturnValue returnValue;

    public Function(final Node node, final Framework parent) {
        this(node, getAttr(node, "name"), parent);
    }

    public Function(final Node node, final String name, final Framework parent) {
        super(name, parent);

        this.variadic = "true".equals(getAttr(node, "variadic"));
        this.args = new ArrayList<Arg>();

        ReturnValue returnValue = null;

        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            final String childName = child.getLocalName();

            if ("retval".equals(childName)) {
                returnValue = new ReturnValue(child, this);
            }

            if ("arg".equals(childName)) {
                final Arg arg = new Arg(child, this);
                if (arg.name == null || "".equals(arg.name)) {
                    arg.javaName = "arg" + i;
                }
                args.add(arg);
            }
        }

        if (returnValue == null) returnValue = ReturnValue.VOID;
        this.returnValue = returnValue;
    }

    public String getJavaName(){ return name; }

    public void disambiguateArgs() {
        final Set<String> priorArgs = RestrictedKeywords.getNewRestrictedSet();
        for (int i = 0; i < args.size(); i++) {
            final Arg arg = args.get(i);
            if (priorArgs.contains(arg.name)) arg.javaName = arg.javaName + i;
            priorArgs.add(arg.javaName);
        }
    }
}
