/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static java.util.jar.JarFile.MANIFEST_NAME;
import static sun.tools.jar.Main.VERSIONS_DIR;
import static sun.tools.jar.Main.MODULE_INFO;
import static sun.tools.jar.Main.getMsg;
import static sun.tools.jar.Main.formatMsg;
import static sun.tools.jar.Main.formatMsg2;
import static sun.tools.jar.Main.toBinaryName;
import static sun.tools.jar.Main.isModuleInfoEntry;

final class Validator {
    private final static boolean DEBUG = Boolean.getBoolean("jar.debug");
    private final  Map<String,FingerPrint> fps = new HashMap<>();
    private static final int vdlen = VERSIONS_DIR.length();
    private final Main main;
    private final JarFile jf;
    private int oldVersion = -1;
    private String currentTopLevelName;
    private boolean isValid = true;
    private Set<String> concealedPkgs;
    private ModuleDescriptor md;

    private Validator(Main main, JarFile jf) {
        this.main = main;
        this.jf = jf;
        loadModuleDescriptor();
    }

    static boolean validate(Main main, JarFile jf) throws IOException {
        return new Validator(main, jf).validate();
    }

    private boolean validate() {
        try {
            jf.stream()
              .filter(e -> !e.isDirectory() &&
                      !e.getName().equals(MANIFEST_NAME))
              .sorted(entryComparator)
              .forEachOrdered(e -> validate(e));
            return isValid;
        } catch (InvalidJarException e) {
            error(formatMsg("error.validator.bad.entry.name", e.getMessage()));
        }
        return false;
    }

    private static class InvalidJarException extends RuntimeException {
        private static final long serialVersionUID = -3642329147299217726L;
        InvalidJarException(String msg) {
            super(msg);
        }
    }

    // sort base entries before versioned entries, and sort entry classes with
    // nested classes so that the top level class appears before the associated
    // nested class
    private static Comparator<JarEntry> entryComparator = (je1, je2) ->  {
        String s1 = je1.getName();
        String s2 = je2.getName();
        if (s1.equals(s2)) return 0;
        boolean b1 = s1.startsWith(VERSIONS_DIR);
        boolean b2 = s2.startsWith(VERSIONS_DIR);
        if (b1 && !b2) return 1;
        if (!b1 && b2) return -1;
        int n = 0; // starting char for String compare
        if (b1 && b2) {
            // normally strings would be sorted so "10" goes before "9", but
            // version number strings need to be sorted numerically
            n = VERSIONS_DIR.length();   // skip the common prefix
            int i1 = s1.indexOf('/', n);
            int i2 = s1.indexOf('/', n);
            if (i1 == -1) throw new InvalidJarException(s1);
            if (i2 == -1) throw new InvalidJarException(s2);
            // shorter version numbers go first
            if (i1 != i2) return i1 - i2;
            // otherwise, handle equal length numbers below
        }
        int l1 = s1.length();
        int l2 = s2.length();
        int lim = Math.min(l1, l2);
        for (int k = n; k < lim; k++) {
            char c1 = s1.charAt(k);
            char c2 = s2.charAt(k);
            if (c1 != c2) {
                // change natural ordering so '.' comes before '$'
                // i.e. top level classes come before nested classes
                if (c1 == '$' && c2 == '.') return 1;
                if (c1 == '.' && c2 == '$') return -1;
                return c1 - c2;
            }
        }
        return l1 - l2;
    };

