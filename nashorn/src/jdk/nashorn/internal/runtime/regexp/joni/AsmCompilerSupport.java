/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package jdk.nashorn.internal.runtime.regexp.joni;

import java.io.FileOutputStream;
import java.io.IOException;

import jdk.nashorn.internal.runtime.regexp.joni.constants.AsmConstants;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

abstract class AsmCompilerSupport extends Compiler implements Opcodes, AsmConstants {
    protected ClassWriter factory;      // matcher allocator, also bit set, code rage and string template container
    protected MethodVisitor factoryInit;// factory constructor
    protected String factoryName;

    protected ClassWriter machine;      // matcher
    protected MethodVisitor machineInit;// matcher constructor
    protected MethodVisitor match;      // actual matcher implementation (the matchAt method)
    protected String machineName;

    // we will? try to manage visitMaxs ourselves for efficiency
    protected int maxStack = 1;
    protected int maxVars = LAST_INDEX;

    // for field generation
    protected int bitsets, ranges, templates;

    // simple class name postfix scheme for now
    static int REG_NUM = 0;

    // dummy class loader for now
    private static final class DummyClassLoader extends ClassLoader {
        public Class<?> defineClass(String name, byte[] bytes) {
            return super.defineClass(name, bytes, 0, bytes.length);
        }
    };

    private static final DummyClassLoader loader = new DummyClassLoader();

    AsmCompilerSupport(Analyser analyser) {
        super(analyser);
    }

    protected final void prepareFactory() {
        factory = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        factoryName = "jdk/nashorn/internal/runtime/regexp/joni/MatcherFactory" + REG_NUM;

        factory.visit(V1_4, ACC_PUBLIC + ACC_FINAL, factoryName, null, "jdk/nashorn/internal/runtime/regexp/joni/MatcherFactory", null);

        MethodVisitor create = factory.visitMethod(ACC_SYNTHETIC, "create", "(Lorg/joni/Regex;[BII)Lorg/joni/Matcher;", null, null);
        create.visitTypeInsn(NEW, machineName);
        create.visitInsn(DUP);          // instance
        create.visitVarInsn(ALOAD, 1);  // Regex
        create.visitVarInsn(ALOAD, 2);  // bytes[]
        create.visitVarInsn(ILOAD, 3);  // p
        create.visitVarInsn(ILOAD, 4);  // end
        create.visitMethodInsn(INVOKESPECIAL, machineName, "<init>", "(Lorg/joni/Regex;[BII)V");
        create.visitInsn(ARETURN);
        create.visitMaxs(0, 0);
        //create.visitMaxs(6, 5);
        create.visitEnd();
    }

    protected final void prepareFactoryInit() {
        factoryInit = factory.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        factoryInit.visitVarInsn(ALOAD, 0);
        factoryInit.visitMethodInsn(INVOKESPECIAL, "jdk/nashorn/internal/runtime/regexp/joni/MatcherFactory", "<init>", "()V");
    }

    protected final void setupFactoryInit() {
        factoryInit.visitInsn(RETURN);
        factoryInit.visitMaxs(0, 0);
        //init.visitMaxs(1, 1);
        factoryInit.visitEnd();
    }

    protected final void prepareMachine() {
        machine = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        machineName = "jdk/nashorn/internal/runtime/regexp/joni/NativeMachine" + REG_NUM;
    }

    protected final void prepareMachineInit() {
        machine.visit(V1_4, ACC_PUBLIC + ACC_FINAL, machineName, null, "jdk/nashorn/internal/runtime/regexp/joni/NativeMachine", null);
        machineInit = machine.visitMethod(ACC_PROTECTED, "<init>", "(Lorg/joni/Regex;[BII)V", null, null);
        machineInit.visitVarInsn(ALOAD, THIS);  // this
        machineInit.visitVarInsn(ALOAD, 1);     // Regex
        machineInit.visitVarInsn(ALOAD, 2);     // bytes[]
        machineInit.visitVarInsn(ILOAD, 3);     // p
        machineInit.visitVarInsn(ILOAD, 4);     // end
        machineInit.visitMethodInsn(INVOKESPECIAL, "jdk/nashorn/internal/runtime/regexp/joni/NativeMachine", "<init>", "(Lorg/joni/Regex;[BII)V");
    }

    protected final void setupMachineInit() {
        if (bitsets + ranges + templates > 0) { // ok, some of these are in use, we'd like to cache the factory
            machine.visitField(ACC_PRIVATE + ACC_FINAL, "factory", "L" + factoryName + ";", null, null);
            machineInit.visitVarInsn(ALOAD, THIS);  // this
            machineInit.visitVarInsn(ALOAD, 1);     // this, Regex
            machineInit.visitFieldInsn(GETFIELD, "jdk/nashorn/internal/runtime/regexp/joni/Regex", "factory", "Lorg/joni/MatcherFactory;"); // this, factory
            machineInit.visitTypeInsn(CHECKCAST, factoryName);
            machineInit.visitFieldInsn(PUTFIELD, machineName, "factory", "L" + factoryName + ";"); // []
        }

        machineInit.visitInsn(RETURN);
        machineInit.visitMaxs(0, 0);
        //init.visitMaxs(5, 5);
        machineInit.visitEnd();
    }

