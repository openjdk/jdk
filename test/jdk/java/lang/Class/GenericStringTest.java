/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6298888 6992705 8161500 6304578 8322878
 * @summary Check Class.toGenericString()
 */

import java.lang.reflect.*;
import java.lang.annotation.*;
import java.util.*;

@ExpectedGenericString("public class GenericStringTest")
public class GenericStringTest {
    public Map<String, Integer>[] mixed = null;
    public Map<String, Integer>[][] mixed2 = null;

    private static record PlatformTestCase(Class<?> clazz, String expected) {}

    public static void main(String... args) throws ReflectiveOperationException {
        int failures = 0;

        String[][] nested = {{""}};
        int[][]    intArray = {{1}};

       List<PlatformTestCase> platformTestCases =
           List.of(new PlatformTestCase(int.class,           "int"),
                   new PlatformTestCase(void.class,          "void"),
                   new PlatformTestCase(args.getClass(),     "java.lang.String[]"),
                   new PlatformTestCase(nested.getClass(),   "java.lang.String[][]"),
                   new PlatformTestCase(intArray.getClass(), "int[][]"),

                   new PlatformTestCase(java.lang.Enum.class,
                                        "public abstract class java.lang.Enum<E extends java.lang.Enum<E>>"),
                   new PlatformTestCase(java.util.Map.class,
                                        "public abstract interface java.util.Map<K,V>"),
                   new PlatformTestCase(java.util.EnumMap.class,
                                        "public class java.util.EnumMap<K extends java.lang.Enum<K>,V>"),
                   new PlatformTestCase(java.util.EventListenerProxy.class,
                                        "public abstract class java.util.EventListenerProxy<T extends java.util.EventListener>"),

                   // Sealed class
                   new PlatformTestCase(java.lang.ref.Reference.class,
                                     "public abstract sealed class java.lang.ref.Reference<T>"),
                   // non-sealed class
                   new PlatformTestCase(java.lang.ref.WeakReference.class,
                                     "public non-sealed class java.lang.ref.WeakReference<T>")
                   );

        for (PlatformTestCase platformTestCase : platformTestCases) {
            failures += checkToGenericString(platformTestCase.clazz,
                                             platformTestCase.expected);
        }

        Field f = GenericStringTest.class.getDeclaredField("mixed");
        // The expected value includes "<K,V>" rather than
        // "<...String,...Integer>" since the Class object rather than
        // Type objects is being queried.
        failures += checkToGenericString(f.getType(), "java.util.Map<K,V>[]");
        f = GenericStringTest.class.getDeclaredField("mixed2");
        failures += checkToGenericString(f.getType(), "java.util.Map<K,V>[][]");

        for(Class<?> clazz : List.of(GenericStringTest.class,
                                     AnInterface.class,
                                     LocalMap.class,
                                     AnEnum.class,
                                     AnotherEnum.class,

                                     SealedRootClass.class,
                                     SealedRootClass.ChildA.class,
                                     SealedRootClass.ChildB.class,
                                     SealedRootClass.ChildB.GrandChildAB.class,
                                     SealedRootClass.ChildC.class,
                                     SealedRootClass.ChildC.GrandChildACA.class,
                                     SealedRootClass.ChildC.GrandChildACB.class,
                                     SealedRootClass.ChildC.GrandChildACC.class,
                                     SealedRootClass.ChildC.GrandChildACC.GreatGrandChildACCA.class,
                                     SealedRootClass.ChildC.GrandChildACC.GreatGrandChildACCB.class,

                                     SealedRootIntf.class,
                                     SealedRootIntf.ChildA.class,
                                     SealedRootIntf.ChildB.class,
                                     SealedRootIntf.ChildB.GrandChildAB.class,
                                     SealedRootIntf.ChildC.class,
                                     SealedRootIntf.ChildC.GrandChildACA.class,
                                     SealedRootIntf.ChildC.GrandChildACB.class,
                                     SealedRootIntf.ChildC.GrandChildACC.class,
                                     SealedRootIntf.ChildC.GrandChildACC.GreatGrandChildACCA.class,
                                     SealedRootIntf.ChildC.GrandChildACC.GreatGrandChildACCB.class,
                                     SealedRootIntf.IntfA.class,
                                     SealedRootIntf.IntfA.IntfAImpl.class,
                                     SealedRootIntf.IntfB.class,
                                     SealedRootIntf.IntfB.IntfAImpl.class)) {
            failures += checkToGenericString(clazz, clazz.getAnnotation(ExpectedGenericString.class).value());
        }

        if (failures > 0) {
            throw new RuntimeException();
        }
    }

