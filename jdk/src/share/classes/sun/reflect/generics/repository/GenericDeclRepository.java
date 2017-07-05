/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect.generics.repository;

import java.lang.reflect.TypeVariable;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.tree.FormalTypeParameter;
import sun.reflect.generics.tree.Signature;
import sun.reflect.generics.visitor.Reifier;



/**
 * This class represents the generic type information for a generic
 * declaration.
 * The code is not dependent on a particular reflective implementation.
 * It is designed to be used unchanged by at least core reflection and JDI.
 */
public abstract class GenericDeclRepository<S extends Signature>
    extends AbstractRepository<S> {

    private TypeVariable[] typeParams; // caches the formal type parameters

    protected GenericDeclRepository(String rawSig, GenericsFactory f) {
        super(rawSig, f);
    }

    // public API
 /*
 * When queried for a particular piece of type information, the
 * general pattern is to consult the corresponding cached value.
 * If the corresponding field is non-null, it is returned.
 * If not, it is created lazily. This is done by selecting the appropriate
 * part of the tree and transforming it into a reflective object
 * using a visitor.
 * a visitor, which is created by feeding it the factory
 * with which the repository was created.
 */

    /**
     * Return the formal type parameters of this generic declaration.
     * @return the formal type parameters of this generic declaration
     */
    public TypeVariable/*<?>*/[] getTypeParameters(){
        if (typeParams == null) { // lazily initialize type parameters
            // first, extract type parameter subtree(s) from AST
            FormalTypeParameter[] ftps = getTree().getFormalTypeParameters();
            // create array to store reified subtree(s)
            TypeVariable[] tps = new TypeVariable[ftps.length];
            // reify all subtrees
            for (int i = 0; i < ftps.length; i++) {
                Reifier r = getReifier(); // obtain visitor
                ftps[i].accept(r); // reify subtree
                // extract result from visitor and store it
                tps[i] = (TypeVariable<?>) r.getResult();
            }
            typeParams = tps; // cache overall result
        }
        return typeParams.clone(); // return cached result
    }
}
