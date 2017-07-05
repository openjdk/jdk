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
import sun.reflect.generics.tree.TypeSignature;
import sun.reflect.generics.parser.SignatureParser;
import sun.reflect.generics.visitor.Reifier;



/**
 * This class represents the generic type information for a constructor.
 * The code is not dependent on a particular reflective implementation.
 * It is designed to be used unchanged by at least core reflection and JDI.
 */
public class FieldRepository extends AbstractRepository<TypeSignature> {

    private Type genericType; // caches the generic type info

 // protected, to enforce use of static factory yet allow subclassing
    protected FieldRepository(String rawSig, GenericsFactory f) {
      super(rawSig, f);
    }

    protected TypeSignature parse(String s) {
        return SignatureParser.make().parseTypeSig(s);
    }

    /**
     * Static factory method.
     * @param rawSig - the generic signature of the reflective object
     * that this repository is servicing
     * @param f - a factory that will provide instances of reflective
     * objects when this repository converts its AST
     * @return a <tt>FieldRepository</tt> that manages the generic type
     * information represented in the signature <tt>rawSig</tt>
     */
    public static FieldRepository make(String rawSig,
                                             GenericsFactory f) {
        return new FieldRepository(rawSig, f);
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

    public Type getGenericType(){
        if (genericType == null) { // lazily initialize generic type
            Reifier r = getReifier(); // obtain visitor
            getTree().accept(r); // reify subtree
            // extract result from visitor and cache it
            genericType = r.getResult();
        }
        return genericType; // return cached result
    }
}
