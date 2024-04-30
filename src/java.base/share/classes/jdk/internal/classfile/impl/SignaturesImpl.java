/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Collections;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.Signature.*;

public final class SignaturesImpl {

    public SignaturesImpl(String signature) {
        this.sig = Objects.requireNonNull(signature);
        this.sigp = 0;
    }

    private final String sig;
    private int sigp;

    public ClassSignature parseClassSignature() {
        try {
            List<TypeParam> typeParamTypes = parseParamTypes();
            ClassTypeSig superclass = classTypeSig();
            ArrayList<ClassTypeSig> superinterfaces = null;
            while (sigp < sig.length()) {
                if (superinterfaces == null)
                    superinterfaces = new ArrayList<>();
                superinterfaces.add(classTypeSig());
            }
            return new ClassSignatureImpl(typeParamTypes, superclass, null2Empty(superinterfaces));
        } catch (IndexOutOfBoundsException e) {
            throw error("Not a valid class signature");
        }
    }

    public MethodSignature parseMethodSignature() {
        try {
            List<TypeParam> typeParamTypes = parseParamTypes();
            require('(');
            ArrayList<Signature> paramTypes = null;
            while (!match(')')) {
                if (paramTypes == null)
                    paramTypes = new ArrayList<>();
                paramTypes.add(typeSig());
            }
            Signature returnType = typeSig();
            ArrayList<ThrowableSig> throwsTypes = null;
            while (sigp < sig.length()) {
                require('^');
                if (throwsTypes == null)
                    throwsTypes = new ArrayList<>();
                var t = referenceTypeSig();
                if (t instanceof ThrowableSig ts)
                    throwsTypes.add(ts);
                else
                    throw error("Not a valid throwable signature %s in".formatted(t.signatureString()));
            }
            return new MethodSignatureImpl(typeParamTypes, null2Empty(throwsTypes), returnType, null2Empty(paramTypes));
        } catch (IndexOutOfBoundsException e) {
            throw error("Not a valid method signature");
        }
    }

    public Signature parseSignature() {
        try {
            var s = typeSig();
            if (sigp == sig.length())
                return s;
        } catch (IndexOutOfBoundsException e) {
        }
        throw error("Not a valid type signature");
    }

    private List<TypeParam> parseParamTypes() {
        ArrayList<TypeParam> typeParamTypes = null;
        if (match('<')) {
            typeParamTypes = new ArrayList<>();
            // cannot have empty <>
            do {
                String name = sig.substring(sigp, requireIdentifier());
                RefTypeSig classBound = null;
                ArrayList<RefTypeSig> interfaceBounds = null;
                require(':');
                if (sig.charAt(sigp) != ':')
                    classBound = referenceTypeSig();
                while (match(':')) {
                    if (interfaceBounds == null)
                        interfaceBounds = new ArrayList<>();
                    interfaceBounds.add(referenceTypeSig());
                }
                typeParamTypes.add(new TypeParamImpl(name, Optional.ofNullable(classBound), null2Empty(interfaceBounds)));
            } while (!match('>'));
        }
        return null2Empty(typeParamTypes);
    }

    private Signature typeSig() {
        char c = sig.charAt(sigp++);
        switch (c) {
            case 'B','C','D','F','I','J','V','S','Z': return Signature.BaseTypeSig.of(c);
            default:
                sigp--;
                return referenceTypeSig();
        }
    }

    private RefTypeSig referenceTypeSig() {
        return switch (sig.charAt(sigp)) {
            case 'L' -> classTypeSig();
            case 'T' -> {
                sigp++;
                var ty = Signature.TypeVarSig.of(sig.substring(sigp, requireIdentifier()));
                require(';');
                yield ty;
            }
            case '[' -> {
                sigp++;
                yield ArrayTypeSig.of(typeSig());
            }
            default -> throw unexpectedError("a type signature");
        };
    }

    private TypeArg typeArg() {
        char c = sig.charAt(sigp++);
        switch (c) {
            case '*': return TypeArg.unbounded();
            case '+': return TypeArg.extendsOf(referenceTypeSig());
            case '-': return TypeArg.superOf(referenceTypeSig());
            default:
                sigp--;
                return TypeArg.of(referenceTypeSig());
        }
    }

    private ClassTypeSig classTypeSig() {
        require('L');
        Signature.ClassTypeSig t = null;

        do {
            int start = sigp;
            requireIdentifier();
            if (t == null) {
                while (match('/')) {
                    requireIdentifier();
                }
            }
            String className = sig.substring(start, sigp);

            ArrayList<TypeArg> argTypes;
            if (match('<')) {
                // cannot have empty <>
                argTypes = new ArrayList<>();
                do {
                    argTypes.add(typeArg());
                } while (!match('>'));
            } else {
                argTypes = null;
            }

            boolean end = match(';');
            if (end || match('.')) {
                t = new ClassTypeSigImpl(Optional.ofNullable(t), className, null2Empty(argTypes));
                if (end)
                    return t;
            } else {
                throw unexpectedError(". or ;");
            }
        } while (true);
    }

