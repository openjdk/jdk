/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8178982 8220497 8210683 8241982 8297216 8303056
 * @summary Test the search feature of javadoc.
 * @library ../../lib
 * @library /test/lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.*
 * @build jtreg.SkippedException
 * @run main TestSearchScript
 */

import javadoc.tester.JavadocTester;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jtreg.SkippedException;

/*
 * Tests for the search feature using any available javax.script JavaScript engine.
 * The test is skipped if no JavaScript engine is available.
 */
public class TestSearchScript extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestSearchScript();
        tester.runTests();
    }

    private Invocable getEngine() throws ScriptException, IOException, NoSuchMethodException {
        // For installing and using GraalVM JS on stock JDK see
        // https://github.com/oracle/graaljs/blob/master/docs/user/RunOnJDK.md
        // and https://github.com/graalvm/graal-js-jdk11-maven-demo
        ScriptEngineManager engineManager = new ScriptEngineManager();
        // Use "js" engine name to use any available JavaScript engine.
        ScriptEngine engine = engineManager.getEngineByName("js");
        if (engine == null) {
            throw new SkippedException("JavaScript engine is not available.");
        }
        // Set Nashorn compatibility mode via Bindings for use with GraalVM JS,
        // see https://github.com/graalvm/graaljs/blob/master/docs/user/ScriptEngine.md
        Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.put("polyglot.js.nashorn-compat", true);
        engine.eval(Files.newBufferedReader(Path.of(testSrc).resolve("javadoc-search.js")));
        Invocable inv = (Invocable) engine;
        inv.invokeFunction("loadIndexFiles", outputDir.toAbsolutePath().toString());
        return inv;
    }

    @Test
    public void testModuleSearch() throws ScriptException, IOException, NoSuchMethodException {
        javadoc("-d", "out-full",
                "-Xdoclint:none",
                "-use",
                "--module-source-path", testSrc,
                "--module", "mapmodule",
                "mappkg", "mappkg.impl");
        checkExit(Exit.OK);

        Invocable inv = getEngine();

        // exact match, case sensitivity
        checkSearch(inv, "mapmodule", List.of("mapmodule"));
        checkSearch(inv, "mappkg", List.of("mapmodule/mappkg", "mapmodule/mappkg.impl", "mappkg.system.property"));
        checkSearch(inv, "Mapmodule", List.of("mapmodule"));
        checkSearch(inv, "Mappkg", List.of("mapmodule/mappkg", "mapmodule/mappkg.impl", "mappkg.system.property"));
        checkSearch(inv, "mymap", List.of("mappkg.impl.MyMap", "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "MyMap", List.of("mappkg.impl.MyMap", "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "mymap(", List.of("mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "MyMap(", List.of("mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "mymap()", List.of("mappkg.impl.MyMap.MyMap()"));
        checkSearch(inv, "MyMap()", List.of("mappkg.impl.MyMap.MyMap()"));
        checkSearch(inv, "Mymap", List.of("mappkg.impl.MyMap", "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "Mymap()", List.of("mappkg.impl.MyMap.MyMap()"));

        // left boundaries, ranking
        checkSearch(inv, "map", List.of("mapmodule", "mapmodule/mappkg", "mapmodule/mappkg.impl", "mappkg.Map", "mappkg.impl.MyMap",
                                        "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)", "mappkg.system.property"));
        checkSearch(inv, "Map", List.of("mapmodule", "mapmodule/mappkg", "mapmodule/mappkg.impl", "mappkg.Map", "mappkg.impl.MyMap",
                                        "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)", "mappkg.system.property"));
        checkSearch(inv, "MAP", List.of("mapmodule", "mapmodule/mappkg", "mapmodule/mappkg.impl", "mappkg.Map", "mappkg.impl.MyMap",
                                        "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)", "mappkg.system.property"));
        checkSearch(inv, "value", List.of("mappkg.impl.MyMap.some_value", "mappkg.impl.MyMap.OTHER_VALUE"));
        checkSearch(inv, "VALUE", List.of("mappkg.impl.MyMap.OTHER_VALUE", "mappkg.impl.MyMap.some_value"));
        checkSearch(inv, "map.other", List.of("mappkg.impl.MyMap.OTHER_VALUE"));
        checkSearch(inv, "Map.Some_", List.of("mappkg.impl.MyMap.some_value"));

        checkSearch(inv, "Mm", List.of());
        checkSearch(inv, "mym", List.of("mappkg.impl.MyMap", "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "imp.mym.mym(", List.of("mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "imp.mym.mym(m", List.of("mappkg.impl.MyMap.MyMap(Map)"));

        // camel case
        checkSearch(inv, "MM", List.of("mappkg.impl.MyMap", "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "MyM", List.of("mappkg.impl.MyMap", "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "Mym", List.of("mappkg.impl.MyMap", "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "i.MyM.MyM(", List.of("mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "i.MMa.MMa(", List.of("mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "i.MyM.MyM(Ma", List.of("mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "i.MMa.MMa(M", List.of("mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "i.Mym.MyM(", List.of("mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)"));
        checkSearch(inv, "i.Mym.Ma(", List.of());

        checkSearch(inv, "mapm", List.of("mapmodule"));

        // child entity listing
        checkSearch(inv, "mapmodule/", List.of("mapmodule/mappkg", "mapmodule/mappkg.impl"));
        checkSearch(inv, "mapmod/", List.of("mapmodule/mappkg", "mapmodule/mappkg.impl"));
        checkSearch(inv, "module/", List.of());
        checkSearch(inv, "le/", List.of());
        checkSearch(inv, "mapmodule.", List.of());
        checkSearch(inv, "mapmod.", List.of());
        checkSearch(inv, "mappkg.", List.of("mapmodule/mappkg.impl", "mappkg.Map", "mappkg.system.property"));
        checkSearch(inv, "mappkg.", List.of("mapmodule/mappkg.impl", "mappkg.Map", "mappkg.system.property"));
        checkSearch(inv, "Map.", List.of("mapmodule/mappkg.impl", "mappkg.Map", "mappkg.Map.contains(Object)",
                                         "mappkg.Map.get(Object)", "mappkg.Map.iterate()", "mappkg.Map.put(Object, Object)",
                                         "mappkg.Map.remove(Object)", "mappkg.impl.MyMap.contains(Object)",
                                         "mappkg.impl.MyMap.get(Object)", "mappkg.impl.MyMap.iterate()",
                                         "mappkg.impl.MyMap.MyMap()", "mappkg.impl.MyMap.MyMap(Map)",
                                         "mappkg.impl.MyMap.OTHER_VALUE", "mappkg.impl.MyMap.put(Object, Object)",
                                         "mappkg.impl.MyMap.remove(Object)", "mappkg.impl.MyMap.some_value",
                                         "mappkg.system.property"));
        checkSearch(inv, "mym.", List.of("mappkg.impl.MyMap.contains(Object)", "mappkg.impl.MyMap.get(Object)",
                                         "mappkg.impl.MyMap.iterate()", "mappkg.impl.MyMap.MyMap()",
                                         "mappkg.impl.MyMap.MyMap(Map)", "mappkg.impl.MyMap.OTHER_VALUE",
                                         "mappkg.impl.MyMap.put(Object, Object)", "mappkg.impl.MyMap.remove(Object)",
                                         "mappkg.impl.MyMap.some_value"));
        checkSearch(inv, "MyMap.i", List.of("mappkg.impl.MyMap.iterate()"));

        // system properties
        checkSearch(inv, "mappkg.system.property", List.of("mappkg.system.property"));
        checkSearch(inv, "system.property", List.of("mappkg.system.property"));
        checkSearch(inv, "property", List.of("mappkg.system.property"));
        checkSearch(inv, "sys.prop", List.of("mappkg.system.property"));
        checkSearch(inv, "m.s.p", List.of("mappkg.system.property"));
        checkSearch(inv, "operty", List.of());

        // search tag
        checkSearch(inv, "search tag", List.of("search tag", "multiline search tag"));
        checkSearch(inv, "search   tag", List.of("search tag", "multiline search tag"));
        checkSearch(inv, "search ", List.of("search tag", "multiline search tag"));
        checkSearch(inv, "tag", List.of("search tag", "multiline search tag"));
        checkSearch(inv, "sea", List.of("search tag", "multiline search tag"));
        checkSearch(inv, "multi", List.of("multiline search tag"));
        checkSearch(inv, "ear", List.of());

        // multiple space separated tokens
        checkSearch(inv, "my map map", List.of("mappkg.impl.MyMap.MyMap(Map)", "mappkg.impl.MyMap.MyMap()"));
        checkSearch(inv, "map get", List.of("mappkg.Map.get(Object)", "mappkg.impl.MyMap.get(Object)"));
        checkSearch(inv, "get", List.of("mappkg.impl.MyMap.get(Object)", "mappkg.Map.get(Object)"));
        checkSearch(inv, "get o", List.of("mappkg.Map.get(Object)", "mappkg.impl.MyMap.get(Object)"));
        checkSearch(inv, "put o o", List.of("mappkg.Map.put(Object, Object)", "mappkg.impl.MyMap.put(Object, Object)"));
        checkSearch(inv, "put(o o)", List.of("mappkg.Map.put(Object, Object)", "mappkg.impl.MyMap.put(Object, Object)"));
    }

    @Test
    public void testPackageSource() throws ScriptException, IOException, NoSuchMethodException {
        javadoc("-d", "out-overload",
                "-Xdoclint:none",
                "-use",
                "-sourcepath", testSrc,
                "listpkg");
        checkExit(Exit.OK);

        Invocable inv = getEngine();

        // exact match, case sensitvity, left boundaries
        checkSearch(inv, "list", List.of("listpkg", "listpkg.List", "listpkg.MyList", "listpkg.ListProvider",
                                         "listpkg.MyListFactory", "listpkg.MyList.MyList()",
                                         "listpkg.MyListFactory.createList(ListProvider, MyListFactory)",
                                         "listpkg.ListProvider.ListProvider()", "listpkg.ListProvider.makeNewList()",
                                         "listpkg.MyListFactory.MyListFactory()"));
        checkSearch(inv, "List", List.of("listpkg", "listpkg.List", "listpkg.MyList", "listpkg.ListProvider",
                                         "listpkg.MyListFactory", "listpkg.MyList.MyList()",
                                         "listpkg.MyListFactory.createList(ListProvider, MyListFactory)",
                                         "listpkg.ListProvider.ListProvider()", "listpkg.ListProvider.makeNewList()",
                                         "listpkg.MyListFactory.MyListFactory()"));
        // partial match
        checkSearch(inv, "fact", List.of("listpkg.MyListFactory", "listpkg.MyListFactory.MyListFactory()"));
        checkSearch(inv, "pro", List.of("listpkg.ListProvider", "listpkg.ListProvider.ListProvider()"));
        checkSearch(inv, "listpro", List.of("listpkg.ListProvider", "listpkg.ListProvider.ListProvider()"));

        // camel case
        checkSearch(inv, "l.MLF.cL(LP, MLF)", List.of("listpkg.MyListFactory.createList(ListProvider, MyListFactory)"));
        checkSearch(inv, "Fact.creaLi(LiPro,MLiFact)", List.of("listpkg.MyListFactory.createList(ListProvider, MyListFactory)"));
        checkSearch(inv, "(LP,ML", List.of("listpkg.MyListFactory.createList(ListProvider, MyListFactory)"));

        // ranking of overloaded methods JDK-8210683
        checkSearch(inv, "list.of",
                List.of("listpkg.List.of()", "listpkg.List.of(E)", "listpkg.List.of(E, E)",
                        "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)", "listpkg.List.of(E...)"));
        checkSearch(inv, "Li.of",
                List.of("listpkg.List.of()", "listpkg.List.of(E)", "listpkg.List.of(E, E)",
                        "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)", "listpkg.List.of(E...)"));
        checkSearch(inv, "li.Li.o",
                List.of("listpkg.List.of()", "listpkg.List.of(E)", "listpkg.List.of(E, E)",
                        "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)", "listpkg.List.of(E...)"));
        checkSearch(inv, "l.l.o",
                List.of("listpkg.List.of()", "listpkg.List.of(E)", "listpkg.List.of(E, E)",
                        "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)", "listpkg.List.of(E...)"));
        checkSearch(inv, "L.l.o", List.of("listpkg.List.of()", "listpkg.List.of(E)", "listpkg.List.of(E, E)",
                        "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)", "listpkg.List.of(E, E, E, E, E)",
                        "listpkg.List.of(E...)"));

        // whitespace
        checkSearch(inv, "(e,e,e",
                List.of("listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)"));
        checkSearch(inv, "(e, e,e",
                List.of("listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)"));
        checkSearch(inv, "(e, e, e",
                List.of("listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)"));
        checkSearch(inv, "(e,   e,  e",
                List.of("listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                "listpkg.List.of(E, E, E, E, E)"));
        checkSearch(inv, "(e, e, e ,",
                List.of("listpkg.List.of(E, E, E, E)", "listpkg.List.of(E, E, E, E, E)"));
        checkSearch(inv, "(e   ,   e,  e,",
                List.of("listpkg.List.of(E, E, E, E)", "listpkg.List.of(E, E, E, E, E)"));
        checkSearch(inv, "  listpkg  .list .of ",
                List.of("listpkg.List.of()", "listpkg.List.of(E)", "listpkg.List.of(E, E)",
                        "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)", "listpkg.List.of(E...)"));
        checkSearch(inv, " l. l. o",
                List.of("listpkg.List.of()", "listpkg.List.of(E)", "listpkg.List.of(E, E)",
                        "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)", "listpkg.List.of(E...)"));
        checkSearch(inv, "list . of",
                List.of("listpkg.List.of()", "listpkg.List.of(E)", "listpkg.List.of(E, E)",
                        "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                        "listpkg.List.of(E, E, E, E, E)", "listpkg.List.of(E...)"));
        checkSearch(inv, "lis t.of", List.of());
        checkSearch(inv, "list . of(e,e,e,",
                List.of("listpkg.List.of(E, E, E, E)", "listpkg.List.of(E, E, E, E, E)"));
        checkSearch(inv, "l . o (e,e,e,",
                List.of("listpkg.List.of(E, E, E, E)", "listpkg.List.of(E, E, E, E, E)"));
        checkSearch(inv, "search    \tt", List.of("other search tag"));
        checkSearch(inv, "sear ch", List.of());
        checkSearch(inv, "( e ..", List.of("listpkg.List.of(E...)"));
        checkSearch(inv, "( i [ ]", List.of("listpkg.Nolist.withArrayArg(int[])"));

        // empty/white space search should not trigger results
        checkNullSearch(inv, "");
        checkNullSearch(inv, " ");
        checkNullSearch(inv, "    ");
        checkNullSearch(inv, " \t\t ");


        // _ word boundaries and case sensitivity
        checkSearch(inv, "some", List.of("listpkg.Nolist.SOME_INT_CONSTANT"));
        checkSearch(inv, "SOME", List.of("listpkg.Nolist.SOME_INT_CONSTANT"));
        checkSearch(inv, "Some", List.of("listpkg.Nolist.SOME_INT_CONSTANT"));
        checkSearch(inv, "int", List.of("All Classes and Interfaces", "listpkg.Nolist.SOME_INT_CONSTANT"));
        checkSearch(inv, "INT", List.of("All Classes and Interfaces", "listpkg.Nolist.SOME_INT_CONSTANT"));
        checkSearch(inv, "Int", List.of("All Classes and Interfaces", "listpkg.Nolist.SOME_INT_CONSTANT"));
        checkSearch(inv, "int_con", List.of("listpkg.Nolist.SOME_INT_CONSTANT"));
        checkSearch(inv, "INT_CON", List.of("listpkg.Nolist.SOME_INT_CONSTANT"));
        checkSearch(inv, "NT", List.of());
        checkSearch(inv, "NT_", List.of());
        checkSearch(inv, "_const", List.of("listpkg.Nolist.SOME_INT_CONSTANT"));
        checkSearch(inv, "_CONST", List.of("listpkg.Nolist.SOME_INT_CONSTANT"));

        // Test for all packages, all classes links
        checkSearch(inv, "all", List.of("All Packages", "All Classes and Interfaces"));
        checkSearch(inv, "All", List.of("All Packages", "All Classes and Interfaces"));
        checkSearch(inv, "ALL", List.of("All Packages", "All Classes and Interfaces"));

        // test for generic types, var-arg and array args
        checkSearch(inv, "(map<string, ? ext collection>)",
                List.of("listpkg.Nolist.withTypeParams(Map<String, ? extends Collection>)"));
        checkSearch(inv, "(m<str,? ext coll>",
                List.of("listpkg.Nolist.withTypeParams(Map<String, ? extends Collection>)"));
        checkSearch(inv, "(object...", List.of("listpkg.Nolist.withVarArgs(Object...)"));
        checkSearch(inv, "(obj...", List.of("listpkg.Nolist.withVarArgs(Object...)"));
        checkSearch(inv, "(e..", List.of("listpkg.List.of(E...)"));
        checkSearch(inv, "(int[]", List.of("listpkg.Nolist.withArrayArg(int[])"));
        checkSearch(inv, "(i[]", List.of("listpkg.Nolist.withArrayArg(int[])"));

        // multiple space separated tokens
        checkSearch(inv, "list of", List.of("listpkg.List.of()", "listpkg.List.of(E)",
                "listpkg.List.of(E, E)", "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                "listpkg.List.of(E, E, E, E, E)", "listpkg.List.of(E...)"));
        checkSearch(inv, "list of e", List.of("listpkg.List.of(E)", "listpkg.List.of(E, E)",
                "listpkg.List.of(E, E, E)", "listpkg.List.of(E, E, E, E)",
                "listpkg.List.of(E, E, E, E, E)", "listpkg.List.of(E...)"));
        checkSearch(inv, "list of e e e", List.of("listpkg.List.of(E, E, E)",
                "listpkg.List.of(E, E, E, E)", "listpkg.List.of(E, E, E, E, E)"));
        checkSearch(inv, "list of e..", List.of("listpkg.List.of(E...)"));

        checkSearch(inv, "list with", List.of());
        checkSearch(inv, "nolist with", List.of("listpkg.Nolist.withVarArgs(Object...)",
                "listpkg.Nolist.withArrayArg(int[])",
                "listpkg.Nolist.withTypeParams(Map<String, ? extends Collection>)"));
        checkSearch(inv, "with arg", List.of("listpkg.Nolist.withVarArgs(Object...)",
                "listpkg.Nolist.withArrayArg(int[])"));
        checkSearch(inv, "with int", List.of("listpkg.Nolist.withArrayArg(int[])"));
        checkSearch(inv, "with map", List.of(
                "listpkg.Nolist.withTypeParams(Map<String, ? extends Collection>)"));

        // search for numeric strings
        checkSearch(inv, "1", List.of("listpkg.MyList.abc123xyz()"));
        checkSearch(inv, "12", List.of("listpkg.MyList.abc123xyz()"));
        checkSearch(inv, "12 x", List.of("listpkg.MyList.abc123xyz()"));
        checkSearch(inv, "123 x", List.of("listpkg.MyList.abc123xyz()"));
        checkSearch(inv, "1 x", List.of("listpkg.MyList.abc123xyz()"));
        checkSearch(inv, "2 x", List.of());
        checkSearch(inv, "3", List.of("listpkg.MyList.M_3X"));
        checkSearch(inv, "3x", List.of("listpkg.MyList.M_3X"));
        checkSearch(inv, "_3", List.of("listpkg.MyList.M_3X"));
        checkSearch(inv, "3 x", List.of("listpkg.MyList.M_3X"));

        // Unicode camel-case tests
        checkSearch(inv, "νέα λίστα", List.of("listpkg.ListProvider.δημιουργήστεΝέαΛίστα()"));
        checkSearch(inv, "δημ νέα λίσ", List.of("listpkg.ListProvider.δημιουργήστεΝέαΛίστα()"));
        checkSearch(inv, "δ ν λ", List.of("listpkg.ListProvider.δημιουργήστεΝέαΛίστα()"));
        checkSearch(inv, "ν λ", List.of("listpkg.ListProvider.δημιουργήστεΝέαΛίστα()"));
        checkSearch(inv, "δημιουργήστεΝέαΛίστα", List.of("listpkg.ListProvider.δημιουργήστεΝέαΛίστα()"));
        checkSearch(inv, "δηΝέΛίσ", List.of("listpkg.ListProvider.δημιουργήστεΝέαΛίστα()"));
        checkSearch(inv, "δΝΛ", List.of("listpkg.ListProvider.δημιουργήστεΝέαΛίστα()"));
        checkSearch(inv, "ΝΛ", List.of("listpkg.ListProvider.δημιουργήστεΝέαΛίστα()"));
        checkSearch(inv, "δημ λίστα", List.of("listpkg.ListProvider.δημιουργήστεΝέαΛίστα()"));
        checkSearch(inv, "сделать новый список", List.of("listpkg.ListProvider.сделатьНовыйСписок()"));
        checkSearch(inv, "сде нов спи", List.of("listpkg.ListProvider.сделатьНовыйСписок()"));
        checkSearch(inv, "с н с", List.of("listpkg.ListProvider.сделатьНовыйСписок()"));
        checkSearch(inv, "н с", List.of("listpkg.ListProvider.сделатьНовыйСписок()"));
        checkSearch(inv, "сделатьНовыйСписок", List.of("listpkg.ListProvider.сделатьНовыйСписок()"));
        checkSearch(inv, "сдеНовСпис", List.of("listpkg.ListProvider.сделатьНовыйСписок()"));
        checkSearch(inv, "сНС", List.of("listpkg.ListProvider.сделатьНовыйСписок()"));
        checkSearch(inv, "сН", List.of("listpkg.ListProvider.сделатьНовыйСписок()"));
        checkSearch(inv, "сдеН Спи", List.of("listpkg.ListProvider.сделатьНовыйСписок()"));

        // Negative Unicode camel-case tests
        checkSearch(inv, "Νέα ίστα", List.of());
        checkSearch(inv, "α λίστα", List.of());
        checkSearch(inv, "ηΝΛ", List.of());
        checkSearch(inv, "овый", List.of());
        checkSearch(inv, "д н с", List.of());
        checkSearch(inv, "пи", List.of());
        checkSearch(inv, "НОВЫЙС ПИСОК", List.of());
    }

    @Test
    public void testChannelSearch() throws ScriptException, IOException, NoSuchMethodException {
        javadoc("-d", "out-channel",
                "-Xdoclint:none",
                "-use",
                "-sourcepath", testSrc,
                "channels");
        checkExit(Exit.OK);

        Invocable inv = getEngine();

        checkSearch(inv, "FileChannel", List.of("channels.FileChannel", "channels.FileChannel.Map",
                "channels.FileChannel.FileChannel()"));
        checkSearch(inv, "FileChannel.", List.of("channels.FileChannel.Map",
                "channels.FileChannel.FileChannel()", "channels.FileChannel.map(FileChannel.Map, int)"));
        checkSearch(inv, "filechannel.M", List.of("channels.FileChannel.Map",
                "channels.FileChannel.map(FileChannel.Map, int)"));
        checkSearch(inv, "FileChannel.map", List.of("channels.FileChannel.Map",
                "channels.FileChannel.map(FileChannel.Map, int)"));
        checkSearch(inv, "FileChannel.map(", List.of("channels.FileChannel.map(FileChannel.Map, int)"));
    }

    void checkSearch(Invocable inv, String query, List<String> results) throws ScriptException, NoSuchMethodException {
        checkList(query, (List<?>) inv.invokeFunction("search", query), results);
    }

    void checkList(String query, List<?> result, List<?> expected) {
        checking("Checking result for query \"" + query + "\"");
        if (!expected.equals(result)) {
            failed("Expected: " + expected + ", got: " + result);
        } else {
            passed("List matches expected result");
        }
    }

    void checkNullSearch(Invocable inv, String query) throws ScriptException, NoSuchMethodException {
        Object result = inv.invokeFunction("search", query);
        checking("Checking result for query \"" + query + "\"");
        if (result == null) {
            passed("Result is null as expected");
        } else {
            failed("Expected: null, got: " + result);
        }
    }
}
