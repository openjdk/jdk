/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.Asserts;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeKlass;

import static java.util.function.Predicate.not;

/**
 * Class used for parsing included classes file and excluded methods file
 */
public class TypesParser {

    private List<MethodTemplate> methodsToExclude;

    private static final HashMap<Class<?>, Type> TYPE_CACHE = new HashMap<>();

    private static String trimComment(String source) {
        int commentStart = source.indexOf('#');
        return commentStart == -1 ? source : source.substring(0, commentStart);
    }

    /**
     * Parses included classes file and excluded methods file to TypeList and SymbolTable.
     * This routine takes all classes named in the classes file and puts them to the TypeList,
     * it also puts all the methods of the classes to the SymbolTable.
     * Excluded methods file is used to remove methods from the SymbolTable.
     * @param klassesFileName - name of the included classes file
     * @param exMethodsFileName - name of the excluded method file
     */
    public static void parseTypesAndMethods(String klassesFileName, String exMethodsFileName) {
        Asserts.assertNotNull(klassesFileName, "Classes input file name is null");
        Asserts.assertFalse(klassesFileName.isEmpty(), "Classes input file name is empty");
        TypesParser theParser = new TypesParser();
        theParser.initMethodsToExclude(exMethodsFileName);
        parseKlasses(klassesFileName)
            .stream()
            .filter(klass -> !TypeList.isReferenceType(getTypeKlass(klass)))
            .forEach(theParser::processKlass);
    }

    private void processKlass(Class<?> klass) {
        TypeKlass typeKlass = getTypeKlass(klass);
        TypeList.add(typeKlass);
        Stream.concat(Arrays.stream(klass.getMethods()), Arrays.stream(klass.getConstructors()))
            .filter(not(Executable::isSynthetic))
            .filter(method -> MethodTemplate.noneMatches(methodsToExclude, method))
            .forEach(method -> {
                String name = method.getName();
                boolean isConstructor = false;
                Type returnType;
                if (name.equals(klass.getName())) {
                    isConstructor = true;
                    returnType = typeKlass;
                } else {
                    returnType = getType(((Method) method).getReturnType());
                }
                ArrayList<VariableInfo> paramList = new ArrayList<>();
                int flags = getMethodFlags(method);
                if (!isConstructor && ((flags & FunctionInfo.STATIC) == 0)) {
                    paramList.add(new VariableInfo("this", typeKlass, typeKlass,
                            VariableInfo.LOCAL | VariableInfo.INITIALIZED));
                }
                Class<?>[] paramKlasses = method.getParameterTypes();
                int argNum = 0;
                for (Class<?> paramKlass : paramKlasses) {
                    argNum++;
                    Type paramType = getType(paramKlass);
                    paramList.add(new VariableInfo("arg" + argNum, typeKlass, paramType,
                            VariableInfo.LOCAL | VariableInfo.INITIALIZED));
                }
                typeKlass.addSymbol(new FunctionInfo(name, typeKlass, returnType, 1, flags, paramList));
            });
    }

    private static Type getType(Class<?> klass) {
        Type type = TYPE_CACHE.get(klass);
        if (type != null) {
            return type;
        }
        if (klass.isPrimitive()) {
            if (klass.equals(void.class)) {
                type = TypeList.VOID;
            } else {
                type = TypeList.find(klass.getName());
            }
        } else {
            int flags = getKlassFlags(klass);
            if (klass.isArray()) {
                Class<?> elementKlass  = klass.getComponentType();
                while (elementKlass.isArray()) {
                    elementKlass = elementKlass.getComponentType();
                }
                Type elementType = getType(elementKlass);
                int dim = getArrayClassDimension(klass);
                type = new TypeArray(elementType, dim);
            } else {
                String canonicalName = klass.getCanonicalName();
                if (!"java.lang.Object".equals(canonicalName)) {
                    flags |= TypeKlass.FINAL;
                }
                type = new TypeKlass(canonicalName, flags);
            }
            Class<?> parentKlass = klass.getSuperclass();
            TypeKlass typeKlass = (TypeKlass) type;
            if (parentKlass != null) {
                TypeKlass parentTypeKlass = (TypeKlass) getType(parentKlass);
                typeKlass.addParent(parentTypeKlass.getName());
                typeKlass.setParent(parentTypeKlass);
            }
            for (Class<?> iface : klass.getInterfaces()) {
                typeKlass.addParent(getType(iface).getName());
            }
        }
        TYPE_CACHE.put(klass, type);
        return type;
    }

