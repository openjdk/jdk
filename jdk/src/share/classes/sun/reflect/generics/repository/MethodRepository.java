/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.reflect.generics.repository;


import java.lang.reflect.Type;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.visitor.Reifier;



/**
 * This class represents the generic type information for a method.
 * The code is not dependent on a particular reflective implementation.
 * It is designed to be used unchanged by at least core reflection and JDI.
 */
public class MethodRepository extends ConstructorRepository {

    private Type returnType; // caches the generic return type info

 // private, to enforce use of static factory
    private MethodRepository(String rawSig, GenericsFactory f) {
      super(rawSig, f);
    }

    /**
     * Static factory method.
     * @param rawSig - the generic signature of the reflective object
     * that this repository is servicing
     * @param f - a factory that will provide instances of reflective
     * objects when this repository converts its AST
     * @return a <tt>MethodRepository</tt> that manages the generic type
     * information represented in the signature <tt>rawSig</tt>
     */
    public static MethodRepository make(String rawSig, GenericsFactory f) {
        return new MethodRepository(rawSig, f);
    }

    // public API

    public Type getReturnType() {
        if (returnType == null) { // lazily initialize return type
            Reifier r = getReifier(); // obtain visitor
            // Extract return type subtree from AST and reify
            getTree().getReturnType().accept(r);
            // extract result from visitor and cache it
            returnType = r.getResult();
            }
        return returnType; // return cached result
    }


}
