/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester.visitors;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import jdk.test.lib.jittester.BinaryOperator;
import jdk.test.lib.jittester.Block;
import jdk.test.lib.jittester.Break;
import jdk.test.lib.jittester.CastOperator;
import jdk.test.lib.jittester.CatchBlock;
import jdk.test.lib.jittester.Continue;
import jdk.test.lib.jittester.Declaration;
import jdk.test.lib.jittester.IRNode;
import jdk.test.lib.jittester.If;
import jdk.test.lib.jittester.Initialization;
import jdk.test.lib.jittester.Literal;
import jdk.test.lib.jittester.LocalVariable;
import jdk.test.lib.jittester.LogicOperator;
import jdk.test.lib.jittester.NonStaticMemberVariable;
import jdk.test.lib.jittester.Nothing;
import jdk.test.lib.jittester.Operator;
import jdk.test.lib.jittester.PrintVariables;
import jdk.test.lib.jittester.ProductionParams;
import jdk.test.lib.jittester.Statement;
import jdk.test.lib.jittester.StaticMemberVariable;
import jdk.test.lib.jittester.Switch;
import jdk.test.lib.jittester.Symbol;
import jdk.test.lib.jittester.TernaryOperator;
import jdk.test.lib.jittester.Throw;
import jdk.test.lib.jittester.TryCatchBlock;
import jdk.test.lib.jittester.Type;
import jdk.test.lib.jittester.TypeList;
import jdk.test.lib.jittester.UnaryOperator;
import jdk.test.lib.jittester.VariableBase;
import jdk.test.lib.jittester.VariableDeclaration;
import jdk.test.lib.jittester.VariableDeclarationBlock;
import jdk.test.lib.jittester.VariableInfo;
import jdk.test.lib.jittester.arrays.ArrayCreation;
import jdk.test.lib.jittester.arrays.ArrayElement;
import jdk.test.lib.jittester.arrays.ArrayExtraction;
import jdk.test.lib.jittester.classes.ClassDefinitionBlock;
import jdk.test.lib.jittester.classes.Interface;
import jdk.test.lib.jittester.classes.Klass;
import jdk.test.lib.jittester.classes.MainKlass;
import jdk.test.lib.jittester.functions.ArgumentDeclaration;
import jdk.test.lib.jittester.functions.ConstructorDefinition;
import jdk.test.lib.jittester.functions.ConstructorDefinitionBlock;
import jdk.test.lib.jittester.functions.Function;
import jdk.test.lib.jittester.functions.FunctionDeclaration;
import jdk.test.lib.jittester.functions.FunctionDeclarationBlock;
import jdk.test.lib.jittester.functions.FunctionDefinition;
import jdk.test.lib.jittester.functions.FunctionDefinitionBlock;
import jdk.test.lib.jittester.functions.FunctionInfo;
import jdk.test.lib.jittester.functions.FunctionRedefinition;
import jdk.test.lib.jittester.functions.FunctionRedefinitionBlock;
import jdk.test.lib.jittester.functions.Return;
import jdk.test.lib.jittester.functions.StaticConstructorDefinition;
import jdk.test.lib.jittester.loops.CounterInitializer;
import jdk.test.lib.jittester.loops.CounterManipulator;
import jdk.test.lib.jittester.loops.DoWhile;
import jdk.test.lib.jittester.loops.For;
import jdk.test.lib.jittester.loops.Loop;
import jdk.test.lib.jittester.loops.LoopingCondition;
import jdk.test.lib.jittester.loops.While;
import jdk.test.lib.jittester.types.TypeArray;
import jdk.test.lib.jittester.types.TypeByte;
import jdk.test.lib.jittester.types.TypeKlass;
import jdk.test.lib.jittester.types.TypeChar;
import jdk.test.lib.jittester.types.TypeDouble;
import jdk.test.lib.jittester.types.TypeFloat;
import jdk.test.lib.jittester.types.TypeLong;
import jdk.test.lib.jittester.types.TypeShort;
import jdk.test.lib.jittester.utils.PrintingUtils;

public class JavaCodeVisitor implements Visitor<String> {

    public static String funcAttributes(FunctionInfo fi) {
        String attrs = attributes(fi);
        if (fi.isSynchronized()) {
            attrs += "synchronized ";
        }
        return attrs;
    }

