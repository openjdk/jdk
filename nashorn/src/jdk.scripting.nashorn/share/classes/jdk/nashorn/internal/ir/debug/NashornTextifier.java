/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir.debug;

import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_PROGRAM_POINT_SHIFT;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.FLAGS_MASK;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.internal.dynalink.support.NameCodec;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.signature.SignatureReader;
import jdk.internal.org.objectweb.asm.util.Printer;
import jdk.internal.org.objectweb.asm.util.TraceSignatureVisitor;
import jdk.nashorn.internal.runtime.ScriptEnvironment;
import jdk.nashorn.internal.runtime.linker.Bootstrap;
import jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor;

/**
 * Pretty printer for --print-code.
 * Also supports dot formats if --print-code has arguments
 */
public final class NashornTextifier extends Printer {
    private static final String BOOTSTRAP_CLASS_NAME = Bootstrap.class.getName().replace('.', '/');

    private String currentClassName;
    private Iterator<Label> labelIter;
    private Graph graph;
    private String currentBlock;

    // Following variables are used to govern the state of collapsing long sequences of NOP.
    /** True if the last instruction was a NOP. */
    private boolean lastWasNop = false;
    /** True if ellipse ("...") was emitted in place of a second NOP. */
    private boolean lastWasEllipse = false;

    private static final int INTERNAL_NAME = 0;
    private static final int FIELD_DESCRIPTOR = 1;
    private static final int FIELD_SIGNATURE = 2;
    private static final int METHOD_DESCRIPTOR = 3;
    private static final int METHOD_SIGNATURE = 4;
    private static final int CLASS_SIGNATURE = 5;

    private final String tab = "  ";
    private final String tab2 = "    ";
    private final String tab3 = "      ";

    private Map<Label, String> labelNames;

    private boolean localVarsStarted = false;

    private NashornClassReader cr;
    private ScriptEnvironment env;

    /**
     * Constructs a new {@link NashornTextifier}. <i>Subclasses must not use this
     * constructor</i>. Instead, they must use the {@link #NashornTextifier(int)}
     * version.
     * @param env script environment
     * @param cr a customized classreader for gathering, among other things, label
     * information
     */
    public NashornTextifier(final ScriptEnvironment env, final NashornClassReader cr) {
        this(Opcodes.ASM5);
        this.env = env;
        this.cr = cr;
    }

    private NashornTextifier(final ScriptEnvironment env, final NashornClassReader cr, final Iterator<Label> labelIter, final Graph graph) {
        this(env, cr);
        this.labelIter = labelIter;
        this.graph = graph;
    }

    /**
     * Constructs a new {@link NashornTextifier}.
     *
     * @param api
     *            the ASM API version implemented by this visitor. Must be one
     *            of {@link Opcodes#ASM4} or {@link Opcodes#ASM5}.
     */
    protected NashornTextifier(final int api) {
        super(api);
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
        final int major = version & 0xFFFF;
        final int minor = version >>> 16;

        currentClassName = name;

        final StringBuilder sb = new StringBuilder();
        sb.append("// class version ").
            append(major).
            append('.').
            append(minor).append(" (").
            append(version).
            append(")\n");

        if ((access & Opcodes.ACC_DEPRECATED) != 0) {
            sb.append("// DEPRECATED\n");
        }

        sb.append("// access flags 0x"). //TODO TRANSLATE TO WHAT THEY MEAN
            append(Integer.toHexString(access).toUpperCase()).
            append('\n');

        appendDescriptor(sb, CLASS_SIGNATURE, signature);
        if (signature != null) {
            final TraceSignatureVisitor sv = new TraceSignatureVisitor(access);
            final SignatureReader r = new SignatureReader(signature);
            r.accept(sv);
            sb.append("// declaration: ").
                append(name).
                append(sv.getDeclaration()).
                append('\n');
        }

        appendAccess(sb, access & ~Opcodes.ACC_SUPER);
        if ((access & Opcodes.ACC_ANNOTATION) != 0) {
            sb.append("@interface ");
        } else if ((access & Opcodes.ACC_INTERFACE) != 0) {
            sb.append("interface ");
        } else if ((access & Opcodes.ACC_ENUM) == 0) {
            sb.append("class ");
        }
        appendDescriptor(sb, INTERNAL_NAME, name);

        if (superName != null && !"java/lang/Object".equals(superName)) {
            sb.append(" extends ");
            appendDescriptor(sb, INTERNAL_NAME, superName);
            sb.append(' ');
        }
        if (interfaces != null && interfaces.length > 0) {
            sb.append(" implements ");
            for (final String interface1 : interfaces) {
                appendDescriptor(sb, INTERNAL_NAME, interface1);
                sb.append(' ');
            }
        }
        sb.append(" {\n");

        addText(sb);
    }

