/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.classfile.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jdk.classfile.ClassSignature;
import jdk.classfile.MethodSignature;
import jdk.classfile.Signature;
import jdk.classfile.Signature.*;

public final class SignaturesImpl {

    public SignaturesImpl() {
    }

    private String sig;
    private int sigp;

    // @@@ Move to ClassSignatureImpl to avoid list copying
    public ClassSignature parseClassSignature(String sig) {
        this.sig = sig;
        sigp = 0;
        List<TypeParam> typeParamTypes = parseParamTypes();
        RefTypeSig superclass = referenceTypeSig();
        List<RefTypeSig> superinterfaces = null;
        while (sigp < sig.length()) {
            if (superinterfaces == null)
                superinterfaces = new ArrayList<>();
            superinterfaces.add(referenceTypeSig());
        }
        return new ClassSignatureImpl(typeParamTypes, superclass, null2Empty(superinterfaces));
    }

    // @@@ Move to ClassSignatureImpl to avoid list copying
    public MethodSignature parseMethodSignature(String sig) {
        this.sig = sig;
        sigp = 0;
        List<TypeParam> typeParamTypes = parseParamTypes();
        assert sig.charAt(sigp) == '(';
        sigp++;
        List<Signature> paramTypes = new ArrayList<>();
        while (sig.charAt(sigp) != ')')
            paramTypes.add(typeSig());
        sigp++;
        Signature returnType = typeSig();
        List<ThrowableSig> throwsTypes = null;
        while (sigp < sig.length() && sig.charAt(sigp) == '^') {
            sigp++;
            if (throwsTypes == null)
                throwsTypes = new ArrayList<>();
            var t = typeSig();
            if (t instanceof ThrowableSig ts)
                throwsTypes.add(ts);
            else
                throw new IllegalStateException("not a valid type signature: " + sig);
        }
        return new MethodSignatureImpl(typeParamTypes, paramTypes, returnType, null2Empty(throwsTypes));
    }

    public Signature parseSignature(String sig) {
        this.sig = sig;
        sigp = 0;
        return typeSig();
    }

