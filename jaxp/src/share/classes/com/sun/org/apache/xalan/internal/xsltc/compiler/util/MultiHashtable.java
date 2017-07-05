/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: MultiHashtable.java,v 1.2.4.1 2005/09/05 11:18:51 pvedula Exp $
 */

package com.sun.org.apache.xalan.internal.xsltc.compiler.util;

import java.util.Hashtable;
import java.util.Vector;

/**
 * @author Jacek Ambroziak
 * @author Santiago Pericas-Geertsen
 */
public final class MultiHashtable extends Hashtable {
    static final long serialVersionUID = -6151608290510033572L;
    public Object put(Object key, Object value) {
        Vector vector = (Vector)get(key);
        if (vector == null)
            super.put(key, vector = new Vector());
        vector.add(value);
        return vector;
    }

    public Object maps(Object from, Object to) {
        if (from == null) return null;
        final Vector vector = (Vector) get(from);
        if (vector != null) {
            final int n = vector.size();
            for (int i = 0; i < n; i++) {
                final Object item = vector.elementAt(i);
                if (item.equals(to)) {
                    return item;
                }
            }
        }
        return null;
    }
}