    protected final void prepareMachineMatch() {
        match = machine.visitMethod(ACC_SYNTHETIC, "matchAt", "(III)I", null, null);
        move(S, SSTART);        // s = sstart
        load("bytes", "[B");    //
        astore(BYTES);          // byte[]bytes = this.bytes
    }

    protected final void setupMachineMatch() {
        match.visitInsn(ICONST_M1);
        match.visitInsn(IRETURN);

        match.visitMaxs(maxStack, maxVars);
        match.visitEnd();
    }

    protected final void setupClasses() {
        byte[]factoryCode = factory.toByteArray();
        byte[]machineCode = machine.toByteArray();

        if (Config.DEBUG_ASM) {
            try {
                FileOutputStream fos;
                fos = new FileOutputStream(factoryName.substring(factoryName.lastIndexOf('/') + 1) + ".class");
                fos.write(factoryCode);
                fos.close();
                fos = new FileOutputStream(machineName.substring(machineName.lastIndexOf('/') + 1) + ".class");
                fos.write(machineCode);
                fos.close();
            } catch (IOException ioe) {
                ioe.printStackTrace(Config.err);
            }
        }

        loader.defineClass(machineName.replace('/', '.'), machineCode);
        Class<?> cls = loader.defineClass(factoryName.replace('/', '.'), factoryCode);
        try {
            regex.factory = (MatcherFactory)cls.newInstance();
        } catch(Exception e) {
            e.printStackTrace(Config.err);
        }
    }

    protected final void aload(int var) {
        match.visitVarInsn(ALOAD, var);
    }

    protected final void astore(int var) {
        match.visitVarInsn(ASTORE, var);
    }

    protected final void loadThis() {
        match.visitVarInsn(ALOAD, THIS);
    }

    protected final void load(int var) {
        match.visitVarInsn(ILOAD, var);
    }

    protected final void store(int var) {
        match.visitVarInsn(ISTORE, var);
    }

    protected final void move(int to, int from) {
        load(from);
        store(to);
    }

    protected final void load(String field, String singature) {
        loadThis();
        match.visitFieldInsn(GETFIELD, machineName, field, singature);
    }

    protected final void load(String field) {
        load(field, "I");
    }

    protected final void store(String field, String singature) {
        loadThis();
        match.visitFieldInsn(PUTFIELD, machineName, field, singature);
    }

    protected final void store(String field) {
        store(field, "I");
    }

    protected final String installTemplate(char[] arr, int p, int length) {
        String templateName = TEMPLATE + ++templates;
        installArray(templateName, arr, p, length);
        return templateName;
    }

    protected final String installCodeRange(int[]arr) {
        String coreRangeName = CODERANGE + ++ranges;
        installArray(coreRangeName, arr);
        return coreRangeName;
    }

    protected final String installBitSet(int[]arr) {
        String bitsetName = BITSET + ++bitsets;
        installArray(bitsetName, arr);
        return bitsetName;
    }

    private void installArray(String name, int[]arr) {
        factory.visitField(ACC_PRIVATE + ACC_FINAL, name, "[I", null, null);
        factoryInit.visitVarInsn(ALOAD, THIS);          // this;
        loadInt(factoryInit, arr.length);               // this, length
        factoryInit.visitIntInsn(NEWARRAY, T_INT);      // this, arr
        for (int i=0;i < arr.length; i++) buildArray(i, arr[i], IASTORE);
        factoryInit.visitFieldInsn(PUTFIELD, factoryName, name, "[I");
    }

    private void installArray(String name, char[]arr, int p, int length) {
        factory.visitField(ACC_PRIVATE + ACC_FINAL, name, "[B", null, null);
        factoryInit.visitVarInsn(ALOAD, THIS);          // this;
        loadInt(factoryInit, arr.length);               // this, length
        factoryInit.visitIntInsn(NEWARRAY, T_BYTE);     // this, arr
        for (int i=p, j=0; i < p + length; i++, j++) buildArray(j, arr[i] & 0xff, BASTORE);
        factoryInit.visitFieldInsn(PUTFIELD, factoryName, name, "[B");
    }

    private void buildArray(int index, int value, int type) {
        factoryInit.visitInsn(DUP);     // ... arr, arr
        loadInt(factoryInit, index);    // ... arr, arr, index
        loadInt(factoryInit, value);    // ... arr, arr, index, value
        factoryInit.visitInsn(type);    // ... arr
    }

    private void loadInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(value + ICONST_0); // ICONST_0 == 3
        } else if (value >= 6 && value <= 127 || value >= -128 && value <= -2) {
            mv.visitIntInsn(BIPUSH, value);
        } else if (value >= 128 && value <= 32767 || value >= -32768 && value <= -129) {
            mv.visitIntInsn(SIPUSH, value);
        } else {
            mv.visitLdcInsn(new Integer(value));
        }
    }
}