    @Override
    public void visitSource(final String file, final String debug) {
        final StringBuilder sb = new StringBuilder();
        if (file != null) {
            sb.append(tab).
                append("// compiled from: ").
                append(file).
                append('\n');
        }
        if (debug != null) {
            sb.append(tab).
                append("// debug info: ").
                append(debug).
                append('\n');
        }
        if (sb.length() > 0) {
            addText(sb);
        }
    }

    @Override
    public void visitOuterClass(final String owner, final String name, final String desc) {
        final StringBuilder sb = new StringBuilder();
        sb.append(tab).append("outer class ");
        appendDescriptor(sb, INTERNAL_NAME, owner);
        sb.append(' ');
        if (name != null) {
            sb.append(name).append(' ');
        }
        appendDescriptor(sb, METHOD_DESCRIPTOR, desc);
        sb.append('\n');
        addText(sb);
    }

    @Override
    public NashornTextifier visitField(final int access, final String name, final String desc, final String signature, final Object value) {
        final StringBuilder sb = new StringBuilder();
//        sb.append('\n');
        if ((access & Opcodes.ACC_DEPRECATED) != 0) {
            sb.append(tab).append("// DEPRECATED\n");
        }

/*        sb.append(tab).
            append("// access flags 0x").
            append(Integer.toHexString(access).toUpperCase()).
            append('\n');
*/

        if (signature != null) {
            sb.append(tab);
            appendDescriptor(sb, FIELD_SIGNATURE, signature);

            final TraceSignatureVisitor sv = new TraceSignatureVisitor(0);
            final SignatureReader r = new SignatureReader(signature);
            r.acceptType(sv);
            sb.append(tab).
                append("// declaration: ").
                append(sv.getDeclaration()).
                append('\n');
        }

        sb.append(tab);
        appendAccess(sb, access);

        final String prunedDesc = desc.endsWith(";") ? desc.substring(0, desc.length() - 1) : desc;
        appendDescriptor(sb, FIELD_DESCRIPTOR, prunedDesc);
        sb.append(' ').append(name);
        if (value != null) {
            sb.append(" = ");
            if (value instanceof String) {
                sb.append('\"').append(value).append('\"');
            } else {
                sb.append(value);
            }
        }

        sb.append(";\n");
        addText(sb);

        final NashornTextifier t = createNashornTextifier();
        addText(t.getText());

        return t;
    }

    @Override
    public NashornTextifier visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {

        graph = new Graph(name);

        final List<Label> extraLabels = cr.getExtraLabels(currentClassName, name, desc);
        this.labelIter = extraLabels == null ? null : extraLabels.iterator();

        final StringBuilder sb = new StringBuilder();

        sb.append('\n');
        if ((access & Opcodes.ACC_DEPRECATED) != 0) {
            sb.append(tab).
                append("// DEPRECATED\n");
        }

        sb.append(tab).
            append("// access flags 0x").
            append(Integer.toHexString(access).toUpperCase()).
            append('\n');

        if (signature != null) {
            sb.append(tab);
            appendDescriptor(sb, METHOD_SIGNATURE, signature);

            final TraceSignatureVisitor v = new TraceSignatureVisitor(0);
            final SignatureReader r = new SignatureReader(signature);
            r.accept(v);
            final String genericDecl = v.getDeclaration();
            final String genericReturn = v.getReturnType();
            final String genericExceptions = v.getExceptions();

            sb.append(tab).
                append("// declaration: ").
                append(genericReturn).
                append(' ').
                append(name).
                append(genericDecl);

            if (genericExceptions != null) {
                sb.append(" throws ").append(genericExceptions);
            }
            sb.append('\n');
        }

        sb.append(tab);
        appendAccess(sb, access);
        if ((access & Opcodes.ACC_NATIVE) != 0) {
            sb.append("native ");
        }
        if ((access & Opcodes.ACC_VARARGS) != 0) {
            sb.append("varargs ");
        }
        if ((access & Opcodes.ACC_BRIDGE) != 0) {
            sb.append("bridge ");
        }

        sb.append(name);
        appendDescriptor(sb, METHOD_DESCRIPTOR, desc);
        if (exceptions != null && exceptions.length > 0) {
            sb.append(" throws ");
            for (final String exception : exceptions) {
                appendDescriptor(sb, INTERNAL_NAME, exception);
                sb.append(' ');
            }
        }

        sb.append('\n');
        addText(sb);

        final NashornTextifier t = createNashornTextifier();
        addText(t.getText());
        return t;
    }

