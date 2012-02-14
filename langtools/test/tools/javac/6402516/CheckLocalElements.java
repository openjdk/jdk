/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 6402516
 * @summary need Trees.getScope(TreePath)
 * @build Checker CheckLocalElements
 * @run main CheckLocalElements
 */

import java.util.*;
import com.sun.source.tree.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

/*
 * Check the local elements of a scope against the contents of string literals.
 */
public class CheckLocalElements extends Checker {
    public static void main(String... args) throws Exception {
        Checker chk = new CheckLocalElements();
        chk.check("TestLocalElements.java");
    }

    @Override
    protected boolean checkLocal(Scope s, String ref) {
        Iterator<? extends Element> elemIter = s.getLocalElements().iterator();
        ref = ref.trim();
        String[] refs = ref.length() == 0 ? new String[0] : ref.split("[ ]*,[ ]*", -1);
        Iterator<String> refIter = Arrays.asList(refs).iterator();
        String r = null;

        nextElem:
        while (elemIter.hasNext()) {
            Element e = elemIter.next();
            try {
                if (r == null)
                    r = refIter.next();

                while (r.endsWith(".*")) {
                    String encl = getEnclosingName(e);
                    String rBase = r.substring(0, r.length() - 2);
                    if (encl.equals(rBase) || encl.startsWith(rBase + "."))
                        continue nextElem;
                    r = refIter.next();
                }

                if (r.equals("-") && (e.getSimpleName().length() == 0)
                    || e.getSimpleName().toString().equals(r)) {
                    r = null;
                    continue nextElem;
                }

                error(s, ref, "mismatch: " + e.getSimpleName() + " " + r);
                return false;

            } catch (NoSuchElementException ex) { // from refIter.next()
                error(s, null, "scope has unexpected entry: " + e.getSimpleName());
                return false;
            }

        }

        if (refIter.hasNext()) {
            error(s, ref, "scope is missing entry: " + refIter.next());
            return false;
        }

        return true;
    }

    private String getEnclosingName(Element e) {
        Element encl = e.getEnclosingElement();
        return encl == null ? "" : encl.accept(qualNameVisitor, null);
    }

    private ElementVisitor<String,Void> qualNameVisitor = new SimpleElementVisitor8<String,Void>() {
        protected String defaultAction(Element e, Void ignore) {
            return "";
        }

        public String visitPackage(PackageElement e, Void ignore) {
            return e.getQualifiedName().toString();
        }

        public String visitType(TypeElement e, Void ignore) {
            return e.getQualifiedName().toString();
        }
    };
}
