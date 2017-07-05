/*
 * Copyright (c) 1996, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

import sun.tools.tree.*;

/**
 * This is the protocol by which a Parser makes callbacks
 * to the later phases of the compiler.
 * <p>
 * (As a backwards compatibility trick, Parser implements
 * this protocol, so that an instance of a Parser subclass
 * can handle its own actions.  The preferred way to use a
 * Parser, however, is to instantiate it directly with a
 * reference to your own ParserActions implementation.)
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author      John R. Rose
 */
public interface ParserActions {
    /**
     * package declaration
     */
    void packageDeclaration(long off, IdentifierToken nm);

    /**
     * import class
     */
    void importClass(long off, IdentifierToken nm);

    /**
     * import package
     */
    void importPackage(long off, IdentifierToken nm);

    /**
     * Define class
     * @return a cookie for the class
     * This cookie is used by the parser when calling defineField
     * and endClass, and is not examined otherwise.
     */
    ClassDefinition beginClass(long off, String doc,
                               int mod, IdentifierToken nm,
                               IdentifierToken sup, IdentifierToken impl[]);


    /**
     * End class
     * @param c a cookie returned by the corresponding beginClass call
     */
    void endClass(long off, ClassDefinition c);

    /**
     * Define a field
     * @param c a cookie returned by the corresponding beginClass call
     */
    void defineField(long where, ClassDefinition c,
                     String doc, int mod, Type t,
                     IdentifierToken nm, IdentifierToken args[],
                     IdentifierToken exp[], Node val);
}