    @Override
    public void visitClassEnd() {
        addText("}\n");
    }

    @Override
    public void visitFieldEnd() {
        //empty
    }

    @Override
    public void visitParameter(final String name, final int access) {
        final StringBuilder sb = new StringBuilder();
        sb.append(tab2).append("// parameter ");
        appendAccess(sb, access);
        sb.append(' ').append(name == null ? "<no name>" : name)
                .append('\n');
        addText(sb);
    }

    @Override
    public void visitCode() {
        //empty
    }

    @Override
    public void visitFrame(final int type, final int nLocal, final Object[] local, final int nStack, final Object[] stack) {
        final StringBuilder sb = new StringBuilder();
        sb.append("frame ");
        switch (type) {
        case Opcodes.F_NEW:
        case Opcodes.F_FULL:
            sb.append("full [");
            appendFrameTypes(sb, nLocal, local);
            sb.append("] [");
            appendFrameTypes(sb, nStack, stack);
            sb.append(']');
            break;
        case Opcodes.F_APPEND:
            sb.append("append [");
            appendFrameTypes(sb, nLocal, local);
            sb.append(']');
            break;
        case Opcodes.F_CHOP:
            sb.append("chop ").append(nLocal);
            break;
        case Opcodes.F_SAME:
            sb.append("same");
            break;
        case Opcodes.F_SAME1:
            sb.append("same1 ");
            appendFrameTypes(sb, 1, stack);
            break;
        default:
            assert false;
            break;
        }
        sb.append('\n');
        sb.append('\n');
        addText(sb);
    }

    private StringBuilder appendOpcode(final StringBuilder sb, final int opcode) {
        final Label next = getNextLabel();
        if (next instanceof NashornLabel) {
            final int bci = next.getOffset();
            if (bci != -1) {
                final String bcis = "" + bci;
                for (int i = 0; i < 5 - bcis.length(); i++) {
                    sb.append(' ');
                }
                sb.append(bcis);
                sb.append(' ');
            } else {
                sb.append("       ");
            }
        }

        return sb.append(tab2).append(OPCODES[opcode].toLowerCase());
    }

    private Label getNextLabel() {
        return labelIter == null ? null : labelIter.next();
    }