    /*
     *  Validator has state and assumes entries provided to accept are ordered
     *  from base entries first and then through the versioned entries in
     *  ascending version order.  Also, to find isolated nested classes,
     *  classes must be ordered so that the top level class is before the associated
     *  nested class(es).
    */
    public void validate(JarEntry je) {
        String entryName = je.getName();

        // directories are always accepted
        if (entryName.endsWith("/")) {
            debug("%s is a directory", entryName);
            return;
        }

        // validate the versioned module-info
        if (isModuleInfoEntry(entryName)) {
            if (entryName.length() != MODULE_INFO.length())
                checkModuleDescriptor(je);
            return;
        }

        // figure out the version and basename from the JarEntry
        int version;
        String basename;
        if (entryName.startsWith(VERSIONS_DIR)) {
            int n = entryName.indexOf("/", vdlen);
            if (n == -1) {
                error(formatMsg("error.validator.version.notnumber", entryName));
                isValid = false;
                return;
            }
            String v = entryName.substring(vdlen, n);
            try {
                version = Integer.parseInt(v);
            } catch (NumberFormatException x) {
                error(formatMsg("error.validator.version.notnumber", entryName));
                isValid = false;
                return;
            }
            if (n == entryName.length()) {
                error(formatMsg("error.validator.entryname.tooshort", entryName));
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
            error(x.getMessage());
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
                error(formatMsg("error.validator.isolated.nested.class", entryName));
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
                        error(Main.formatMsg("error.validator.new.public.class", entryName));
                        isValid = false;
                        return;
                    }
                    warn(formatMsg("warn.validator.concealed.public.class", entryName));
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
            warn(formatMsg("warn.validator.identical.entry", entryName));
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
                error(formatMsg("error.validator.incompatible.class.version", entryName));
                isValid = false;
                return;
            }
            if (!fp.isSameAPI(matchFp)) {
                error(formatMsg("error.validator.different.api", entryName));
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

        warn(formatMsg("warn.validator.resources.with.same.name", entryName));
        fps.put(internalName, fp);
        return;
    }

    private void loadModuleDescriptor() {
        ZipEntry je = jf.getEntry(MODULE_INFO);
        if (je != null) {
            try (InputStream jis = jf.getInputStream(je)) {
                md = ModuleDescriptor.read(jis);
                concealedPkgs = new HashSet<>(md.packages());
                md.exports().stream().map(Exports::source).forEach(concealedPkgs::remove);
                md.opens().stream().map(Opens::source).forEach(concealedPkgs::remove);
                return;
            } catch (Exception x) {
                error(x.getMessage() + " : " + je.getName());
                this.isValid = false;
            }
        }
        md = null;
        concealedPkgs = Collections.emptySet();
    }

    private static boolean isPlatformModule(String name) {
        return name.startsWith("java.") || name.startsWith("jdk.");
    }

    /**
     * Checks whether or not the given versioned module descriptor's attributes
     * are valid when compared against the root module descriptor.
     *
     * A versioned module descriptor must be identical to the root module
     * descriptor, with two exceptions:
     *  - A versioned descriptor can have different non-public `requires`
     *    clauses of platform ( `java.*` and `jdk.*` ) modules, and
     *  - A versioned descriptor can have different `uses` clauses, even of
     *    service types defined outside of the platform modules.
     */
    private void checkModuleDescriptor(JarEntry je) {
        try (InputStream is = jf.getInputStream(je)) {
            ModuleDescriptor root = this.md;
            ModuleDescriptor md = null;
            try {
                md = ModuleDescriptor.read(is);
            } catch (InvalidModuleDescriptorException x) {
                error(x.getMessage());
                isValid = false;
                return;
            }
            if (root == null) {
                this.md = md;
            } else {
                if (!root.name().equals(md.name())) {
                    error(getMsg("error.validator.info.name.notequal"));
                    isValid = false;
                }
                if (!root.requires().equals(md.requires())) {
                    Set<Requires> rootRequires = root.requires();
                    for (Requires r : md.requires()) {
                        if (rootRequires.contains(r))
                            continue;
                        if (r.modifiers().contains(Requires.Modifier.TRANSITIVE)) {
                            error(getMsg("error.validator.info.requires.transitive"));
                            isValid = false;
                        } else if (!isPlatformModule(r.name())) {
                            error(getMsg("error.validator.info.requires.added"));
                            isValid = false;
                        }
                    }
                    for (Requires r : rootRequires) {
                        Set<Requires> mdRequires = md.requires();
                        if (mdRequires.contains(r))
                            continue;
                        if (!isPlatformModule(r.name())) {
                            error(getMsg("error.validator.info.requires.dropped"));
                            isValid = false;
                        }
                    }
                }
                if (!root.exports().equals(md.exports())) {
                    error(getMsg("error.validator.info.exports.notequal"));
                    isValid = false;
                }
                if (!root.opens().equals(md.opens())) {
                    error(getMsg("error.validator.info.opens.notequal"));
                    isValid = false;
                }
                if (!root.provides().equals(md.provides())) {
                    error(getMsg("error.validator.info.provides.notequal"));
                    isValid = false;
                }
                if (!root.mainClass().equals(md.mainClass())) {
                    error(formatMsg("error.validator.info.manclass.notequal", je.getName()));
                    isValid = false;
                }
                if (!root.version().equals(md.version())) {
                    error(formatMsg("error.validator.info.version.notequal", je.getName()));
                    isValid = false;
                }
            }
        } catch (IOException x) {
            error(x.getMessage());
            isValid = false;
        }
    }

    private boolean checkInternalName(String entryName, String basename, String internalName) {
        String className = className(basename);
        if (internalName.equals(className)) {
            return true;
        }
        error(formatMsg2("error.validator.names.mismatch",
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
        error(formatMsg("error.validator.isolated.nested.class", entryName));
        return false;
    }

    private String className(String entryName) {
        return entryName.endsWith(".class") ? entryName.substring(0, entryName.length() - 6) : null;
    }

    private boolean isConcealed(String internalName) {
        if (concealedPkgs.isEmpty()) {
            return false;
        }
        int idx = internalName.lastIndexOf('/');
        String pkgName = idx != -1 ? internalName.substring(0, idx).replace('/', '.') : "";
        return concealedPkgs.contains(pkgName);
    }

    private void debug(String fmt, Object... args) {
        if (DEBUG) System.err.format(fmt, args);
    }

    private void error(String msg) {
        main.error(msg);
    }

    private void warn(String msg) {
        main.warn(msg);
    }

}
