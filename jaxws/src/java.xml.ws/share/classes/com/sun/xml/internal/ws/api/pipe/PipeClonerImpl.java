/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.pipe;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.sun.xml.internal.ws.api.pipe.helper.AbstractTubeImpl;

/**
 * Clones the whole pipeline.
 *
 * <p>
 * Since {@link Pipe}s may form an arbitrary directed graph, someone needs
 * to keep track of isomorphism for a clone to happen correctly. This class
 * serves that role.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("deprecation")
public class PipeClonerImpl extends PipeCloner {

    private static final Logger LOGGER = Logger.getLogger(PipeClonerImpl.class.getName());

    // no need to be constructed publicly. always use the static clone method.
    /*package*/ public PipeClonerImpl() {
        super(new HashMap<Object,Object>());
    }

    protected PipeClonerImpl(Map<Object,Object> master2copy) {
        super(master2copy);
    }

    /**
     * {@link Pipe} version of {@link #copy(Tube)}
     */
    @SuppressWarnings("unchecked")
        public <T extends Pipe> T copy(T p) {
        Pipe r = (Pipe)master2copy.get(p);
        if(r==null) {
            r = p.copy(this);
            // the pipe must puts its copy to the map by itself
            assert master2copy.get(p)==r : "the pipe must call the add(...) method to register itself before start copying other pipes, but "+p+" hasn't done so";
        }
        return (T)r;
    }


    /**
     * The {@link Pipe} version of {@link #add(Tube, Tube)}.
     */
    public void add(Pipe original, Pipe copy) {
        assert !master2copy.containsKey(original);
        assert original!=null && copy!=null;
        master2copy.put(original,copy);
    }

    /**
     * Disambiguation version.
     */
    public void add(AbstractTubeImpl original, AbstractTubeImpl copy) {
        add((Tube)original,copy);
    }

        @Override
        public void add(Tube original, Tube copy) {
        assert !master2copy.containsKey(original);
        assert original!=null && copy!=null;
        master2copy.put(original,copy);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Tube> T copy(T t) {
        Tube r = (Tube)master2copy.get(t);
        if(r==null) {
            if (t != null) {
              r = t.copy(this);
            } else {
              if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.fine("WARNING, tube passed to 'copy' in " + this + " was null, so no copy was made");
              }
            }
        }
        return (T)r;
        }
}