    @Override
    public void visitInsn(final int opcode) {
        if(opcode == Opcodes.NOP) {
            if(lastWasEllipse) {
                getNextLabel();
                return;
            } else if(lastWasNop) {
                getNextLabel();
                addText("          ...\n");
                lastWasEllipse = true;
                return;
            } else {
                lastWasNop = true;
            }
        } else {
            lastWasNop = lastWasEllipse = false;
        }
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, opcode).append('\n');
        addText(sb);
        checkNoFallThru(opcode, null);
    }

    @Override
    public void visitIntInsn(final int opcode, final int operand) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, opcode)
                .append(' ')
                .append(opcode == Opcodes.NEWARRAY ? TYPES[operand] : Integer
                        .toString(operand)).append('\n');
        addText(sb);
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, opcode).append(' ').append(var).append('\n');
        addText(sb);
    }

    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, opcode).append(' ');
        appendDescriptor(sb, INTERNAL_NAME, type);
        sb.append('\n');
        addText(sb);
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, opcode).append(' ');
        appendDescriptor(sb, INTERNAL_NAME, owner);
        sb.append('.').append(name).append(" : ");
        appendDescriptor(sb, FIELD_DESCRIPTOR, desc);
        sb.append('\n');
        addText(sb);
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc, final boolean itf) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, opcode).append(' ');
        appendDescriptor(sb, INTERNAL_NAME, owner);
        sb.append('.').append(name);
        appendDescriptor(sb, METHOD_DESCRIPTOR, desc);
        sb.append('\n');
        addText(sb);
    }

    @Override
    public void visitInvokeDynamicInsn(final String name, final String desc, final Handle bsm, final Object... bsmArgs) {
        final StringBuilder sb = new StringBuilder();

        appendOpcode(sb, Opcodes.INVOKEDYNAMIC).append(' ');
        final boolean isNashornBootstrap = isNashornBootstrap(bsm);
        if (isNashornBootstrap) {
            sb.append(NashornCallSiteDescriptor.getOperationName((Integer)bsmArgs[0]));
            final String decodedName = NameCodec.decode(name);
            if (!decodedName.isEmpty()) {
                sb.append(':').append(decodedName);
            }
        } else {
            sb.append(name);
        }
        appendDescriptor(sb, METHOD_DESCRIPTOR, desc);
        final int len = sb.length();
        for (int i = 0; i < 80 - len ; i++) {
            sb.append(' ');
        }
        sb.append(" [");
        appendHandle(sb, bsm);
        if (bsmArgs.length == 0) {
            sb.append("none");
        } else {
            for (final Object cst : bsmArgs) {
                if (cst instanceof String) {
                    appendStr(sb, (String)cst);
                } else if (cst instanceof Type) {
                    sb.append(((Type)cst).getDescriptor()).append(".class");
                } else if (cst instanceof Handle) {
                    appendHandle(sb, (Handle)cst);
                } else if (cst instanceof Integer && isNashornBootstrap) {
                    final int c = (Integer)cst;
                    final int pp = c >> CALLSITE_PROGRAM_POINT_SHIFT;
                    if (pp != 0) {
                        sb.append(" pp=").append(pp);
                    }
                    sb.append(NashornCallSiteDescriptor.toString(c & FLAGS_MASK));
                } else {
                    sb.append(cst);
                }
                sb.append(", ");
            }
            sb.setLength(sb.length() - 2);
        }

        sb.append("]\n");
        addText(sb);
    }

    private static boolean isNashornBootstrap(final Handle bsm) {
        return "bootstrap".equals(bsm.getName()) && BOOTSTRAP_CLASS_NAME.equals(bsm.getOwner());
    }

    private static boolean noFallThru(final int opcode) {
        switch (opcode) {
        case Opcodes.GOTO:
        case Opcodes.ATHROW:
        case Opcodes.ARETURN:
        case Opcodes.IRETURN:
        case Opcodes.LRETURN:
        case Opcodes.FRETURN:
        case Opcodes.DRETURN:
            return true;
        default:
            return false;
        }
    }

    private void checkNoFallThru(final int opcode, final String to) {
        if (noFallThru(opcode)) {
            graph.setNoFallThru(currentBlock);
        }

        if (currentBlock != null && to != null) {
            graph.addEdge(currentBlock, to);
        }
    }

    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, opcode).append(' ');
        final String to = appendLabel(sb, label);
        sb.append('\n');
        addText(sb);
        checkNoFallThru(opcode, to);
    }

    private void addText(final Object t) {
        text.add(t);
        if (currentBlock != null) {
            graph.addText(currentBlock, t.toString());
        }
    }

    @Override
    public void visitLabel(final Label label) {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n");
        final String name = appendLabel(sb, label);
        sb.append(" [bci=");
        sb.append(label.info);
        sb.append("]");
        sb.append("\n");

        graph.addNode(name);
        if (currentBlock != null && !graph.isNoFallThru(currentBlock)) {
            graph.addEdge(currentBlock, name);
        }
        currentBlock = name;
        addText(sb);
    }

    @Override
    public void visitLdcInsn(final Object cst) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, Opcodes.LDC).append(' ');
        if (cst instanceof String) {
            appendStr(sb, (String) cst);
        } else if (cst instanceof Type) {
            sb.append(((Type) cst).getDescriptor()).append(".class");
        } else {
            sb.append(cst);
        }
        sb.append('\n');
        addText(sb);
    }

    @Override
    public void visitIincInsn(final int var, final int increment) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, Opcodes.IINC).append(' ');
        sb.append(var).append(' ')
                .append(increment).append('\n');
        addText(sb);
    }

    @Override
    public void visitTableSwitchInsn(final int min, final int max, final Label dflt, final Label... labels) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, Opcodes.TABLESWITCH).append(' ');
        for (int i = 0; i < labels.length; ++i) {
            sb.append(tab3).append(min + i).append(": ");
            final String to = appendLabel(sb, labels[i]);
            graph.addEdge(currentBlock, to);
            sb.append('\n');
        }
        sb.append(tab3).append("default: ");
        appendLabel(sb, dflt);
        sb.append('\n');
        addText(sb);
    }

    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, Opcodes.LOOKUPSWITCH).append(' ');
        for (int i = 0; i < labels.length; ++i) {
            sb.append(tab3).append(keys[i]).append(": ");
            final String to = appendLabel(sb, labels[i]);
            graph.addEdge(currentBlock, to);
            sb.append('\n');
        }
        sb.append(tab3).append("default: ");
        final String to = appendLabel(sb, dflt);
        graph.addEdge(currentBlock, to);
        sb.append('\n');
        addText(sb.toString());
    }

    @Override
    public void visitMultiANewArrayInsn(final String desc, final int dims) {
        final StringBuilder sb = new StringBuilder();
        appendOpcode(sb, Opcodes.MULTIANEWARRAY).append(' ');
        appendDescriptor(sb, FIELD_DESCRIPTOR, desc);
        sb.append(' ').append(dims).append('\n');
        addText(sb);
    }

    @Override
    public void visitTryCatchBlock(final Label start, final Label end, final Label handler, final String type) {
        final StringBuilder sb = new StringBuilder();
        sb.append(tab2).append("try ");
        final String from = appendLabel(sb, start);
        sb.append(' ');
        appendLabel(sb, end);
        sb.append(' ');
        final String to = appendLabel(sb, handler);
        sb.append(' ');
        appendDescriptor(sb, INTERNAL_NAME, type);
        sb.append('\n');
        addText(sb);
        graph.setIsCatch(to, type);
        graph.addTryCatch(from, to);
    }

    @Override
    public void visitLocalVariable(final String name, final String desc,final String signature, final Label start, final Label end, final int index) {

        final StringBuilder sb = new StringBuilder();
        if (!localVarsStarted) {
            text.add("\n");
            localVarsStarted = true;
            graph.addNode("vars");
            currentBlock = "vars";
        }

        sb.append(tab2).append("local ").append(name).append(' ');
        final int len = sb.length();
        for (int i = 0; i < 25 - len; i++) {
            sb.append(' ');
        }
        String label;

        label = appendLabel(sb, start);
        for (int i = 0; i < 5 - label.length(); i++) {
            sb.append(' ');
        }
        label = appendLabel(sb, end);
        for (int i = 0; i < 5 - label.length(); i++) {
            sb.append(' ');
        }

        sb.append(index).append(tab2);

        appendDescriptor(sb, FIELD_DESCRIPTOR, desc);
        sb.append('\n');

        if (signature != null) {
            sb.append(tab2);
            appendDescriptor(sb, FIELD_SIGNATURE, signature);

            final TraceSignatureVisitor sv = new TraceSignatureVisitor(0);
            final SignatureReader r = new SignatureReader(signature);
            r.acceptType(sv);
            sb.append(tab2).append("// declaration: ")
                    .append(sv.getDeclaration()).append('\n');
        }
        addText(sb.toString());
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<line ");
        sb.append(line);
        sb.append(">\n");
        addText(sb.toString());
    }

    @Override
    public void visitMaxs(final int maxStack, final int maxLocals) {
        final StringBuilder sb = new StringBuilder();
        sb.append('\n');
        sb.append(tab2).append("max stack  = ").append(maxStack);
        sb.append(", max locals = ").append(maxLocals).append('\n');
        addText(sb.toString());
    }

    private void printToDir(final Graph g) {
        if (env._print_code_dir != null) {
            final File dir = new File(env._print_code_dir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException(dir.toString());
            }

            File file;
            int uniqueId = 0;
            do {
                final String fileName = g.getName() + (uniqueId == 0 ? "" : "_" + uniqueId) +  ".dot";
                file = new File(dir, fileName);
                uniqueId++;
            } while (file.exists());

            try (PrintWriter pw = new PrintWriter(new FileOutputStream(file))) {
                pw.println(g);
            } catch (final FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void visitMethodEnd() {
        //here we need to do several bytecode guesses best upon the ldc instructions.
        //for each instruction, assign bci. for an ldc/w/2w, guess a byte and keep
        //iterating. if the next label is wrong, backtrack.
        if (env._print_code_func == null || env._print_code_func.equals(graph.getName())) {
            if (env._print_code_dir != null) {
                printToDir(graph);
            }
        }
    }

    /**
     * Creates a new TraceVisitor instance.
     *
     * @return a new TraceVisitor.
     */
    protected NashornTextifier createNashornTextifier() {
        return new NashornTextifier(env, cr, labelIter, graph);
    }

    private static void appendDescriptor(final StringBuilder sb, final int type, final String desc) {
        if (desc != null) {
            if (type == CLASS_SIGNATURE || type == FIELD_SIGNATURE || type == METHOD_SIGNATURE) {
                sb.append("// signature ").append(desc).append('\n');
            } else {
                appendShortDescriptor(sb, desc);
            }
        }
    }

    private String appendLabel(final StringBuilder sb, final Label l) {
        if (labelNames == null) {
            labelNames = new HashMap<>();
        }
        String name = labelNames.get(l);
        if (name == null) {
            name = "L" + labelNames.size();
            labelNames.put(l, name);
        }
        sb.append(name);
        return name;
    }

    private static void appendHandle(final StringBuilder sb, final Handle h) {
        switch (h.getTag()) {
        case Opcodes.H_GETFIELD:
            sb.append("getfield");
            break;
        case Opcodes.H_GETSTATIC:
            sb.append("getstatic");
            break;
        case Opcodes.H_PUTFIELD:
            sb.append("putfield");
            break;
        case Opcodes.H_PUTSTATIC:
            sb.append("putstatic");
            break;
        case Opcodes.H_INVOKEINTERFACE:
            sb.append("interface");
            break;
        case Opcodes.H_INVOKESPECIAL:
            sb.append("special");
            break;
        case Opcodes.H_INVOKESTATIC:
            sb.append("static");
            break;
        case Opcodes.H_INVOKEVIRTUAL:
            sb.append("virtual");
            break;
        case Opcodes.H_NEWINVOKESPECIAL:
            sb.append("new_special");
            break;
        default:
            assert false;
            break;
        }
        sb.append(" '");
        sb.append(h.getName());
        sb.append("'");
    }

    private static void appendAccess(final StringBuilder sb, final int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            sb.append("public ");
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            sb.append("private ");
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            sb.append("protected ");
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            sb.append("final ");
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            sb.append("static ");
        }
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            sb.append("synchronized ");
        }
        if ((access & Opcodes.ACC_VOLATILE) != 0) {
            sb.append("volatile ");
        }
        if ((access & Opcodes.ACC_TRANSIENT) != 0) {
            sb.append("transient ");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            sb.append("abstract ");
        }
        if ((access & Opcodes.ACC_STRICT) != 0) {
            sb.append("strictfp ");
        }
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
            sb.append("synthetic ");
        }
        if ((access & Opcodes.ACC_MANDATED) != 0) {
            sb.append("mandated ");
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            sb.append("enum ");
        }
    }

    private void appendFrameTypes(final StringBuilder sb, final int n, final Object[] o) {
        for (int i = 0; i < n; ++i) {
            if (i > 0) {
                sb.append(' ');
            }
            if (o[i] instanceof String) {
                final String desc = (String) o[i];
                if (desc.startsWith("[")) {
                    appendDescriptor(sb, FIELD_DESCRIPTOR, desc);
                } else {
                    appendDescriptor(sb, INTERNAL_NAME, desc);
                }
            } else if (o[i] instanceof Integer) {
                switch (((Integer)o[i])) {
                case 0:
                    appendDescriptor(sb, FIELD_DESCRIPTOR, "T");
                    break;
                case 1:
                    appendDescriptor(sb, FIELD_DESCRIPTOR, "I");
                    break;
                case 2:
                    appendDescriptor(sb, FIELD_DESCRIPTOR, "F");
                    break;
                case 3:
                    appendDescriptor(sb, FIELD_DESCRIPTOR, "D");
                    break;
                case 4:
                    appendDescriptor(sb, FIELD_DESCRIPTOR, "J");
                    break;
                case 5:
                    appendDescriptor(sb, FIELD_DESCRIPTOR, "N");
                    break;
                case 6:
                    appendDescriptor(sb, FIELD_DESCRIPTOR, "U");
                    break;
                default:
                    assert false;
                    break;
                }
            } else {
                appendLabel(sb, (Label) o[i]);
            }
        }
    }

    private static void appendShortDescriptor(final StringBuilder sb, final String desc) {
        //final StringBuilder buf = new StringBuilder();
        if (desc.charAt(0) == '(') {
            for (int i = 0; i < desc.length(); i++) {
                if (desc.charAt(i) == 'L') {
                    int slash = i;
                    while (desc.charAt(i) != ';') {
                        i++;
                        if (desc.charAt(i) == '/') {
                            slash = i;
                        }
                    }
                    sb.append(desc.substring(slash + 1, i)).append(';');
                } else {
                    sb.append(desc.charAt(i));
                }
            }
        } else {
            final int lastSlash = desc.lastIndexOf('/');
            final int lastBracket = desc.lastIndexOf('[');
            if(lastBracket != -1) {
                sb.append(desc, 0, lastBracket + 1);
            }
            sb.append(lastSlash == -1 ? desc : desc.substring(lastSlash + 1));
        }
    }

    private static void appendStr(final StringBuilder sb, final String s) {
        sb.append('\"');
        for (int i = 0; i < s.length(); ++i) {
            final char c = s.charAt(i);
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c < 0x20 || c > 0x7f) {
                sb.append("\\u");
                if (c < 0x10) {
                    sb.append("000");
                } else if (c < 0x100) {
                    sb.append("00");
                } else if (c < 0x1000) {
                    sb.append('0');
                }
                sb.append(Integer.toString(c, 16));
            } else {
                sb.append(c);
            }
        }
        sb.append('\"');
    }

    private static class Graph {
        private final LinkedHashSet<String> nodes;
        private final Map<String, StringBuilder> contents;
        private final Map<String, Set<String>> edges;
        private final Set<String> hasPreds;
        private final Set<String> noFallThru;
        private final Map<String, String> catches;
        private final Map<String, Set<String>> exceptionMap; //maps catch nodes to all their trys that can reach them
        private final String name;

        private static final String LEFT_ALIGN      = "\\l";
        private static final String COLOR_CATCH     = "\"#ee9999\"";
        private static final String COLOR_ORPHAN    = "\"#9999bb\"";
        private static final String COLOR_DEFAULT   = "\"#99bb99\"";
        private static final String COLOR_LOCALVARS = "\"#999999\"";

        Graph(final String name) {
            this.name         = name;
            this.nodes        = new LinkedHashSet<>();
            this.contents     = new HashMap<>();
            this.edges        = new HashMap<>();
            this.hasPreds     = new HashSet<>();
            this.catches      = new HashMap<>();
            this.noFallThru   = new HashSet<>();
            this.exceptionMap = new HashMap<>();
         }

        void addEdge(final String from, final String to) {
            Set<String> edgeSet = edges.get(from);
            if (edgeSet == null) {
                edgeSet = new LinkedHashSet<>();
                edges.put(from, edgeSet);
            }
            edgeSet.add(to);
            hasPreds.add(to);
        }

        void addTryCatch(final String tryNode, final String catchNode) {
            Set<String> tryNodes = exceptionMap.get(catchNode);
            if (tryNodes == null) {
                tryNodes = new HashSet<>();
                exceptionMap.put(catchNode, tryNodes);
            }
            if (!tryNodes.contains(tryNode)) {
                addEdge(tryNode, catchNode);
            }
            tryNodes.add(tryNode);
        }

        void addNode(final String node) {
            assert !nodes.contains(node);
            nodes.add(node);
        }

        void setNoFallThru(final String node) {
            noFallThru.add(node);
        }

        boolean isNoFallThru(final String node) {
            return noFallThru.contains(node);
        }

        void setIsCatch(final String node, final String exception) {
            catches.put(node, exception);
        }

        String getName() {
            return name;
        }

        void addText(final String node, final String text) {
            StringBuilder sb = contents.get(node);
            if (sb == null) {
                sb = new StringBuilder();
            }

            for (int i = 0; i < text.length(); i++) {
                switch (text.charAt(i)) {
                case '\n':
                    sb.append(LEFT_ALIGN);
                    break;
                case '"':
                    sb.append("'");
                    break;
                default:
                    sb.append(text.charAt(i));
                    break;
                }
           }

            contents.put(node, sb);
        }

        private static String dottyFriendly(final String name) {
            return name.replace(':', '_');
        }

        @Override
        public String toString() {

            final StringBuilder sb = new StringBuilder();
            sb.append("digraph ").append(dottyFriendly(name)).append(" {");
            sb.append("\n");
            sb.append("\tgraph [fontname=courier]\n");
            sb.append("\tnode [style=filled,color="+COLOR_DEFAULT+",fontname=courier]\n");
            sb.append("\tedge [fontname=courier]\n\n");

            for (final String node : nodes) {
                sb.append("\t");
                sb.append(node);
                sb.append(" [");
                sb.append("id=");
                sb.append(node);
                sb.append(", label=\"");
                String c = contents.get(node).toString();
                if (c.startsWith(LEFT_ALIGN)) {
                    c = c.substring(LEFT_ALIGN.length());
                }
                final String ex = catches.get(node);
                if (ex != null) {
                    sb.append("*** CATCH: ").append(ex).append(" ***\\l");
                }
                sb.append(c);
                sb.append("\"]\n");
            }

            for (final String from : edges.keySet()) {
                for (final String to : edges.get(from)) {
                    sb.append("\t");
                    sb.append(from);
                    sb.append(" -> ");
                    sb.append(to);
                    sb.append("[label=\"");
                    sb.append(to);
                    sb.append("\"");
                    if (catches.get(to) != null) {
                        sb.append(", color=red, style=dashed");
                    }
                    sb.append(']');
                    sb.append(";\n");
                }
            }

            sb.append("\n");
            for (final String node : nodes) {
                sb.append("\t");
                sb.append(node);
                sb.append(" [shape=box");
                if (catches.get(node) != null) {
                    sb.append(", color=" + COLOR_CATCH);
                } else if ("vars".equals(node)) {
                    sb.append(", shape=hexagon, color=" + COLOR_LOCALVARS);
                } else if (!hasPreds.contains(node)) {
                    sb.append(", color=" + COLOR_ORPHAN);
                }
                sb.append("]\n");
            }

            sb.append("}\n");
            return sb.toString();
        }
    }

    static class NashornLabel extends Label {
        final Label label;
        final int   bci;
        final int   opcode;

        NashornLabel(final Label label, final int bci) {
            this.label = label;
            this.bci   = bci;
            this.opcode = -1;
        }

        //not an ASM label
        NashornLabel(final int opcode, final int bci) {
            this.opcode = opcode;
            this.bci = bci;
            this.label = null;
        }

        Label getLabel() {
            return label;
        }

        @Override
        public int getOffset() {
            return bci;
        }

        @Override
        public String toString() {
            return "label " + bci;
        }
    }

    @Override
    public Printer visitAnnotationDefault() {
        throw new AssertionError();
    }

    @Override
    public Printer visitClassAnnotation(final String arg0, final boolean arg1) {
        return this;
    }

    @Override
    public void visitClassAttribute(final Attribute arg0) {
        throw new AssertionError();
    }

    @Override
    public Printer visitFieldAnnotation(final String arg0, final boolean arg1) {
        throw new AssertionError();
    }

    @Override
    public void visitFieldAttribute(final Attribute arg0) {
        throw new AssertionError();
    }

    @Override
    public Printer visitMethodAnnotation(final String arg0, final boolean arg1) {
        return this;
    }

    @Override
    public void visitMethodAttribute(final Attribute arg0) {
        throw new AssertionError();
    }

    @Override
    public Printer visitParameterAnnotation(final int arg0, final String arg1, final boolean arg2) {
        throw new AssertionError();
    }

    @Override
    public void visit(final String arg0, final Object arg1) {
        throw new AssertionError();
    }

    @Override
    public Printer visitAnnotation(final String arg0, final String arg1) {
        throw new AssertionError();
    }

    @Override
    public void visitAnnotationEnd() {
        //empty
    }

    @Override
    public Printer visitArray(final String arg0) {
        throw new AssertionError();
    }

    @Override
    public void visitEnum(final String arg0, final String arg1, final String arg2) {
        throw new AssertionError();
    }

    @Override
    public void visitInnerClass(final String arg0, final String arg1, final String arg2, final int arg3) {
        throw new AssertionError();
    }
}
