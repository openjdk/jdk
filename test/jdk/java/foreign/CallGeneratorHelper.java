/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

import java.io.IOException;
import java.io.PrintStream;
import java.lang.foreign.*;

import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jdk.internal.foreign.Utils;
import org.testng.annotations.*;

public class CallGeneratorHelper extends NativeTestHelper {

    static final List<MemoryLayout> STACK_PREFIX_LAYOUTS = Stream.concat(
            Stream.generate(() -> (MemoryLayout) C_LONG_LONG).limit(8),
            Stream.generate(() -> (MemoryLayout)  C_DOUBLE).limit(8)
        ).toList();

    static SegmentAllocator THROWING_ALLOCATOR = (size, align) -> {
        throw new UnsupportedOperationException();
    };

    static final int SAMPLE_FACTOR = Integer.parseInt((String)System.getProperties().getOrDefault("generator.sample.factor", "-1"));

    static final int MAX_FIELDS = 3;
    static final int MAX_PARAMS = 3;
    static final int CHUNK_SIZE = 600;

    enum Ret {
        VOID,
        NON_VOID
    }

    enum StructFieldType {
        INT("int", C_INT),
        FLOAT("float", C_FLOAT),
        DOUBLE("double", C_DOUBLE),
        POINTER("void*", C_POINTER);

        final String typeStr;
        final MemoryLayout layout;

        StructFieldType(String typeStr, MemoryLayout layout) {
            this.typeStr = typeStr;
            this.layout = layout;
        }

        MemoryLayout layout() {
            return layout;
        }

        @SuppressWarnings("unchecked")
        static List<List<StructFieldType>>[] perms = new List[10];

        static List<List<StructFieldType>> perms(int i) {
            if (perms[i] == null) {
                perms[i] = generateTest(i, values());
            }
            return perms[i];
        }
    }

    enum ParamType {
        INT("int", C_INT),
        FLOAT("float", C_FLOAT),
        DOUBLE("double", C_DOUBLE),
        POINTER("void*", C_POINTER),
        STRUCT("struct S", null);

        private final String typeStr;
        private final MemoryLayout layout;

        ParamType(String typeStr, MemoryLayout layout) {
            this.typeStr = typeStr;
            this.layout = layout;
        }

        String type(List<StructFieldType> fields) {
            return this == STRUCT ?
                    typeStr + "_" + sigCode(fields) :
                    typeStr;
        }

        MemoryLayout layout(List<StructFieldType> fields) {
            if (this == STRUCT) {
                return Utils.computePaddedStructLayout(
                        IntStream.range(0, fields.size())
                            .mapToObj(i -> fields.get(i).layout().withName("f" + i))
                            .toArray(MemoryLayout[]::new));
            } else {
                return layout;
            }
        }

        @SuppressWarnings("unchecked")
        static List<List<ParamType>>[] perms = new List[10];

        static List<List<ParamType>> perms(int i) {
            if (perms[i] == null) {
                perms[i] = generateTest(i, values());
            }
            return perms[i];
        }
    }

    static <Z> List<List<Z>> generateTest(int i, Z[] elems) {
        List<List<Z>> res = new ArrayList<>();
        generateTest(i, new Stack<>(), elems, res);
        return res;
    }

    static <Z> void generateTest(int i, Stack<Z> combo, Z[] elems, List<List<Z>> results) {
        if (i == 0) {
            results.add(new ArrayList<>(combo));
        } else {
            for (Z z : elems) {
                combo.push(z);
                generateTest(i - 1, combo, elems, results);
                combo.pop();
            }
        }
    }