    private List<TypeParam> parseParamTypes() {
        List<TypeParam> typeParamTypes = null;
        if (sig.charAt(sigp) == '<') {
            sigp++;
            typeParamTypes = new ArrayList<>();
            while (sig.charAt(sigp) != '>') {
                int sep = sig.indexOf(":", sigp);
                String name = sig.substring(sigp, sep);
                RefTypeSig classBound = null;
                List<RefTypeSig> interfaceBounds = null;
                sigp = sep + 1;
                if (sig.charAt(sigp) != ':')
                    classBound = referenceTypeSig();
                while (sig.charAt(sigp) == ':') {
                    sigp++;
                    if (interfaceBounds == null)
                        interfaceBounds = new ArrayList<>();
                    interfaceBounds.add(referenceTypeSig());
                }
                typeParamTypes.add(new TypeParamImpl(name, Optional.ofNullable(classBound), null2Empty(interfaceBounds)));
            }
            sigp++;
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
        char c = sig.charAt(sigp++);
        switch (c) {
            case 'L':
                StringBuilder sb = new StringBuilder();
                List<Signature> argTypes = null;
                Signature.ClassTypeSig t = null;
                char sigch ;
                do {
                    switch  (sigch = sig.charAt(sigp++)) {
                        case '<' -> {
                            argTypes = new ArrayList<>();
                            while (sig.charAt(sigp) != '>')
                                argTypes.add(typeSig());
                            sigp++;
                        }
                        case '.',';' -> {
                            t = new ClassTypeSigImpl(Optional.ofNullable(t), sb.toString(), null2Empty(argTypes));
                            sb.setLength(0);
                            argTypes = null;
                        }
                        default -> sb.append(sigch);
                    }
                } while (sigch != ';');
                return t;
            case 'T':
                int sep = sig.indexOf(';', sigp);
                var ty = Signature.TypeVarSig.of(sig.substring(sigp, sep));
                sigp = sep + 1;
                return ty;
            case '[': return ArrayTypeSig.of(typeSig());
            case '*': return TypeArg.unbounded();
            case '+': return TypeArg.extendsOf(referenceTypeSig());
            case '-': return TypeArg.superOf(referenceTypeSig());
        }
        throw new IllegalStateException("not a valid type signature: " + sig);
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

    public static record ClassTypeSigImpl(Optional<ClassTypeSig> outerType, String className, List<Signature> typeArgs)
            implements Signature.ClassTypeSig {

        public ClassTypeSigImpl(Optional<ClassTypeSig> outerType, String className, List<Signature> typeArgs) {
            this.outerType = outerType;
            this.className = className;
            this.typeArgs = List.copyOf(typeArgs);
        }

        @Override
        public String signatureString() {
            String prefix = "L";
            if (outerType.isPresent()) {
                prefix = outerType.get().signatureString();
                assert prefix.charAt(prefix.length() - 1) == ';';
                prefix = prefix.substring(0, prefix.length() - 1) + '.';
            }
            String suffix = ";";
            if (typeArgs != null && !typeArgs.isEmpty()) {
                var sb = new StringBuilder();
                sb.append('<');
                for (var ta : typeArgs)
                    sb.append(ta.signatureString());
                suffix = sb.append(">;").toString();
            }
            return prefix + className + suffix;
        }
    }

    public static record TypeArgImpl(WildcardIndicator wildcardIndicator, Optional<RefTypeSig> boundType) implements Signature.TypeArg {

        @Override
        public String signatureString() {
            return switch (wildcardIndicator) {
                case EXTENDS, SUPER -> wildcardIndicator.indicator + boundType.get().signatureString();
                case UNBOUNDED -> "*";
            };
        }
    }

    public static record TypeParamImpl(String identifier, Optional<RefTypeSig> classBound, List<RefTypeSig> interfaceBounds)
            implements TypeParam {
        public TypeParamImpl(String identifier, Optional<RefTypeSig> classBound, List<RefTypeSig> interfaceBounds) {
            this.identifier = identifier;
            this.classBound = classBound;
            this.interfaceBounds = List.copyOf(interfaceBounds);
        }
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

    public static record ClassSignatureImpl(List<TypeParam> typeParameters, RefTypeSig superclassSignature,
            List<RefTypeSig> superinterfaceSignatures) implements ClassSignature {

        public ClassSignatureImpl(List<TypeParam> typeParameters, RefTypeSig superclassSignature,
                                  List<RefTypeSig> superinterfaceSignatures) {
            this.typeParameters = List.copyOf(typeParameters);
            this.superclassSignature = superclassSignature;
            this.superinterfaceSignatures = List.copyOf(superinterfaceSignatures);
        }

        @Override
        public String signatureString() {
            var sb = printTypeParameters(typeParameters);
            sb.append(superclassSignature.signatureString());
            if (superinterfaceSignatures != null) for (var in : superinterfaceSignatures)
                sb.append(in.signatureString());
            return sb.toString();
        }
    }

    public static record MethodSignatureImpl(List<TypeParam> typeParameters,
            List<Signature> arguments,
            Signature result,
            List<ThrowableSig> throwableSignatures) implements MethodSignature {

        public MethodSignatureImpl(List<TypeParam> typeParameters, List<Signature> arguments, Signature result,
                                   List<ThrowableSig> throwableSignatures) {
            this.typeParameters = List.copyOf(typeParameters);
            this.arguments = List.copyOf(arguments);
            this.result = result;
            this.throwableSignatures = List.copyOf(throwableSignatures);
        }

        @Override
        public String signatureString() {
            var sb = printTypeParameters(typeParameters);
            sb.append('(');
            for (var a : arguments)
                sb.append(a.signatureString());
            sb.append(')').append(result.signatureString());
            if (throwableSignatures != null && !throwableSignatures.isEmpty())
                for (var t : throwableSignatures)
                    sb.append('^').append(t.signatureString());
            return sb.toString();
        }
    }

    public static <T> List<T> null2Empty(List<T> l) {
        return l == null ? List.of() : l;
    }
}
