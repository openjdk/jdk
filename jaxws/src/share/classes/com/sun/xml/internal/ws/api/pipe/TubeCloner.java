/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.api.pipe;

import java.util.HashMap;
import java.util.Map;

/**
 * Clones the whole pipeline.
 *
 * <p>
 * Since {@link Tube}s may form an arbitrary directed graph, someone needs
 * to keep track of isomorphism for a clone to happen correctly. This class
 * serves that role.
 *
 * @author Kohsuke Kawaguchi
 */
public class TubeCloner {
    // Pipe to pipe, or tube to tube
    protected final Map<Object,Object> master2copy = new HashMap<Object,Object>();

    /*package*/ TubeCloner() {
    }

    /**
     * Invoked by a client of a tube to clone the whole pipeline.
     *
     * <p>
     * {@link Tube}s implementing the {@link Tube#copy(TubeCloner)} method
     * shall use {@link #copy(Tube)} method.
     *
     * @param p
     *      The entry point of a pipeline to be copied. must not be null.
     * @return
     *      The cloned pipeline. Always non-null.
     */
    public static Tube clone(Tube p) {
        // we often want to downcast TubeCloner to PipeCloner,
        // so let's create PipeCloner to make that possible
        return new PipeCloner().copy(p);
    }

    /**
     * Invoked by a {@link Tube#copy(TubeCloner)} implementation
     * to copy a reference to another pipe.
     *
     * <p>
     * This method is for {@link Tube} implementations, not for users.
     *
     * <p>
     * If the given tube is already copied for this cloning episode,
     * this method simply returns that reference. Otherwise it copies
     * a tube, make a note, and returns a copied tube. This additional
     * step ensures that a graph is cloned isomorphically correctly.
     *
     * <p>
     * (Think about what happens when a graph is A->B, A->C, B->D, and C->D
     * if you don't have this step.)
     *
     * @param t
     *      The tube to be copied.
     * @return
     *      The cloned tube. Always non-null.
     */
    public <T extends Tube> T copy(T t) {
        Tube r = (Tube)master2copy.get(t);
        if(r==null) {
            r = t.copy(this);
            // the pipe must puts its copy to the map by itself
            assert master2copy.get(t)==r : "the tube must call the add(...) method to register itself before start copying other pipes, but "+t +" hasn't done so";
        }
        return (T)r;
    }

    /**
     * This method must be called from within the copy constructor
     * to notify that the copy was created.
     *
     * <p>
     * When your pipe has references to other pipes,
     * it's particularly important to call this method
     * before you start copying the pipes you refer to,
     * or else there's a chance of inifinite loop.
     */
    public void add(Tube original, Tube copy) {
        assert !master2copy.containsKey(original);
        assert original!=null && copy!=null;
        master2copy.put(original,copy);
    }
}