    @DataProvider(name = "functions")
    public static Object[][] functions() {
        int functions = 0;
        List<Object[]> downcalls = new ArrayList<>();
        for (Ret r : Ret.values()) {
            for (int i = 0; i <= MAX_PARAMS; i++) {
                if (r != Ret.VOID && i == 0) continue;
                for (List<ParamType> ptypes : ParamType.perms(i)) {
                    String retCode = r == Ret.VOID ? "V" : ptypes.get(0).name().charAt(0) + "";
                    String sigCode = sigCode(ptypes);
                    if (ptypes.contains(ParamType.STRUCT)) {
                        for (int j = 1; j <= MAX_FIELDS; j++) {
                            for (List<StructFieldType> fields : StructFieldType.perms(j)) {
                                String structCode = sigCode(fields);
                                int count = functions;
                                int fCode = functions++ / CHUNK_SIZE;
                                String fName = String.format("f%d_%s_%s_%s", fCode, retCode, sigCode, structCode);
                                if (SAMPLE_FACTOR == -1 || (count % SAMPLE_FACTOR) == 0) {
                                    downcalls.add(new Object[]{count, fName, r, ptypes, fields});
                                }
                            }
                        }
                    } else {
                        String structCode = sigCode(List.<StructFieldType>of());
                        int count = functions;
                        int fCode = functions++ / CHUNK_SIZE;
                        String fName = String.format("f%d_%s_%s_%s", fCode, retCode, sigCode, structCode);
                        if (SAMPLE_FACTOR == -1 || (count % SAMPLE_FACTOR) == 0) {
                            downcalls.add(new Object[]{count, fName, r, ptypes, List.of()});
                        }
                    }
                }
            }
        }
        return downcalls.toArray(new Object[0][]);
    }

    static <Z extends Enum<Z>> String sigCode(List<Z> elems) {
        return elems.stream().map(p -> p.name().charAt(0) + "").collect(Collectors.joining());
    }

    private static void generateStructDecl(PrintStream out, List<StructFieldType> fields) {
        String structCode = sigCode(fields);
        List<String> fieldDecls = new ArrayList<>();
        for (int i = 0 ; i < fields.size() ; i++) {
            fieldDecls.add(String.format("%s p%d;", fields.get(i).typeStr, i));
        }
        String res = String.format("struct S_%s { %s };", structCode,
                fieldDecls.stream().collect(Collectors.joining(" ")));
        out.println(res);
    }

    private static PrintStream printStream(String first) throws IOException {
        return new PrintStream(Files.newOutputStream(Path.of(first)));
    }

    // This can be used to generate the test implementation.
    // From the test/jdk/java/foreign directory, run this class using:
    // java -cp <jtreg_home>\lib\testng-7.3.0.jar --add-exports java.base/jdk.internal.foreign=ALL-UNNAMED ./CallGeneratorHelper.java
    // Copyright header has to be added manually, and on Windows line endings have to be changed from \r\n to just \n
    public static void main(String[] args) throws IOException {
        try (PrintStream shared = printStream("shared.h");
                 PrintStream libTestDowncall = printStream("libTestDowncall.c");
                 PrintStream libTestDowncallStack = printStream("libTestDowncallStack.c");
                 PrintStream libTestUpcall = printStream("libTestUpcall.c");
                 PrintStream libTestUpcallStack = printStream("libTestUpcallStack.c")) {
            generateShared(shared);
            generateDowncalls(libTestDowncall, false);
            generateDowncalls(libTestDowncallStack, true);
            generateUpcalls(libTestUpcall, false);
            generateUpcalls(libTestUpcallStack, true);
        }
    }

    private static void generateShared(PrintStream out) {
        out.println("""
            #include "export.h"

            #ifdef __clang__
            #pragma clang optimize off
            #elif defined __GNUC__
            #pragma GCC optimize ("O0")
            #elif defined _MSC_BUILD
            #pragma optimize( "", off )
            #endif

            #ifdef _AIX
            #pragma align (natural)
            #endif
             """);

        for (int j = 1; j <= MAX_FIELDS; j++) {
            for (List<StructFieldType> fields : StructFieldType.perms(j)) {
                generateStructDecl(out, fields);
            }
        }

        out.print("""

            #ifdef _AIX
            #pragma align (reset)
            #endif
            """);
    }

    private static void generateDowncalls(PrintStream out, boolean stack) {
        out.println("#include \"shared.h\"\n");

        for (Object[] downcall : functions()) {
            String fName = (String)downcall[1];
            Ret r = (Ret)downcall[2];
            @SuppressWarnings("unchecked")
            List<ParamType> ptypes = (List<ParamType>)downcall[3];
            @SuppressWarnings("unchecked")
            List<StructFieldType> fields = (List<StructFieldType>)downcall[4];
            generateDowncallFunction(out, fName, r, ptypes, fields, stack);
        }
    }