    private static int checkToGenericString(Class<?> clazz, String expected) {
        String genericString = clazz.toGenericString();
        if (!genericString.equals(expected)) {
            System.err.printf("Unexpected Class.toGenericString output; expected %n\t'%s',%n got %n\t'%s'.%n",
                              expected,
                              genericString);
            return 1;
        } else
            return 0;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface ExpectedGenericString {
    String value();
}

@ExpectedGenericString("abstract interface AnInterface")
strictfp interface AnInterface {}

@ExpectedGenericString("abstract interface LocalMap<K,V>")
interface LocalMap<K,V> {}

@ExpectedGenericString("final enum AnEnum")
enum AnEnum {
    FOO;
}

// If an enum class has a specialized enum constant, that is compiled
// by having the enum class as being sealed rather than final. See JLS
// 8.9 Enum Classes.
@ExpectedGenericString("sealed enum AnotherEnum")
enum AnotherEnum {
    BAR{};
}

// Test cases for sealed/non-sealed _class_ hierarchy.
@ExpectedGenericString("sealed class SealedRootClass")
sealed class SealedRootClass
    permits
    SealedRootClass.ChildA,
    SealedRootClass.ChildB,
    SealedRootClass.ChildC {

    @ExpectedGenericString("final class SealedRootClass$ChildA")
    final class ChildA extends SealedRootClass {}

    @ExpectedGenericString("sealed class SealedRootClass$ChildB")
    sealed class ChildB extends SealedRootClass permits SealedRootClass.ChildB.GrandChildAB {
        @ExpectedGenericString("final class SealedRootClass$ChildB$GrandChildAB")
        final class GrandChildAB extends ChildB {}
    }

    @ExpectedGenericString("non-sealed class SealedRootClass$ChildC")
    non-sealed class ChildC extends SealedRootClass {
        // The subclasses of ChildC do not themselves have to be
        // sealed, non-sealed, or final.
        @ExpectedGenericString("class SealedRootClass$ChildC$GrandChildACA")
        class GrandChildACA extends ChildC {}

        @ExpectedGenericString("final class SealedRootClass$ChildC$GrandChildACB")
        final class GrandChildACB extends ChildC {}

        @ExpectedGenericString("sealed class SealedRootClass$ChildC$GrandChildACC")
        sealed class GrandChildACC extends ChildC {
            @ExpectedGenericString("final class SealedRootClass$ChildC$GrandChildACC$GreatGrandChildACCA")
            final class GreatGrandChildACCA extends GrandChildACC {}

            @ExpectedGenericString("non-sealed class SealedRootClass$ChildC$GrandChildACC$GreatGrandChildACCB")
            non-sealed class GreatGrandChildACCB extends GrandChildACC {}
        }
    }
}

// Test cases for sealed/non-sealed _interface_ hierarchy.
@ExpectedGenericString("abstract sealed interface SealedRootIntf")
sealed interface SealedRootIntf
    permits
    SealedRootIntf.ChildA,
    SealedRootIntf.ChildB,
    SealedRootIntf.ChildC,

    SealedRootIntf.IntfA,
    SealedRootIntf.IntfB {

    @ExpectedGenericString("public static final class SealedRootIntf$ChildA")
    final class ChildA implements SealedRootIntf {}

    @ExpectedGenericString("public static sealed class SealedRootIntf$ChildB")
    sealed class ChildB implements SealedRootIntf permits SealedRootIntf.ChildB.GrandChildAB {
        @ExpectedGenericString("final class SealedRootIntf$ChildB$GrandChildAB")
        final class GrandChildAB extends ChildB {}
    }

    @ExpectedGenericString("public static non-sealed class SealedRootIntf$ChildC")
    non-sealed class ChildC implements SealedRootIntf {
        // The subclasses of ChildC do not themselves have to be
        // sealed, non-sealed, or final.
        @ExpectedGenericString("class SealedRootIntf$ChildC$GrandChildACA")
        class GrandChildACA extends ChildC {}

        @ExpectedGenericString("final class SealedRootIntf$ChildC$GrandChildACB")
        final class GrandChildACB extends ChildC {}

        @ExpectedGenericString("sealed class SealedRootIntf$ChildC$GrandChildACC")
        sealed class GrandChildACC extends ChildC {
            @ExpectedGenericString("final class SealedRootIntf$ChildC$GrandChildACC$GreatGrandChildACCA")
            final class GreatGrandChildACCA extends GrandChildACC {}

            @ExpectedGenericString("non-sealed class SealedRootIntf$ChildC$GrandChildACC$GreatGrandChildACCB")
            non-sealed class GreatGrandChildACCB extends GrandChildACC {}
        }
    }

    @ExpectedGenericString("public abstract static sealed interface SealedRootIntf$IntfA")
    sealed interface IntfA extends  SealedRootIntf {
        @ExpectedGenericString("public static non-sealed class SealedRootIntf$IntfA$IntfAImpl")
        non-sealed class IntfAImpl implements IntfA {}
    }

    @ExpectedGenericString("public abstract static non-sealed interface SealedRootIntf$IntfB")
    non-sealed interface IntfB extends  SealedRootIntf {
        // Check that non-sealing can be allowed with a second superinterface being sealed.
        @ExpectedGenericString("public static non-sealed class SealedRootIntf$IntfB$IntfAImpl")
        non-sealed class IntfAImpl implements IntfB, IntfA  {}
    }
}