    public static String attributes(Symbol s) {
        String attrs = "";
        if (s.isPrivate()) {
            attrs += "private ";
        }
        if (s.isProtected()) {
            attrs += "protected ";
        }
        if (s.isPublic()) {
            attrs += "public ";
        }
        if (s.isFinal()) {
            attrs += "final ";
        }
        if (s.isStatic()) {
            attrs += "static ";
        }
        return attrs;
    }

    public String expressionToJavaCode(Operator t, IRNode p, Operator.Order o) {
        String result;
        try {
            if ((o == Operator.Order.LEFT && ((Operator) p).getPriority() < t.getPriority())
                    || (o == Operator.Order.RIGHT && ((Operator) p).getPriority() <= t.getPriority())) {
                result = "(" + p.accept(this)+ ")";
            } else {
                result = p.accept(this);
            }
        } catch (Exception e) {
            result = p.accept(this);
        }
        return result;
    }

    @Override
    public String visit(ArgumentDeclaration node) {
        VariableInfo vi = node.variableInfo;
        return attributes(vi) + vi.type.accept(this) + " " + vi.name;
    }

    @Override
    public String visit(ArrayCreation node) {
        Type arrayType = node.getArrayType();
        String type = arrayType.accept(this);
        String name = node.getVariable().getName();
        StringBuilder code = new StringBuilder()
                .append(node.getVariable().accept(this))
                .append(";\n")
                .append(PrintingUtils.align(node.getParent().getLevel()))
                .append(name)
                .append(" = new ")
                .append(type);
        code.append(node.getChildren().stream()
                .map(p -> p.accept(this))
                .collect(Collectors.joining("][", "[", "]")));
        code.append(";\n");
        if (!TypeList.isBuiltIn(arrayType)) {
            code.append(PrintingUtils.align(node.getParent().getLevel()))
                .append("java.util.Arrays.fill(")
                .append(name)
                .append(", new ")
                .append(type)
                .append("());\n");
        }
        return code.toString();
    }

    @Override
    public String visit(ArrayElement node) {
        IRNode array = node.getChild(0);
        StringBuilder code = new StringBuilder();
        if (array instanceof VariableBase || array instanceof Function) {
            code.append(array.accept(this));
        } else {
            code.append("(")
                .append(array.accept(this))
                .append(")");
        }
        code.append(node.getChildren().stream()
                .skip(1)
                .map(c -> c.accept(this))
                .collect(Collectors.joining("][", "[", "]")));
        return code.toString();
    }

    @Override
    public String visit(ArrayExtraction node) {
        IRNode array = node.getChild(0);
        StringBuilder code = new StringBuilder();
        if (array instanceof VariableBase || array instanceof Function) {
            code.append(array.accept(this));
        } else {
            code.append("(")
                .append(array.accept(this))
                .append(")");
        }
        code.append(node.getChildren().stream()
                .skip(1)
                .map(c -> c.accept(this))
                .collect(Collectors.joining("][", "[", "]")));
        return code.toString();
    }

    @Override
    public String visit(BinaryOperator node) {
        IRNode left = node.getChild(Operator.Order.LEFT.ordinal());
        IRNode right = node.getChild(Operator.Order.RIGHT.ordinal());
        if (left == null || right == null) {
            return "null";
        }
        return expressionToJavaCode(node, left, Operator.Order.LEFT)
               + " " + node.getOperationCode() + " "
               + expressionToJavaCode(node, right, Operator.Order.RIGHT);
    }

