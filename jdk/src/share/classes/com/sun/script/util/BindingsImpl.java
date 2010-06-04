/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.script.util;
import java.util.*;
import javax.script.Bindings;

/*
 * Abstract super class for Bindings implementations. Handles
 * global and local scopes.
 *
 * @author Mike Grogan
 * @since 1.6
 */
public abstract class BindingsImpl extends BindingsBase {

    //get method delegates to global if key is not defined in
    //base class or local scope
    protected Bindings global = null;

    //get delegates to local scope
    protected Bindings local = null;

    public void setGlobal(Bindings n) {
        global = n;
    }

    public void setLocal(Bindings n) {
        local = n;
    }

    public  Set<Map.Entry<String, Object>> entrySet() {
        return new BindingsEntrySet(this);
    }

    public Object get(Object key) {
        checkKey(key);

        Object ret  = null;
        if ((local != null) && (null != (ret = local.get(key)))) {
            return ret;
        }

        ret = getImpl((String)key);

        if (ret != null) {
            return ret;
        } else if (global != null) {
            return global.get(key);
        } else {
            return null;
        }
    }

    public Object remove(Object key) {
        checkKey(key);
        Object ret = get(key);
        if (ret != null) {
            removeImpl((String)key);
        }
        return ret;
    }
}