    private static TypeKlass getTypeKlass(Class<?> klass) {
        return (TypeKlass) getType(klass);
    }

    private static int getArrayClassDimension(Class<?> klass) {
        if (!klass.isArray()) {
            return 0;
        }
        String name = klass.getName();
        int begin = name.indexOf('[');
        name = name.substring(begin, name.length());
        return name.length() / 2;
    }

    private static int getKlassFlags(Class<?> klass) {
        int flags = TypeKlass.NONE;
        if (klass.isInterface()) {
            flags = flags | TypeKlass.INTERFACE;
        } else if ((klass.getModifiers() & Modifier.ABSTRACT) != 0) {
            flags = flags | TypeKlass.ABSTRACT;
        } else if ((klass.getModifiers() & Modifier.FINAL) != 0) {
            flags = flags | TypeKlass.FINAL;
        }
        return flags;
    }

    private static int getMethodFlags(Executable method) {
        int flags = FunctionInfo.NONE;
        int modifiers = method.getModifiers();
        if (Modifier.isAbstract(modifiers)) {
            flags |= FunctionInfo.ABSTRACT;
        }
        if (Modifier.isFinal(modifiers)) {
            flags |= FunctionInfo.FINAL;
        }
        if (Modifier.isPublic(modifiers)) {
            flags |= FunctionInfo.PUBLIC;
        } else if (Modifier.isProtected(modifiers)) {
            flags |= FunctionInfo.PROTECTED;
        } else if (Modifier.isPrivate(modifiers)) {
            flags |= FunctionInfo.PRIVATE;
        } else {
            flags |= FunctionInfo.DEFAULT;
        }
        if (Modifier.isStatic(modifiers)) {
            flags |= FunctionInfo.STATIC;
        }
        if (Modifier.isSynchronized(modifiers)) {
            flags |= FunctionInfo.SYNCHRONIZED;
        }
        return flags;
    }

    private static List<Class<?>> parseKlasses(String klassesFileName) {
        Asserts.assertNotNull(klassesFileName, "Classes input file name is null");
        Asserts.assertFalse(klassesFileName.isEmpty(), "Classes input file name is empty");
        List<String> klassNamesList = new ArrayList<>();
        Path klassesFilePath = Paths.get(klassesFileName);
        try {
            Files.lines(klassesFilePath).forEach(line -> {
                line = line.trim();
                if (line.isEmpty()) {
                    return;
                }
                String msg = String.format("Format of the classes input file \"%s\" is incorrect,"
                        + " line \"%s\" has wrong format", klassesFileName, line);
                Asserts.assertTrue(line.matches("\\w[\\w\\.$]*"), msg);
                klassNamesList.add(line.replaceAll(";", ""));
            });
        } catch (IOException ex) {
            throw new Error("Error reading klasses file", ex);
        }
        List<Class<?>> klassesList = new ArrayList<>();
        klassNamesList.forEach(klassName -> {
            try {
                klassesList.add(Class.forName(klassName));
            } catch (ClassNotFoundException ex) {
                throw new Error("Unexpected exception while parsing klasses file", ex);
            }
        });
        return klassesList;
    }

    private void initMethodsToExclude(String methodsFileName) {
        if (methodsFileName != null && !methodsFileName.isEmpty()) {
            Path methodsFilePath = Paths.get(methodsFileName);
            try {
                methodsToExclude = Files.lines(methodsFilePath)
                    // Cleaning nonimportant parts
                    .map(TypesParser::trimComment)
                    .map(String::trim)
                    .filter(not(String::isEmpty))

                    // Actual parsing
                    .map(MethodTemplate::parse)
                    .collect(Collectors.toList());
            } catch (IOException ex) {
                throw new Error("Error reading exclude method file", ex);
            }
        } else {
            methodsToExclude = new ArrayList<>();
        }
    }
}
