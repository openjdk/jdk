/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.sjavac.comp;

import javax.lang.model.element.Element;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;

/** Utility class containing dependency information between packages
 *  and the pubapi for a package.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 */
public class Dependencies {
    protected static final Context.Key<Dependencies> dependenciesKey =
        new Context.Key<Dependencies>();

    // The log to be used for error reporting.
    protected Log log;
    // Map from package name to packages that the package depends upon.
    protected Map<Name,Set<Name>> deps;
    // This is the set of all packages that are supplied
    // through the java files at the command line.
    protected Set<Name> explicitPackages;

    // Map from a package name to its public api.
    // Will the Name encode the module in the future?
    // If not, this will have to change to map from Module+Name to public api.
    protected Map<Name,StringBuffer> publicApiPerClass;

    public static Dependencies instance(Context context) {
        Dependencies instance = context.get(dependenciesKey);
        if (instance == null)
            instance = new Dependencies(context);
        return instance;
    }

    private Dependencies(Context context) {
        context.put(dependenciesKey, this);
        log = Log.instance(context);
    }

    public void reset()
    {
        deps = new HashMap<Name, Set<Name>>();
        explicitPackages = new HashSet<Name>();
        publicApiPerClass = new HashMap<Name,StringBuffer>();
    }

    /**
     * Fetch the set of dependencies that are relevant to the compile
     * that has just been performed. I.e. we are only interested in
     * dependencies for classes that were explicitly compiled.
     * @return
     */
    public Map<String,Set<String>> getDependencies() {
        Map<String,Set<String>> new_deps = new HashMap<String,Set<String>>();
        if (explicitPackages == null) return new_deps;
        for (Name pkg : explicitPackages) {
            Set<Name> set = deps.get(pkg);
            if (set != null) {
                Set<String> new_set = new_deps.get(pkg.toString());
                if (new_set == null) {
                    new_set = new HashSet<String>();
                    // Modules beware....
                    new_deps.put(":"+pkg.toString(), new_set);
                }
                for (Name d : set) {
                    new_set.add(":"+d.toString());
                }
            }
        }
        return new_deps;
    }

    static class CompareNames implements Comparator<Name> {
         public int compare(Name a, Name b) {
             return a.toString().compareTo(b.toString());
         }

    }

    /**
     * Convert the map from class names to their pubapi to a map
     * from package names to their pubapi (which is the sorted concatenation
     * of all the class pubapis)
     */
    public Map<String,String> getPubapis() {
        Map<String,String> publicApiPerPackage = new HashMap<String,String>();
        if (publicApiPerClass == null) return publicApiPerPackage;
        Name[] keys = publicApiPerClass.keySet().toArray(new Name[0]);
        Arrays.sort(keys, new CompareNames());
        StringBuffer newPublicApi = new StringBuffer();
        int i=0;
        String prevPkg = "";
        for (Name k : keys) {
            String cn = k.toString();
            String pn = "";
            int dp = cn.lastIndexOf('.');
            if (dp != -1) {
                pn = cn.substring(0,dp);
            }
            if (!pn.equals(prevPkg)) {
                if (!prevPkg.equals("")) {
                    // Add default module name ":"
                    publicApiPerPackage.put(":"+prevPkg, newPublicApi.toString());
                }
                newPublicApi = new StringBuffer();
                prevPkg = pn;
            }
            newPublicApi.append(publicApiPerClass.get(k));
            i++;
        }
        if (!prevPkg.equals(""))
            publicApiPerPackage.put(":"+prevPkg, newPublicApi.toString());
        return publicApiPerPackage;
    }

    /**
     * Visit the api of a class and construct a pubapi string and
     * store it into the pubapi_perclass map.
     */
    public void visitPubapi(Element e) {
        Name n = ((ClassSymbol)e).fullname;
        Name p = ((ClassSymbol)e).packge().fullname;
        StringBuffer sb = publicApiPerClass.get(n);
        assert(sb == null);
        sb = new StringBuffer();
        PubapiVisitor v = new PubapiVisitor(sb);
        v.visit(e);
        if (sb.length()>0) {
            publicApiPerClass.put(n, sb);
        }
        explicitPackages.add(p);
     }

    /**
     * Collect a dependency. curr_pkg is marked as depending on dep_pkg.
     */
    public void collect(Name currPkg, Name depPkg) {
        if (!currPkg.equals(depPkg)) {
            Set<Name> theset = deps.get(currPkg);
            if (theset==null) {
                theset = new HashSet<Name>();
                deps.put(currPkg, theset);
            }
            theset.add(depPkg);
        }
    }
}
