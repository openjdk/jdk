/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.apple.internal.jobjc.generator.model.Framework;
import com.apple.internal.jobjc.generator.model.Framework.FrameworkDependency;
import com.apple.internal.jobjc.generator.utils.Fp;

public class Utils {
    public static boolean isLeopard = System.getProperty("os.version").startsWith("10.5");
    public static boolean isSnowLeopard = System.getProperty("os.version").startsWith("10.6");

    @SuppressWarnings("unchecked")
    public static <T> List<T> list(final Object...args) {
        final ArrayList<Object> list = new ArrayList<Object>(args.length);
        for (final Object arg : args) list.add(arg);
        return (List<T>)list;
    }

    /**
     * A small implementation of UNIX find.
     * @param matchRegex Only collect paths that match this regex.
     * @param pruneRegex Don't recurse down a path that matches this regex. May be null.
     * @throws IOException if File.getCanonicalPath() fails.
     */
    public static List<File> find(final File startpath, final String matchRegex, final String pruneRegex) throws IOException{
        final Pattern matchPattern = Pattern.compile(matchRegex, Pattern.CASE_INSENSITIVE);
        final Pattern prunePattern = pruneRegex == null ? null : Pattern.compile(pruneRegex, Pattern.CASE_INSENSITIVE);
        final Set<String> visited = new HashSet<String>();
        final List<File> found = new ArrayList<File>();
        class Search{
            void search(final File path) throws IOException{
                if(prunePattern != null && prunePattern.matcher(path.getAbsolutePath()).matches()) return;
                String cpath = path.getCanonicalPath();
                if(!visited.add(cpath))  return;
                if(matchPattern.matcher(path.getAbsolutePath()).matches())
                    found.add(path);
                if(path.isDirectory())
                    for(File sub : path.listFiles())
                        search(sub);
            }
        }
        new Search().search(startpath);
        return found;
    }

    public static String joinWComma(final List<?> list) { return Fp.join(", ", list); }
    public static String joinWComma(final Object[] list) { return Fp.join(", ", Arrays.asList(list)); }

    public static class Substituter {
        String str;

        public Substituter(final String str) {
            this.str = str.replaceAll("\\#", "\t").replaceAll("\\~", "\n");
        }

        public void replace(final String key, final String value) {
            str = str.replaceAll("\\$" + key, value);
        }

        /**
         * Apply String.format first, and then pass through Substituter.
         */
        public static String format(String format, Object... args){
            return new Substituter(String.format(format, args)).toString();
        }

        @Override public String toString() {
            return str;
        }
    }

    static Map<String, String> getArgs(final String...args) {
        final Map<String, String> argMap = new HashMap<String, String>();
        for (final String arg : args) {
            final String[] splitArg = arg.split("\\=");
            if (splitArg.length != 2) continue;
            argMap.put(splitArg[0], splitArg[1]);
        }
        return argMap;
    }

    static void recDelete(final File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) for (final File f : file.listFiles()) recDelete(f);
        file.delete();
    }

    public static String capitalize(String s){
        if(s.length() == 0) return s;
        return Character.toString(Character.toUpperCase(s.charAt(0))) + s.substring(1);
    }

    /**
     * Sort frameworks by dependencies. If A is a dependency of B,
     * then A will come before B in the list.
     */
    public static void topologicalSort(final List<Framework> frameworks) {
        final Set<Framework> visited = new TreeSet<Framework>();
        final List<Framework> sorted = new ArrayList<Framework>(frameworks.size());
        class Rec{
            void visit(final Framework fw){
                if(!visited.add(fw)) return;
                for(FrameworkDependency dep : fw.dependencies)
                    if(dep.object != null)
                        visit(dep.object);
                sorted.add(fw);
            }
        }
        for(Framework fw : frameworks) new Rec().visit(fw);
        frameworks.clear();
        frameworks.addAll(sorted);
    }

    /**
     * If there is a cycle it is returned. Otherwise null is returned.
     */
    public static List<Framework> getDependencyCycle(List<Framework> frameworks) {
        @SuppressWarnings("serial")
        class FoundCycle extends Throwable{
            public final List<Framework> cycle;
            public FoundCycle(List<Framework> cycle){
                this.cycle = cycle;
            }
        };
        class Rec{
            void visit(final Framework fw, List<Framework> visited) throws FoundCycle{
                visited = new LinkedList<Framework>(visited);
                if(visited.contains(fw)){
                    visited.add(fw);
                    throw new FoundCycle(visited);
                }
                visited.add(fw);
                for(FrameworkDependency dep : fw.dependencies)
                    if(dep.object != null)
                        visit(dep.object, visited);
            }
        }
        try{ for(Framework fw : frameworks){ new Rec().visit(fw, new LinkedList<Framework>()); }}
        catch(FoundCycle x){ return x.cycle; }
        return null;
    }

    public static String getCanonicalPath(File file) throws RuntimeException{
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
