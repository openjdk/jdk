/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.scripts;

import jdk.nashorn.api.scripting.JSObject;

/**
 * Nashorn's StructureLoader and ScriptLoader instances load
 * this class in the respective dynamic modules created. This
 * class is never loaded by Nashorn's own class loader. The
 * .class bytes of this class are loaded as resource by the
 * {@link jdk.nashorn.internal.runtime.NashornLoader} class. This class
 * exists in this package because nashorn structures and scripts
 * modules use this package name for the only exported package
 * from those modules.
 *
 * Note that this class may be dynamically generated at runtime.
 * But, this java source is used for ease of reading.
 */
final class ModuleGraphManipulator {
    private ModuleGraphManipulator() {}

    private static final Module MY_MODULE;
    private static final String MY_PKG_NAME;

    static {
        final Class<?> myClass = ModuleGraphManipulator.class;
        MY_MODULE = myClass.getModule();
        final String myName = myClass.getName();
        MY_PKG_NAME = myName.substring(0, myName.lastIndexOf('.'));

        // nashorn's module is the module of the class loader of current class
        final Module nashornModule = myClass.getClassLoader().getClass().getModule();

        // Make sure this class was not loaded by Nashorn's own loader!
        if (MY_MODULE == nashornModule) {
            throw new IllegalStateException(myClass + " loaded by wrong loader!");
        }

        // open package to nashorn module
        MY_MODULE.addOpens(MY_PKG_NAME, nashornModule);
    }

    // The following method is reflectively invoked from Nashorn
    // to add required module export edges. Because this package
    // itself is qualified exported only to nashorn and this
    // method is private, unsafe calls are not possible.

    private static void addExport(final Module otherMod) {
        MY_MODULE.addExports(MY_PKG_NAME, otherMod);
    }
}