    @Override
    public String visit(Block node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            String s = i.accept(this);
            if (!s.isEmpty()) {
                int level = node.getLevel();
                if (i instanceof Block) {
                    code.append(PrintingUtils.align(level + 1))
                        .append("{\n")
                        .append(s)
                        .append(PrintingUtils.align(level + 1))
                        .append("}");
                } else {
                    code.append(PrintingUtils.align(level + 1))
                        .append(s);
                }
                code.append(addComplexityInfo(i));
                code.append("\n");
            }
        }
        return code.toString();
    }

    private String addComplexityInfo(IRNode node) {
        if (ProductionParams.printComplexity.value()) {
            return " /* " + node.complexity() + " */";
        }
        return "";
    }

    @Override
    public String visit(Break node) {
        return "break;";
    }

    @Override
    public String visit(CastOperator node) {
        return "(" + node.getResultType().accept(this)+ ")"
                + expressionToJavaCode(node, node.getChild(0), Operator.Order.LEFT);
    }

    @Override
    public String visit(ClassDefinitionBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append("\n")
                .append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append("\n");
        }

        return code.toString();
    }

    @Override
    public String visit(ConstructorDefinition node) {
        String args = node.getChildren().stream()
                .skip(1)
                .map(c -> c.accept(this))
                .collect(Collectors.joining(", "));
        IRNode body = node.getChild(0);
        StringBuilder code = new StringBuilder();
        code.append(funcAttributes(node.getFunctionInfo()))
            .append(node.getFunctionInfo().name)
            .append("(")
            .append(args)
            .append(")\n")
            .append(PrintingUtils.align(node.getLevel() + 1))
            .append("{\n")
            .append(body != null ? body.accept(this) : "")
            .append(PrintingUtils.align(node.getLevel() + 1))
            .append("}");
        return code.toString();
    }

    @Override
    public String visit(ConstructorDefinitionBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append("\n")
                .append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(Continue node) {
        return "continue;";
    }

    @Override
    public String visit(CounterInitializer node) {
        VariableInfo vi = node.get();
        return vi.type.accept(this) + " " + vi.name + " = " + node.getChild(0).accept(this)+ ";";
    }

    @Override
    public String visit(CounterManipulator node) {
        return node.getChild(0).accept(this);
    }

    @Override
    public String visit(Declaration node) {
        return node.getChild(0).accept(this)+ ";";
    }

    @Override
    public String visit(DoWhile node) {
        IRNode header = node.getChild(DoWhile.DoWhilePart.HEADER.ordinal());
        IRNode body1 = node.getChild(DoWhile.DoWhilePart.BODY1.ordinal());
        IRNode body2 = node.getChild(DoWhile.DoWhilePart.BODY2.ordinal());
        StringBuilder code = new StringBuilder();
        Loop loop = node.getLoop();
        int level = node.getLevel();
        code.append(loop.initialization.accept(this))
            .append("\n")
            .append(header.accept(this))
            .append(PrintingUtils.align(level))
            .append("do\n")
            .append(PrintingUtils.align(level))
            .append("{\n")
            .append(body1.accept(this))
            .append(PrintingUtils.align(level + 1))
            .append(loop.manipulator.accept(this))
            .append(";\n")
            .append(body2.accept(this))
            .append(PrintingUtils.align(level))
            .append("} while (")
            .append(loop.condition.accept(this))
            .append(");");
        return code.toString();
    }

    @Override
    public String visit(For node) {
        IRNode header = node.getChild(For.ForPart.HEADER.ordinal());
        IRNode statement1 = node.getChild(For.ForPart.STATEMENT1.ordinal());
        IRNode statement2 = node.getChild(For.ForPart.STATEMENT2.ordinal());
        IRNode body1 = node.getChild(For.ForPart.BODY1.ordinal());
        IRNode body2 = node.getChild(For.ForPart.BODY2.ordinal());
        IRNode body3 = node.getChild(For.ForPart.BODY3.ordinal());
        Loop loop = node.getLoop();
        StringBuilder code = new StringBuilder();
        int level = node.getLevel();
        code.append(loop.initialization.accept(this))
            .append("\n")
            .append(header.accept(this))
            .append(PrintingUtils.align(level))
            .append("for (")
            .append(statement1.accept(this))
            .append("; ")
            .append(loop.condition.accept(this))
            .append("; ")
            .append(statement2.accept(this))
            .append(")\n")
            .append(PrintingUtils.align(level))
            .append("{\n")
            .append(body1.accept(this))
            .append(PrintingUtils.align(level + 1))
            .append(loop.manipulator.accept(this))
            .append(";\n")
            .append(body2.accept(this))
            .append(body3.accept(this))
            .append(PrintingUtils.align(level))
            .append("}");
        return code.toString();
    }

    @Override
    public String visit(Function node) {
        FunctionInfo value = node.getValue();
        String nameAndArgs = value.name + "("
                + node.getChildren().stream()
                    .skip(value.isStatic() || value.isConstructor() ? 0 : 1)
                    .map(c -> c.accept(this))
                    .collect(Collectors.joining(", "))
                + ")";
        String prefix = "";
        if (value.isStatic()) {
            if(!node.getKlass().equals(value.klass)) {
                prefix = value.klass.getName() + ".";
            }
        } else if (value.isConstructor()) {
            prefix = "new ";
        } else {
            IRNode object = node.getChild(0);
            String objectString = object.accept(this);
            if (!objectString.equals("this")) {
                 if (object instanceof VariableBase || object instanceof Function
                            || object instanceof Literal) {
                     prefix = objectString + ".";
                 } else {
                     prefix = "(" + objectString + ")" + ".";
                 }
            }
        }
        return prefix + nameAndArgs;
    }

    @Override
    public String visit(FunctionDeclaration node) {
        String args = node.getChildren().stream()
                .map(c -> c.accept(this))
                .collect(Collectors.joining(", "));

        FunctionInfo functionInfo = node.getFunctionInfo();
        return (functionInfo.klass.isInterface() ? "" : "abstract ")
                + funcAttributes(functionInfo) + functionInfo.type.accept(this)+ " "
                + functionInfo.name + "(" + args + ");";
    }

    @Override
    public String visit(FunctionDeclarationBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(FunctionDefinition node) {
        String args = node.getChildren().stream()
                .skip(2)
                .map(c -> c.accept(this))
                .collect(Collectors.joining(", "));
        IRNode body = node.getChild(0);
        IRNode ret = node.getChild(1);
        FunctionInfo functionInfo = node.getFunctionInfo();
        return funcAttributes(functionInfo) + functionInfo.type.accept(this) + " " + functionInfo.name + "(" + args + ")" + "\n"
                + PrintingUtils.align(node.getLevel() + 1) + "{\n"
                + body.accept(this)
                + (ret != null ? PrintingUtils.align(node.getLevel() + 2) + ret.accept(this) + "\n" : "")
                + PrintingUtils.align(node.getLevel() + 1) + "}";
    }

    @Override
    public String visit(FunctionDefinitionBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append("\n")
                .append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(FunctionRedefinition node) {
        String args = node.getChildren().stream()
                .map(c -> c.accept(this))
                .collect(Collectors.joining(", "));

        IRNode body = node.getChild(0);
        IRNode ret = node.getChild(1);
        int level = node.getLevel();
        FunctionInfo functionInfo = node.getFunctionInfo();
        return funcAttributes(functionInfo) + functionInfo.type + " " + functionInfo.name + "(" + args + ")" + "\n"
                + PrintingUtils.align(level + 1) + "{\n"
                + body
                + (ret != null ? PrintingUtils.align(level + 2) + ret + "\n" : "")
                + PrintingUtils.align(level + 1) + "}";
    }

    @Override
    public String visit(FunctionRedefinitionBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append("\n")
                .append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(If node) {
        int level = node.getLevel();
        String thenBlockString = PrintingUtils.align(level) + "{\n"
                                 + node.getChild(If.IfPart.THEN.ordinal()).accept(this)
                                 + PrintingUtils.align(level) + "}";

        String elseBlockString = null;
        if (node.getChild(If.IfPart.ELSE.ordinal()) != null) {
            elseBlockString = PrintingUtils.align(level) + "{\n"
                              + node.getChild(If.IfPart.ELSE.ordinal()).accept(this)
                              + PrintingUtils.align(level) + "}";
        }

        return "if (" + node.getChild(If.IfPart.CONDITION.ordinal()).accept(this)+ ")\n"
            + thenBlockString + (elseBlockString != null ? "\n"
            + PrintingUtils.align(level) + "else\n" + elseBlockString : "");
    }

    @Override
    public String visit(Initialization node) {
        VariableInfo vi = node.getVariableInfo();
        return attributes(vi) + vi.type.accept(this)+ " " + vi.name + " = "
                + node.getChild(0).accept(this);
    }

    @Override
    public String visit(Interface node) {
        return "interface " + node.getName() + (node.getParentKlass() != null ? " extends "
                + node.getParentKlass().getName() : "") + " {\n"
                + (node.getChildren().size() > 0 ? node.getChild(0).accept(this) : "")
                + "}\n";
    }

    @Override
    public String visit(Klass node) {
        TypeKlass thisKlass = node.getThisKlass();
        String r = (ProductionParams.enableStrictFP.value() ? "strictfp " : "")
                + (thisKlass.isFinal() ? "final " : "")
                + (thisKlass.isAbstract() ? "abstract " : "")
                + "class " + node.getName()
                + (node.getParentKlass()!= null ? " extends " + node.getParentKlass().getName() : "");
        List<TypeKlass> interfaces = node.getInterfaces();
        r += interfaces.stream()
                .map(c -> c.getName())
                .collect(Collectors.joining(", ", (interfaces.isEmpty() ? "" : " implements "), ""));
        IRNode dataMembers = node.getChild(Klass.KlassPart.DATA_MEMBERS.ordinal());
        IRNode constructors = node.getChild(Klass.KlassPart.CONSTRUCTORS.ordinal());
        IRNode redefinedFunctions = node.getChild(Klass.KlassPart.REDEFINED_FUNCTIONS.ordinal());
        IRNode overridenFunctions = node.getChild(Klass.KlassPart.OVERRIDEN_FUNCTIONS.ordinal());
        IRNode memberFunctions = node.getChild(Klass.KlassPart.MEMBER_FUNCTIONS.ordinal());
        IRNode memberFunctionDecls = node.getChild(Klass.KlassPart.MEMBER_FUNCTIONS_DECLARATIONS.ordinal());
        IRNode printVariables = node.getChild(Klass.KlassPart.PRINT_VARIABLES.ordinal());
        r += " {\n"
             + (dataMembers != null ? (dataMembers.accept(this)+ "\n") : "")
             + (constructors != null ? (constructors.accept(this)+ "\n") : "")
             + (redefinedFunctions != null ? (redefinedFunctions.accept(this)+ "\n") : "")
             + (overridenFunctions != null ? (overridenFunctions.accept(this)+ "\n") : "")
             + (memberFunctionDecls != null ? (memberFunctionDecls.accept(this)+ "\n") : "")
             + (memberFunctions != null ? (memberFunctions.accept(this)+ "\n") : "")
             + "    public String toString()\n"
             + "    {\n"
             + printVariables.accept(this)
             + "    }\n"
             + "}\n";
        return r;
    }

    @Override
    public String visit(Literal node) {
        Type resultType = node.getResultType();
        Object value = node.getValue();
        if (resultType.equals(new TypeLong())) {
            return value.toString() + "L";
        }
        if (resultType.equals(new TypeFloat())) {
            return String.format((Locale) null,
                "%." + ProductionParams.floatingPointPrecision.value() + "EF",
                Double.parseDouble(value.toString()));
        }
        if (resultType.equals(new TypeDouble())) {
            return String.format((Locale) null,
                "%." + 2 * ProductionParams.floatingPointPrecision.value() + "E",
                Double.parseDouble(value.toString()));
        }
        if (resultType.equals(new TypeChar())) {
            if (((Character) value).charValue() == '\\') {
                return "\'" + "\\\\" + "\'";
            } else {
                return "\'" + value.toString() + "\'";
            }
        }
        if (resultType.equals(new TypeShort())) {
            return "(short) " + value.toString();
        }
        if (resultType.equals(new TypeByte())) {
            return "(byte) " + value.toString();
        }
        return value.toString();
    }

    @Override
    public String visit(LocalVariable node) {
        return node.get().name;
    }

    @Override
    public String visit(LogicOperator node) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String visit(LoopingCondition node) {
        return node.getCondition().accept(this);
    }

    @Override
    public String visit(MainKlass node) {
        String name = node.getName();
        IRNode dataMembers = node.getChild(MainKlass.MainKlassPart.DATA_MEMBERS.ordinal());
        IRNode memberFunctions = node.getChild(MainKlass.MainKlassPart.MEMBER_FUNCTIONS.ordinal());
        IRNode testFunction = node.getChild(MainKlass.MainKlassPart.TEST_FUNCTION.ordinal());
        IRNode printVariables = node.getChild(MainKlass.MainKlassPart.PRINT_VARIABLES.ordinal());
        String executeFunction = "    public static String execute()\n"
                + "    {\n"
                + "        try {\n"
                + "            " + name + " t = new " + name + "();\n"
                + "            try { t.test(); }\n"
                + "            catch(Throwable e) { }\n"
                + "            try { return t.toString(); }\n"
                + "            catch (Throwable e) { return \"Error during result conversion to String\"; }\n"
                + "        } catch (Throwable e) { return \"Error during test execution\"; }\n"
                + "    }\n";
        String mainFunction = "    public static void main(String[] args)\n"
                + "    {\n"
                + "        try {\n"
                + "            " + name + " t = new " + name + "();\n"
                + "            try {\n"
                + "                for (int i = 0; i < 150000; ++i) {\n"
                + "                    t.test();\n"
                + "                }\n"
                + "            }\n"
                + "            catch(Throwable e) { e.printStackTrace(); }\n"
                + "            try { System.out.println(t); }\n"
                + "            catch(Throwable e) { e.printStackTrace();}\n"
                + "        } catch (Throwable e) { e.printStackTrace(); }\n"
                + "    }\n";
        String printerClass = "    static class Printer\n"
                + "    {\n"
                + "        public static String print(boolean arg) { return String.valueOf(arg); }\n"
                + "        public static String print(byte arg)    { return String.valueOf(arg); }\n"
                + "        public static String print(short arg)   { return String.valueOf(arg); }\n"
                + "        public static String print(char arg)    { return String.valueOf((int)arg); }\n"
                + "        public static String print(int arg)     { return String.valueOf(arg); }\n"
                + "        public static String print(long arg)    { return String.valueOf(arg); }\n"
                + "        public static String print(float arg)   { return String.valueOf(arg); }\n"
                + "        public static String print(double arg)  { return String.valueOf(arg); }\n"
                + "\n"
                + "\n"
                + "        public static String print(Object arg)\n"
                + "        {\n"
                + "            return print_r(new java.util.Stack(), arg);\n"
                + "        }\n"
                + "\n"
                + "        private static String print_r(java.util.Stack visitedObjects, Object arg)\n"
                + "        {\n"
                + "            String result = \"\";\n"
                + "            if (arg == null)\n"
                + "                result += \"null\";\n"
                + "            else\n"
                + "            if (arg.getClass().isArray())\n"
                + "            {\n"
                + "                for (int i = 0; i < visitedObjects.size(); i++)\n"
                + "                    if (visitedObjects.elementAt(i) == arg) return \"<recursive>\";\n"
                + "\n"
                + "                visitedObjects.push(arg);\n"
                + "\n"
                + "                final String delimiter = \", \";\n"
                + "                result += \"[\";\n"
                + "\n"
                + "                if (arg instanceof Object[])\n"
                + "                {\n"
                + "                    Object[] array = (Object[]) arg;\n"
                + "                    for (int i = 0; i < array.length; i++)\n"
                + "                    {\n"
                + "                        result += print_r(visitedObjects, array[i]);\n"
                + "                        if (i < array.length - 1) result += delimiter;\n"
                + "                    }\n"
                + "                }\n"
                + "                else\n"
                + "                if (arg instanceof boolean[])\n"
                + "                {\n"
                + "                    boolean[] array = (boolean[]) arg;\n"
                + "                    for (int i = 0; i < array.length; i++)\n"
                + "                    {\n"
                + "                        result += print(array[i]);\n"
                + "                        if (i < array.length - 1) result += delimiter;\n"
                + "                    }\n"
                + "                }\n"
                + "                else\n"
                + "                if (arg instanceof byte[])\n"
                + "                {\n"
                + "                    byte[] array = (byte[]) arg;\n"
                + "                    for (int i = 0; i < array.length; i++)\n"
                + "                    {\n"
                + "                        result += print(array[i]);\n"
                + "                        if (i < array.length - 1) result += delimiter;\n"
                + "                    }\n"
                + "                }\n"
                + "                else\n"
                + "                if (arg instanceof short[])\n"
                + "                {\n"
                + "                    short[] array = (short[]) arg;\n"
                + "                    for (int i = 0; i < array.length; i++)\n"
                + "                    {\n"
                + "                        result += print(array[i]);\n"
                + "                        if (i < array.length - 1) result += delimiter;\n"
                + "                    }\n"
                + "                }\n"
                + "                else\n"
                + "                if (arg instanceof char[])\n"
                + "                {\n"
                + "                    char[] array = (char[]) arg;\n"
                + "                    for (int i = 0; i < array.length; i++)\n"
                + "                    {\n"
                + "                        result += print(array[i]);\n"
                + "                        if (i < array.length - 1) result += delimiter;\n"
                + "                    }\n"
                + "                }\n"
                + "                else\n"
                + "                if (arg instanceof int[])\n"
                + "                {\n"
                + "                     int[] array = (int[]) arg;\n"
                + "                     for (int i = 0; i < array.length; i++)\n"
                + "                     {\n"
                + "                        result += print(array[i]);\n"
                + "                        if (i < array.length - 1) result += delimiter;\n"
                + "                     }\n"
                + "                }\n"
                + "                else\n"
                + "                if (arg instanceof long[])\n"
                + "                {\n"
                + "                    long[] array = (long[]) arg;\n"
                + "                    for (int i = 0; i < array.length; i++)\n"
                + "                    {\n"
                + "                        result += print(array[i]);\n"
                + "                        if (i < array.length - 1) result += delimiter;\n"
                + "                    }\n"
                + "                }\n"
                + "                else\n"
                + "                if (arg instanceof float[])\n"
                + "                {\n"
                + "                    float[] array = (float[]) arg;\n"
                + "                    for (int i = 0; i < array.length; i++)\n"
                + "                    {\n"
                + "                        result += print(array[i]);\n"
                + "                        if (i < array.length - 1) result += delimiter;\n"
                + "                    }\n"
                + "                }\n"
                + "                else\n"
                + "                if (arg instanceof double[])\n"
                + "                {\n"
                + "                    double[] array = (double[]) arg;\n"
                + "                    for (int i = 0; i < array.length; i++)\n"
                + "                    {\n"
                + "                        result += print(array[i]);\n"
                + "                        if (i < array.length - 1) result += delimiter;\n"
                + "                    }\n"
                + "                }\n"
                + "\n"
                + "                result += \"]\";\n"
                + "                visitedObjects.pop();\n"
                + "\n"
                + "            } else\n"
                + "            {\n"
                + "                result += arg.toString();\n"
                + "            }\n"
                + "\n"
                + "            return result;\n"
                + "        }\n"
                + "    }\n";

        return (ProductionParams.enableStrictFP.value() ? "strictfp " : "")
                + "public class " + name + " {\n"
                + dataMembers.accept(this)+ "\n"
                + (memberFunctions != null ? memberFunctions.accept(this): "") + "\n"
                + executeFunction
                + "\n"
                + mainFunction
                + "\n"
                + "    private void test()\n"
                + "    {\n"
                + testFunction.accept(this)
                + "    }" + addComplexityInfo(testFunction) + "\n"
                + "    public String toString()\n"
                + "    {\n"
                + printVariables.accept(this)
                + "    }\n"
                + printerClass
                + "}\n\n";
    }

    @Override
    public String visit(NonStaticMemberVariable node) {
        IRNode object = node.getChild(0);
        String objectString = object.accept(this);
        VariableInfo value = node.getValue();
        if (objectString.equals("this")) {
            return value.name;
        } else {
            if (object instanceof VariableBase || object instanceof Function || object instanceof Literal) {
                return objectString + "." + value.name;
            } else {
                return "(" + objectString + ")" + "." + value.name;
            }
        }
    }

    @Override
    public String visit(Nothing node) {
        return "";
    }

    @Override
    public String visit(PrintVariables node) {
        int level = node.getLevel();
        List<Symbol> vars = node.getVars();
        StringBuilder result = new StringBuilder()
                .append(PrintingUtils.align(level))
                .append("String result =  \"[\\n\";\n");
        if (!vars.isEmpty()) {
            for (int i = 0; i < vars.size(); i++) {
                Symbol v = vars.get(i);
                result.append(PrintingUtils.align(level))
                        .append("result += \"").append(v.klass.getName())
                        .append(".")
                        .append(v.name)
                        .append(" = \"; ")
                        .append("result += ")
                        .append(node.getPrinterName())
                        .append(".print(")
                        .append(v.name)
                        .append(");\n")
                        .append(PrintingUtils.align(level));
                if (i < vars.size() - 1) {
                    result.append("result += \"\\n\";");
                } else {
                    result.append("result += \"\";");
                }
                result.append("\n");
            }
        }
        result.append(PrintingUtils.align(level))
                .append("result += \"\\n]\";\n")
                .append(PrintingUtils.align(level))
                .append("return result;\n");
        return result.toString();
    }

    @Override
    public String visit(Return node) {
        return "return " + node.getExpression().accept(this) + ";";
    }

    @Override
    public String visit(Throw node) {
        return "throw " + node.getThowable().accept(this) + ";";
    }

    @Override
    public String visit(Statement node) {
        return node.getChild(0).accept(this)+ (node.isSemicolonNeeded() ? ";" : "");
    }

    @Override
    public String visit(StaticConstructorDefinition node) {
        IRNode body = node.getChild(0);
        return "static {\n"
                + (body != null ? body.accept(this): "")
                + PrintingUtils.align(node.getLevel()) + "}";
    }

    @Override
    public String visit(StaticMemberVariable node) {
        IRNode klass = node.getKlass();
        VariableInfo value = node.get();
        if (klass.equals(value.klass)) {
            return value.name;
        } else {
            return value.klass.getName() + "." + value.name;
        }
    }

    @Override
    public String visit(Switch node) {
        int level = node.getLevel();
        int caseBlockIdx = node.getCaseBlockIndex();
        String cases = "";
        for (int i = 0; i < caseBlockIdx - 1; ++i) {
            cases += PrintingUtils.align(level + 1);
            if (node.getChild(i + 1) != null) {
                cases += "case " + node.getChild(i + 1).accept(this)+ ":\n";
            } else {
                cases += "default:\n";
            }

            cases += node.getChild(i + caseBlockIdx).accept(this)+ "\n";
        }
        return "switch (" + node.getChild(0).accept(this)+ ")\n"
               + PrintingUtils.align(level) + "{\n"
               + cases
               + PrintingUtils.align(level) + "}";
    }

    @Override
    public String visit(TernaryOperator node) {
        IRNode conditionalExp = node.getChild(TernaryOperator.TernaryPart.CONDITION.ordinal());
        IRNode leftExp = node.getChild(TernaryOperator.TernaryPart.TRUE.ordinal());
        IRNode rightExp = node.getChild(TernaryOperator.TernaryPart.FALSE.ordinal());
        if (Objects.isNull(conditionalExp) || Objects.isNull(leftExp) || Objects.isNull(rightExp)) {
            return "null";
        }
        return expressionToJavaCode(node, conditionalExp, Operator.Order.RIGHT) + " ? "
                + expressionToJavaCode(node, leftExp, Operator.Order.RIGHT) + " : "
                + expressionToJavaCode(node, rightExp, Operator.Order.RIGHT);
    }

    @Override
    public String visit(Type node) {
        return node.getName();
    }

    @Override
    public String visit(TypeArray node) {
        String r = node.getType().accept(this);
        for (int i = 0; i < node.getDimensions(); i++) {
            r += "[]";
        }
        return r;
    }

    @Override
    public String visit(UnaryOperator node) {
        IRNode exp = node.getChild(0);
        if (node.isPrefix()) {
            return node.getOperatorText() + (exp instanceof Operator ? " " : "")
                    + expressionToJavaCode(node, exp, Operator.Order.LEFT);
        } else {
            return expressionToJavaCode(node, exp, Operator.Order.RIGHT)
                    + (exp instanceof Operator ? " " : "") + node.getOperatorText();
        }
    }

    @Override
    public String visit(VariableDeclaration node) {
        VariableInfo vi = node.getVariableInfo();
        return attributes(vi) + vi.type.accept(this)+ " " + vi.name;
    }

    @Override
    public String visit(VariableDeclarationBlock node) {
        StringBuilder code = new StringBuilder();
        for (IRNode i : node.getChildren()) {
            code.append(PrintingUtils.align(node.getLevel()))
                .append(i.accept(this))
                .append(addComplexityInfo(i))
                .append("\n");
        }
        return code.toString();
    }

    @Override
    public String visit(While node) {
        IRNode header = node.getChild(While.WhilePart.HEADER.ordinal());
        IRNode body1 = node.getChild(While.WhilePart.BODY1.ordinal());
        IRNode body2 = node.getChild(While.WhilePart.BODY2.ordinal());
        IRNode body3 = node.getChild(While.WhilePart.BODY3.ordinal());
        int level = node.getLevel();
        Loop loop = node.getLoop();
        return loop.initialization.accept(this)+ "\n"
                + header.accept(this)
                + PrintingUtils.align(level) + "while (" + loop.condition.accept(this)+ ")\n"
                + PrintingUtils.align(level) + "{\n"
                + body1.accept(this)
                + PrintingUtils.align(level + 1) + loop.manipulator.accept(this)+ ";\n"
                + body2.accept(this)
                + body3.accept(this)
                + PrintingUtils.align(level) + "}";
    }

    @Override
    public String visit(CatchBlock node) {
        StringBuilder result = new StringBuilder();
        int level = node.getLevel();
        result.append(PrintingUtils.align(level)).append("catch(");
        result.append(node.throwables.get(0).accept(this));
        for (int i = 1; i < node.throwables.size(); i++) {
            result.append(" | ").append(node.throwables.get(i).accept(this));
        }
        result.append(" ex) {\n");
        result.append(node.getChild(0).accept(this));
        result.append(PrintingUtils.align(level)).append("}\n");
        return result.toString();
    }

    @Override
    public String visit(TryCatchBlock node) {
        StringBuilder result = new StringBuilder();
        List<? extends IRNode> childs = node.getChildren();
        IRNode body = childs.get(0);
        IRNode finallyBody = childs.get(1);
        int level = node.getLevel();
        result.append("try {\n")
                .append(body.accept(this)).append("\n")
                .append(PrintingUtils.align(level)).append("}\n");
        for (int i = 2; i < childs.size(); i++) {
            result.append(childs.get(i).accept(this));
        }
        if (finallyBody != null) {
            result.append(PrintingUtils.align(level)).append("finally {\n")
                    .append(finallyBody.accept(this)).append("\n")
                    .append(PrintingUtils.align(level)).append("}\n");
        }
        return result.toString();
    }
}
