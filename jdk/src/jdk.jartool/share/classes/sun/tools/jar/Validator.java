/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.jar;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class Validator implements Consumer<JarEntry> {
    private final static boolean DEBUG = Boolean.getBoolean("jar.debug");
    private final  Map<String,FingerPrint> fps = new HashMap<>();
    private final int vdlen = Main.VERSIONS_DIR.length();
    private final Main main;
    private final JarFile jf;
    private int oldVersion = -1;
    private String currentTopLevelName;
    private boolean isValid = true;

    Validator(Main main, JarFile jf) {
        this.main = main;
        this.jf = jf;
    }

    boolean isValid() {
        return isValid;
    }

    /*
     *  Validator has state and assumes entries provided to accept are ordered
     *  from base entries first and then through the versioned entries in
     *  ascending version order.  Also, to find isolated nested classes,
     *  classes must be ordered so that the top level class is before the associated
     *  nested class(es).
    */
    public void accept(JarEntry je) {
        String entryName = je.getName();

        // directories are always accepted
        if (entryName.endsWith("/")) {
            debug("%s is a directory", entryName);
            return;
        }

        // figure out the version and basename from the JarEntry
        int version;
        String basename;
        if (entryName.startsWith(Main.VERSIONS_DIR)) {
            int n = entryName.indexOf("/", vdlen);
            if (n == -1) {
                main.error(Main.formatMsg("error.validator.version.notnumber", entryName));
                isValid = false;
                return;
            }
            String v = entryName.substring(vdlen, n);
            try {
                version = Integer.parseInt(v);
            } catch (NumberFormatException x) {
                main.error(Main.formatMsg("error.validator.version.notnumber", entryName));
                isValid = false;
                return;
            }
            if (n == entryName.length()) {
                main.error(Main.formatMsg("error.validator.entryname.tooshort", entryName));
                isValid = false;
                return;
            }
            basename = entryName.substring(n + 1);
        } else {
            version = 0;
            basename = entryName;
        }
        debug("\n===================\nversion %d %s", version, entryName);

        if (oldVersion != version) {
            oldVersion = version;
            currentTopLevelName = null;
        }

        // analyze the entry, keeping key attributes
        FingerPrint fp;
        try (InputStream is = jf.getInputStream(je)) {
            fp = new FingerPrint(basename, is.readAllBytes());
        } catch (IOException x) {
            main.error(x.getMessage());
            isValid = false;
            return;
        }
        String internalName = fp.name();

        // process a base entry paying attention to nested classes
        if (version == 0) {
            debug("base entry found");
            if (fp.isNestedClass()) {
                debug("nested class found");
                if (fp.topLevelName().equals(currentTopLevelName)) {
                    fps.put(internalName, fp);
                    return;
                }
                main.error(Main.formatMsg("error.validator.isolated.nested.class", entryName));
                isValid = false;
                return;
            }
            // top level class or resource entry
            if (fp.isClass()) {
                currentTopLevelName = fp.topLevelName();
                if (!checkInternalName(entryName, basename, internalName)) {
                    isValid = false;
                    return;
                }
            }
            fps.put(internalName, fp);
            return;
        }

        // process a versioned entry, look for previous entry with same name
        FingerPrint matchFp = fps.get(internalName);
        debug("looking for match");
        if (matchFp == null) {
            debug("no match found");
            if (fp.isClass()) {
                if (fp.isNestedClass()) {
                    if (!checkNestedClass(version, entryName, internalName, fp)) {
                        isValid = false;
                    }
                    return;
                }
                if (fp.isPublicClass()) {
                    if (!isConcealed(internalName)) {
                        main.error(Main.formatMsg("error.validator.new.public.class", entryName));
                        isValid = false;
                        return;
                    }
                    main.warn(Main.formatMsg("warn.validator.concealed.public.class", entryName));
                    debug("%s is a public class entry in a concealed package", entryName);
                }
                debug("%s is a non-public class entry", entryName);
                fps.put(internalName, fp);
                currentTopLevelName = fp.topLevelName();
                return;
            }
            debug("%s is a resource entry");
            fps.put(internalName, fp);
            return;
        }
        debug("match found");

        // are the two classes/resources identical?
        if (fp.isIdentical(matchFp)) {
            main.warn(Main.formatMsg("warn.validator.identical.entry", entryName));
            return;  // it's okay, just takes up room
        }
        debug("sha1 not equal -- different bytes");

        // ok, not identical, check for compatible class version and api
        if (fp.isClass()) {
            if (fp.isNestedClass()) {
                if (!checkNestedClass(version, entryName, internalName, fp)) {
                    isValid = false;
                }
                return;
            }
            debug("%s is a class entry", entryName);
            if (!fp.isCompatibleVersion(matchFp)) {
                main.error(Main.formatMsg("error.validator.incompatible.class.version", entryName));
                isValid = false;
                return;
            }
            if (!fp.isSameAPI(matchFp)) {
                main.error(Main.formatMsg("error.validator.different.api", entryName));
                isValid = false;
                return;
            }
            if (!checkInternalName(entryName, basename, internalName)) {
                isValid = false;
                return;
            }
            debug("fingerprints same -- same api");
            fps.put(internalName, fp);
            currentTopLevelName = fp.topLevelName();
            return;
        }
        debug("%s is a resource", entryName);

        main.warn(Main.formatMsg("warn.validator.resources.with.same.name", entryName));
        fps.put(internalName, fp);
        return;
    }

    private boolean checkInternalName(String entryName, String basename, String internalName) {
        String className = className(basename);
        if (internalName.equals(className)) {
            return true;
        }
        main.error(Main.formatMsg2("error.validator.names.mismatch",
                entryName, internalName.replace("/", ".")));
        return false;
    }

    private boolean checkNestedClass(int version, String entryName, String internalName, FingerPrint fp) {
        debug("%s is a nested class entry in top level class %s", entryName, fp.topLevelName());
        if (fp.topLevelName().equals(currentTopLevelName)) {
            debug("%s (top level class) was accepted", fp.topLevelName());
            fps.put(internalName, fp);
            return true;
        }
        debug("top level class was not accepted");
        main.error(Main.formatMsg("error.validator.isolated.nested.class", entryName));
        return false;
    }

    private String className(String entryName) {
        return entryName.endsWith(".class") ? entryName.substring(0, entryName.length() - 6) : null;
    }

    private boolean isConcealed(String internalName) {
        if (main.concealedPackages.isEmpty()) {
            return false;
        }
        int idx = internalName.lastIndexOf('/');
        String pkgName = idx != -1 ? internalName.substring(0, idx).replace('/', '.') : "";
        return main.concealedPackages.contains(pkgName);
    }

    private void debug(String fmt, Object... args) {
        if (DEBUG) System.err.format(fmt, args);
    }
}