    private static final List<String> stackParamTypes = Stream.concat(Stream.generate(() -> "long long").limit(8),
                Stream.generate(() -> "double").limit(8)).toList();
    private static final List<String> stackParamNames = IntStream.range(0, 16).mapToObj(i -> "pf" + i).toList();
    private static final List<String> stackParamDecls = IntStream.range(0, 16)
            .mapToObj(i -> stackParamTypes.get(i) + " " + stackParamNames.get(i)).toList();

    private static void generateDowncallFunction(PrintStream out, String fName, Ret ret, List<ParamType> params, List<StructFieldType> fields, boolean stack) {
        String retType = ret == Ret.VOID ? "void" : params.get(0).type(fields);
        List<String> paramDecls = new ArrayList<>();
        if (stack) {
            paramDecls.addAll(stackParamDecls);
        }
        for (int i = 0 ; i < params.size() ; i++) {
            paramDecls.add(String.format("%s p%d", params.get(i).type(fields), i));
        }
        String sig = paramDecls.isEmpty() ?
                "void" :
                paramDecls.stream().collect(Collectors.joining(", "));
        String body = ret == Ret.VOID ? "{ }" : "{ return p0; }";
        String res = String.format("EXPORT %s %s%s(%s) %s", retType, stack ? "s" : "", fName,
                sig, body);
        out.println(res);
    }

    private static void generateUpcalls(PrintStream out, boolean stack) {
        out.println("#include \"shared.h\"\n");

        for (Object[] downcall : functions()) {
            String fName = (String)downcall[1];
            Ret r = (Ret)downcall[2];
            @SuppressWarnings("unchecked")
            List<ParamType> ptypes = (List<ParamType>)downcall[3];
            @SuppressWarnings("unchecked")
            List<StructFieldType> fields = (List<StructFieldType>)downcall[4];
            generateUpcallFunction(out, fName, r, ptypes, fields, stack);
        }
    }

    private static void generateUpcallFunction(PrintStream out, String fName, Ret ret, List<ParamType> params, List<StructFieldType> fields, boolean stack) {
        String retType = ret == Ret.VOID ? "void" : params.get(0).type(fields);
        List<String> paramDecls = new ArrayList<>();
        if (stack) {
            paramDecls.addAll(stackParamDecls);
        }
        for (int i = 0 ; i < params.size() ; i++) {
            paramDecls.add(String.format("%s p%d", params.get(i).type(fields), i));
        }
        Stream<String> prefixParamNames = stack ? stackParamNames.stream() : Stream.of();
        String paramNames = Stream.concat(prefixParamNames, IntStream.range(0, params.size())
                .mapToObj(i -> "p" + i))
                .collect(Collectors.joining(", "));
        String sig = paramDecls.isEmpty() ?
                "" :
                paramDecls.stream().collect(Collectors.joining(", ")) + ", ";
        String body = String.format(ret == Ret.VOID ? "{ cb(%s); }" : "{ return cb(%s); }", paramNames);
        List<String> paramTypes = params.stream().map(p -> p.type(fields)).collect(Collectors.toList());
        if (stack) {
            paramTypes.addAll(0, stackParamTypes);
        }
        String cbSig = paramTypes.isEmpty() ?
                "void" :
                paramTypes.stream().collect(Collectors.joining(", "));
        String cbParam = String.format("%s (*cb)(%s)",
                retType, cbSig);

        String res = String.format("EXPORT %s %s%s(%s %s) %s", retType, stack ? "s" : "", fName,
                sig, cbParam, body);
        out.println(res);
    }

    //helper methods

    MethodHandle downcallHandle(Linker abi, MemorySegment symbol, SegmentAllocator allocator, FunctionDescriptor descriptor) {
        MethodHandle mh = abi.downcallHandle(symbol, descriptor);
        if (descriptor.returnLayout().isPresent() && descriptor.returnLayout().get() instanceof GroupLayout) {
            mh = mh.bindTo(allocator);
        }
        return mh;
    }
}
