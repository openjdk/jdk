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

package sun.reflect.generics.scope;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.TypeVariable;



/**
 * Abstract superclass for lazy scope objects, used when building
 * factories for generic information repositories.
 * The type parameter <tt>D</tt> represents the type of reflective
 * object whose scope this class is representing.
 * <p> To subclass this, all one needs to do is implement
 * <tt>computeEnclosingScope</tt> and the subclass' constructor.
 */
public abstract class AbstractScope<D extends GenericDeclaration>
    implements Scope {

    private D recvr; // the declaration whose scope this instance represents
    private Scope enclosingScope; // the enclosing scope of this scope

    /**
     * Constructor. Takes a reflective object whose scope the newly
     * constructed instance will represent.
     * @param D - A generic declaration whose scope the newly
     * constructed instance will represent
     */
    protected AbstractScope(D decl){ recvr = decl;}

    /**
     * Accessor for the receiver - the object whose scope this <tt>Scope</tt>
     * object represents.
     * @return The object whose scope this <tt>Scope</tt> object represents
     */
    protected D getRecvr() {return recvr;}

    /** This method must be implemented by any concrete subclass.
     * It must return the enclosing scope of this scope. If this scope
     * is a top-level scope, an instance of  DummyScope must be returned.
     * @return The enclosing scope of this scope
     */
    protected abstract Scope computeEnclosingScope();

    /**
     * Accessor for the enclosing scope, which is computed lazily and cached.
     * @return the enclosing scope
     */
    protected Scope getEnclosingScope(){
        if (enclosingScope == null) {enclosingScope = computeEnclosingScope();}
        return enclosingScope;
    }

    /**
     * Lookup a type variable in the scope, using its name. Returns null if
     * no type variable with this name is declared in this scope or any of its
     * surrounding scopes.
     * @param name - the name of the type variable being looked up
     * @return the requested type variable, if found
     */
    public TypeVariable<?> lookup(String name) {
        TypeVariable[] tas = getRecvr().getTypeParameters();
        for (TypeVariable/*<?>*/ tv : tas) {
            if (tv.getName().equals(name)) {return tv;}
        }
        return getEnclosingScope().lookup(name);
    }
}