    /**
     * Tries to match a character, and moves pointer if it matches.
     */
    private boolean match(char c) {
        if (sigp < sig.length() && sig.charAt(sigp) == c) {
            sigp++;
            return true;
        }
        return false;
    }

    /**
     * Requires a character and moves past it, failing otherwise.
     */
    private void require(char c) {
        if (!match(c))
            throw unexpectedError(String.valueOf(c));
    }

    /**
     * Requires an identifier, moving pointer to next illegal character and returning
     * its position. Fails if the identifier is empty.
     */
    private int requireIdentifier() {
        int start = sigp;
        l:
        while (sigp < sig.length()) {
            switch (sig.charAt(sigp)) {
                case '.', ';', '[', '/', '<', '>', ':' -> {
                    break l;
                }
            }
            sigp++;
        }
        if (start == sigp) {
            throw unexpectedError("an identifier");
        }
        return sigp;
    }

    public static record BaseTypeSigImpl(char baseType) implements Signature.BaseTypeSig {

        @Override
        public String signatureString() {
            return "" + baseType;
        }
    }

    public static record TypeVarSigImpl(String identifier) implements Signature.TypeVarSig {

        @Override
        public String signatureString() {
            return "T" + identifier + ';';
        }
    }

    public static record ArrayTypeSigImpl(int arrayDepth, Signature elemType) implements Signature.ArrayTypeSig {

        @Override
        public Signature componentSignature() {
            return arrayDepth > 1 ? new ArrayTypeSigImpl(arrayDepth - 1, elemType) : elemType;
        }

        @Override
        public String signatureString() {
            return "[".repeat(arrayDepth) + elemType.signatureString();
        }
    }

    public static record ClassTypeSigImpl(Optional<ClassTypeSig> outerType, String className, List<Signature.TypeArg> typeArgs)
            implements Signature.ClassTypeSig {

        @Override
        public String signatureString() {
            String prefix = "L";
            if (outerType.isPresent()) {
                prefix = outerType.get().signatureString();
                assert prefix.charAt(prefix.length() - 1) == ';';
                prefix = prefix.substring(0, prefix.length() - 1) + '.';
            }
            String suffix = ";";
            if (!typeArgs.isEmpty()) {
                var sb = new StringBuilder();
                sb.append('<');
                for (var ta : typeArgs)
                    sb.append(((TypeArgImpl)ta).signatureString());
                suffix = sb.append(">;").toString();
            }
            return prefix + className + suffix;
        }
    }

    public static record TypeArgImpl(WildcardIndicator wildcardIndicator, Optional<RefTypeSig> boundType) implements Signature.TypeArg {

        public String signatureString() {
            return switch (wildcardIndicator) {
                case DEFAULT -> boundType.get().signatureString();
                case EXTENDS -> "+" + boundType.get().signatureString();
                case SUPER -> "-" + boundType.get().signatureString();
                case UNBOUNDED -> "*";
            };
        }
    }

    public static record TypeParamImpl(String identifier, Optional<RefTypeSig> classBound, List<RefTypeSig> interfaceBounds)
            implements TypeParam {
    }

    private static StringBuilder printTypeParameters(List<TypeParam> typeParameters) {
        var sb = new StringBuilder();
        if (typeParameters != null && !typeParameters.isEmpty()) {
            sb.append('<');
            for (var tp : typeParameters) {
                sb.append(tp.identifier()).append(':');
                if (tp.classBound().isPresent())
                    sb.append(tp.classBound().get().signatureString());
                if (tp.interfaceBounds() != null) for (var is : tp.interfaceBounds())
                    sb.append(':').append(is.signatureString());
            }
            sb.append('>');
        }
        return sb;
    }

    public static record ClassSignatureImpl(List<TypeParam> typeParameters, ClassTypeSig superclassSignature,
            List<ClassTypeSig> superinterfaceSignatures) implements ClassSignature {

        @Override
        public String signatureString() {
            var sb = printTypeParameters(typeParameters);
            sb.append(superclassSignature.signatureString());
            if (superinterfaceSignatures != null) for (var in : superinterfaceSignatures)
                sb.append(in.signatureString());
            return sb.toString();
        }
    }

    public static record MethodSignatureImpl(
            List<TypeParam> typeParameters,
            List<ThrowableSig> throwableSignatures,
            Signature result,
            List<Signature> arguments) implements MethodSignature {

        @Override
        public String signatureString() {
            var sb = printTypeParameters(typeParameters);
            sb.append('(');
            for (var a : arguments)
                sb.append(a.signatureString());
            sb.append(')').append(result.signatureString());
            if (!throwableSignatures.isEmpty())
                for (var t : throwableSignatures)
                    sb.append('^').append(t.signatureString());
            return sb.toString();
        }
    }

    private static <T> List<T> null2Empty(ArrayList<T> l) {
        return l == null ? List.of() : Collections.unmodifiableList(l);
    }

    private IllegalArgumentException unexpectedError(String expected) {
        return error(sigp < sig.length() ? "Unexpected character %c at position %d, expected %s"
                .formatted(sig.charAt(sigp), sigp, expected)
                : "Unexpected end of signature at position %d, expected %s".formatted(sigp, expected));
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("%s: %s".formatted(message, sig));
    }
}
