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
package com.sun.codemodel.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * A part is a part of a javadoc comment, and it is a list of values.
 *
 * <p>
 * A part can contain a free-form text. This text is modeled as a collection of 'values'
 * in this class. A value can be a {@link JType} (which will be prinited with a @link tag),
 * anything that can be turned into a {@link String} via the {@link Object#toString()} method,
 * or a {@link Collection}/array of those objects.
 *
 * <p>
 * Values can be added through the various append methods one by one or in a bulk.
 *
 * @author Kohsuke Kawaguchi
 */
public class JCommentPart extends ArrayList<Object> {
    /**
     * Appends a new value.
     *
     * If the value is {@link JType} it will be printed as a @link tag.
     * Otherwise it will be converted to String via {@link Object#toString()}.
     */
    public JCommentPart append(Object o) {
        add(o);
        return this;
    }

    public boolean add(Object o) {
        flattenAppend(o);
        return true;
    }

    private void flattenAppend(Object value) {
        if(value==null) return;
        if(value instanceof Object[]) {
            for( Object o : (Object[])value)
                flattenAppend(o);
        } else
        if(value instanceof Collection) {
            for( Object o : (Collection)value)
                flattenAppend(o);
        } else
            super.add(value);
    }

    /**
     * Writes this part into the formatter by using the specified indentation.
     */
    protected void format( JFormatter f, String indent ) {
        if(!f.isPrinting()) {
            // quickly pass the types to JFormatter
            for( Object o : this )
                if(o instanceof JClass)
                    f.g((JClass)o);
            return;
        }

        if(!isEmpty())
            f.p(indent);

        Iterator itr = iterator();
        while(itr.hasNext()) {
            Object o = itr.next();

            if(o instanceof String) {
                int idx;
                String s = (String)o;
                while( (idx=s.indexOf('\n'))!=-1 ) {
                    String line = s.substring(0,idx);
                    if(line.length()>0)
                        f.p(line);
                    s = s.substring(idx+1);
                    f.nl().p(indent);
                }
                if(s.length()!=0)
                    f.p(s);
            } else
            if(o instanceof JClass) {
                // TODO: this doesn't print the parameterized type properly
                ((JClass)o).printLink(f);
            } else
            if(o instanceof JType) {
                f.g((JType)o);
            } else
                throw new IllegalStateException();
        }

        if(!isEmpty())
            f.nl();
    }
}
