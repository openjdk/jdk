/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

package indify;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.io.*;
import java.lang.reflect.Modifier;
import java.util.function.Predicate;
import java.util.regex.*;
import java.util.stream.Collectors;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.DirectMethodHandleDesc.Kind.*;

/**
 * Transform one or more class files to incorporate JSR 292 features, such as {@code invokedynamic}.
 * <p>
 * This standalone program, contained within a single source file, is useful for test harnesses, small experiments, and Javadoc examples.
 * Copies of this file may be distributed to various locations for standalone usage. The primary maintained location of this file is:
 * <a href="http://kenai.com/projects/ninja/sources/indify-repo/content/src/indify/Indify.java">
 * http://kenai.com/projects/ninja/sources/indify-repo/content/src/indify/Indify.java</a>
 * </p>
 *
 * <p>
 * Static private methods named MH_x and MT_x (where x is arbitrary) must generate MethodHandle and MethodType constants.
 * All calls to these methods are transformed into {@code CONSTANT_MethodHandle} and {@code CONSTANT_MethodType} "ldc" instructions.
 * The code must create method types using {@code methodType} or {@code fromMethodDescriptorString}. The "lookup" argument must be
 * created using {@code java.lang.invoke.MethodHandles#lookup MethodHandles.lookup}. The class and string arguments must be constant.
 * The following methods of {@code java.lang.invoke.MethodHandle.Lookup Lookup} are allowed for method handle creation:
 * {@code findStatic}, {@code findVirtual}, {@code findConstructor}, {@code findSpecial}, {@code findGetter}, {@code findSetter},
 * {@code findStaticGetter}, or {@code findStaticSetter}. The call to one of these methods must be followed immediately by an
 * {@code areturn} instruction. Replacing calls to MH_x or MT_x methods with {@code ldc} instructions should not change the program's
 * meaning.
 * </p>
 *
 * <p>
 * Static private methods named INDY_x must generate {@code invokedynamic} call sites. All calls to them must be immediately followed
 * by {@code invokeExact} calls. These pairs of calls are transformed into {@code invokedynamic} instructions. Each INDY_x method must
 * start with a call to an MH_x method, which acts as its bootstrap method. This method must be immediately invoked (via {@code invokeGeneric})
 * on constant lookup, name, and type arguments. An object array of constants may also be appended to the {@code invokeGeneric} call.
 * This call must be cast to {@code CallSite}, and the result must be immediately followed by a call to {@code dynamicInvoker}, with the
 * resulting method handle returned.
 * </p>
 *
 * <p>
 * These actions collectively simulate the JVM's execution of an {@code invokedynamic} instruction in the unlinked state. Running this
 * code once should yield the same results as running the corresponding {@code invokedynamic} instruction. To model caching behavior,
 * an INDY_x method's code can begin with getstatic, aaload, and if_acmpne instructions to load a static method handle value and return
 * it if the value is non-null.
 * </p>
 *
 * <h3>Example usage:</h3>
 * <blockquote><pre>
 * $ JAVA_HOME=(some recent OpenJDK 7 build)
 * $ ant
 * $ $JAVA_HOME/bin/java -cp build/classes indify.Indify --overwrite --dest build/testout build/classes/indify/Example.class
 * $ $JAVA_HOME/bin/java -cp build/classes indify.Example
 * MT = (java.lang.Object)java.lang.Object
 * MH = adder(int,int)java.lang.Integer
 * adder(1,2) = 3
 * calling indy:  42
 * $ $JAVA_HOME/bin/java -cp build/testout indify.Example
 * (same output as above)
 * </pre></blockquote>
 *
 * <p>
 * A version of this transformation built on top of <a href="http://asm.ow2.org/">http://asm.ow2.org/</a> would be welcome.
 * </p>
 *
 * @author John Rose
 */

public class Indify {
    public static void main(String... av) throws IOException {
        new Indify().run(av);
    }

    /**
     * Destination file where output will be written.
     */
    public File dest;

    /**
     * Array of classpath entries, with the default being the current directory.
     */
    public String[] classpath = {"."};

    /**
     * Flag indicating whether to continue processing after encountering an error.
     * Default is {@code false}.
     */
    public boolean keepgoing = false;

    /**
     * Flag indicating whether to expand properties in input files.
     * Default is {@code false}.
     */
    public boolean expandProperties = false;

    /**
     * Flag indicating whether to overwrite existing files.
     * Default is {@code false}.
     */
    public boolean overwrite = false;

    /**
     * Flag indicating whether to suppress output messages.
     * Default is {@code false}.
     */
    public boolean quiet = false;

    /**
     * Flag indicating whether to enable verbose output.
     * Default is {@code false}.
     */
    public boolean verbose = false;

    /**
     * Flag indicating whether to process all items.
     * Default is {@code false}.
     */
    public boolean all = false;

    /**
     * Count of verify specifiers, with the default being -1 indicating no verification.
     */
    public int verifySpecifierCount = -1;

    /**
     * Processes command-line arguments to transform class files by incorporating JSR 292 features.
     * <p>
     * This method accepts various options and a list of files to be processed. If the '--java' option
     * is specified, it runs the application with the provided arguments. Otherwise, it processes
     * each file using the {@code indify} method.
     * </p>
     *
     * @param av the command-line arguments
     * @throws IOException if an I/O error occurs during file processing
     * @throws IllegalArgumentException if the arguments are invalid
     */
    public void run(String... av) throws IOException {
        List<String> avl = new ArrayList<>(Arrays.asList(av));
        parseOptions(avl);

        if (avl.isEmpty()) {
            throw new IllegalArgumentException("Usage: indify [--dest dir] [option...] file...");
        }

        if ("--java".equals(avl.get(0))) {
            avl.remove(0);
            try {
                runApplication(avl.toArray(new String[0]));
            } catch (Exception ex) {
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
            return;
        }

        Exception err = null;
        for (String a : avl) {
            try {
                indify(a);
            } catch (Exception ex) {
                if (err == null) {
                    err = ex;
                }
                System.err.println("Failure on " + a);
                if (!keepgoing) {
                    break;
                } else if (ex != err) {
                err.addSuppressed(ex);
            }
            }
        }

        if (err != null) {
            if (err instanceof IOException) {
                throw (IOException) err;
            }
            throw (RuntimeException) err;
        }
    }

    /** Execute the given application under a class loader which indifies all application classes. */
    public void runApplication(String... av) throws Exception {
        List<String> avl = new ArrayList<>(Arrays.asList(av));
        String mainClassName = avl.remove(0);
        av = avl.toArray(new String[0]);
        Class<?> mainClass = Class.forName(mainClassName, true, makeClassLoader());
        Method main = mainClass.getMethod("main", String[].class);
        try { main.setAccessible(true); } catch (SecurityException ignored) { }
        main.invoke(null, (Object) av);
    }

    public void parseOptions(List<String> av) throws IOException {
        for (; !av.isEmpty(); av.remove(0)) {
            String a = av.get(0);
            if (a.startsWith("-")) {
                String a2 = null;
                int eq = a.indexOf('=');
                if (eq > 0) {
                    a2 = maybeExpandProperties(a.substring(eq+1));
                    a = a.substring(0, eq+1);
                }
                switch (a) {
                case "--java":
                    return;
                case "-d": case "--dest": case "-d=": case "--dest=":
                    dest = new File(a2 != null ? a2 : maybeExpandProperties(av.remove(1)));
                    break;
                case "-cp": case "--classpath":
                    classpath = maybeExpandProperties(av.remove(1)).split("["+File.pathSeparatorChar+"]");
                    break;
                case "-k": case "--keepgoing": case "--keepgoing=":
                    keepgoing = booleanOption(a2);
                    break;
                case "--expand-properties": case "--expand-properties=":
                    expandProperties = booleanOption(a2);
                    break;
                case "--verify-specifier-count": case "--verify-specifier-count=":
                    verifySpecifierCount = Integer.valueOf(a2);
                    break;
                case "--overwrite": case "--overwrite=":
                    overwrite = booleanOption(a2);
                    break;
                case "--all": case "--all=":
                    all = booleanOption(a2);
                    break;
                case "-q": case "--quiet": case "--quiet=":
                    quiet = booleanOption(a2);
                    break;
                case "-v": case "--verbose": case "--verbose=":
                    verbose = booleanOption(a2);
                    break;
                default:
                    throw new IllegalArgumentException("unrecognized flag: "+a);
                }
            } else {
                break;
            }
        }
        if (dest == null && !overwrite)
            throw new RuntimeException("no output specified; need --dest d or --overwrite");
        if (expandProperties) {
            for (int i = 0; i < av.size(); i++)
                av.set(i, maybeExpandProperties(av.get(i)));
        }
    }

    private boolean booleanOption(String s) {
        if (s == null)  return true;
        return switch (s) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw new IllegalArgumentException("unrecognized boolean flag=" + s);
        };
    }

    private String maybeExpandProperties(String s) {
        if (!expandProperties)  return s;
        Set<String> propsDone = new HashSet<>();
        while (s.contains("${")) {
            int lbrk = s.indexOf("${");
            int rbrk = s.indexOf('}', lbrk);
            if (rbrk < 0)  break;
            String prop = s.substring(lbrk+2, rbrk);
            if (!propsDone.add(prop))  break;
            String value = System.getProperty(prop);
            if (verbose)  System.err.println("expanding ${"+prop+"} => "+value);
            if (value == null)  break;
            s = s.substring(0, lbrk) + value + s.substring(rbrk+1);
        }
        return s;
    }

    public void indify(String a) throws IOException {
        File f = new File(a);
        String fn = f.getName();
        if (fn.endsWith(".class") && f.isFile())
            indifyFile(f, dest);
        else if (fn.endsWith(".jar") && f.isFile())
            indifyJar(f, dest);
        else if (f.isDirectory())
            indifyTree(f, dest);
        else if (!keepgoing)
            throw new RuntimeException("unrecognized file: "+a);
    }

    private void ensureDirectory(File dir) {
        if (dir.mkdirs() && !quiet)
            System.err.println("Created new directory to: "+dir);
    }

    public void indifyFile(File f, File dest) throws IOException {
        if (verbose)  System.err.println("reading "+f);
        ClassModel model = parseClassFile(f);
        Logic logic = new Logic(model);
        ClassModel newClassModel = logic.transform();
        assert newClassModel != null;
        logic.reportPatternMethods(quiet, keepgoing);
        writeNewClassFile(newClassModel);
    }

    void writeNewClassFile(ClassModel newClassModel) throws IOException {
        byte[] new_bytes = transformToBytes(newClassModel);
        File destFile = classPathFile(dest, newClassModel.thisClass().name().stringValue());
        ensureDirectory(destFile.getParentFile());
        if (verbose)  System.err.println("writing "+destFile);
        Files.write(destFile.toPath(), new_bytes);
        System.err.println("Wrote New ClassFile to: "+destFile);
    }

    byte[] transformToBytes(ClassModel classModel) {
        return of(StackMapsOption.GENERATE_STACK_MAPS).transform(classModel, ClassTransform.ACCEPT_ALL);
    }

    File classPathFile(File pathDir, String className) {
        String qualname = className.replace('.','/')+".class";
        qualname = qualname.replace('/', File.separatorChar);
        return new File(pathDir, qualname);
    }

    public void indifyJar(File f, Object dest) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void indifyTree(File f, File dest) throws IOException {
        if (verbose)  System.err.println("reading directory: "+f);
        for (File f2 : Objects.requireNonNull(f.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (name.endsWith(".class")) return true;
                if (name.contains(".")) return false;
                // return true if it might be a package name:
                return Character.isJavaIdentifierStart(name.charAt(0));
            }
        }))) {
            if (f2.getName().endsWith(".class"))
                indifyFile(f2, dest);
            else if (f2.isDirectory())
                indifyTree(f2, dest);
        }
    }

    public ClassLoader makeClassLoader() {
        return new Loader();
    }
    private class Loader extends ClassLoader {
        Loader() {
            this(Indify.class.getClassLoader());
        }
        Loader(ClassLoader parent) {
            super(parent);
        }
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            File f = findClassInPath(name);
            if (f != null) {
                try {
                    Class<?> c = transformAndLoadClass(f);
                    if (c != null) {
                        if (resolve)  resolveClass(c);
                        return c;
                    }
                } catch (ClassNotFoundException ex) {
                    // fall through
                } catch (IOException ex) {
                    // fall through
                } catch (Exception ex) {
                    // pass error from reportPatternMethods, etc.
                    if (ex instanceof RuntimeException)  throw (RuntimeException) ex;
                    throw new RuntimeException(ex);
                }
            }
            return super.loadClass(name, resolve);
        }
        private File findClassInPath(String name) {
            for (String s : classpath) {
                File f = classPathFile(new File(s), name);
                //System.out.println("Checking for "+f);
                if (f.exists() && f.canRead()) {
                    return f;
                }
            }
            return null;
        }
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                File f = findClassInPath(name);
                if (f != null) {
                    Class<?> c = transformAndLoadClass(f);
                    if (c != null)  return c;
                }
            } catch (IOException ex) {
                throw new ClassNotFoundException("IO error", ex);
            }
            throw new ClassNotFoundException();
        }
        private Class<?> transformAndLoadClass(File f) throws ClassNotFoundException, IOException {
            if (verbose)  System.err.println("Loading class from "+f);
            ClassModel model = parseClassFile(f);
            Logic logic = new Logic(model);
            ClassModel newClassModel = logic.transform();
            if(newClassModel == null)  throw new IOException("No transformation has been done");
            logic.reportPatternMethods(!verbose, keepgoing);
            byte[] new_Bytes = transformToBytes(newClassModel);
            System.err.println("Transformed bytes: " + new_Bytes.length);

            return defineClass(null, new_Bytes, 0, new_Bytes.length);
        }
    }

    private class Logic {
        ClassModel classModel;
        ConstantPoolBuilder poolBuilder;  //Builder for the new constant pool
        final char[] poolMarks;
        final Map<MethodModel, PoolEntry> Constants = new HashMap<>();
        final Map<MethodModel, String> IndySignatures = new HashMap<>();
        Logic(ClassModel classModel){
            this.classModel = classModel;
            poolBuilder = ConstantPoolBuilder.of(classModel);
            poolMarks = new char[classModel.constantPool().size()];
        }

        ClassModel transform(){
            if (!initializeMarks())  return null;
            if (!findPatternMethods()) return null;

            ClassModel newClassModel = transformFromCPBuilder(classModel, poolBuilder);
            CodeTransform codeTransform;
            ClassTransform classTransform;

            for(MethodModel m : classModel.methods()){
                if(Constants.containsKey(m)) continue;  //skip if pattern method, it will be removed

                Predicate<MethodModel> filter = method -> Objects.equals(method.methodName().stringValue(), m.methodName().stringValue());

                List<Instruction> instructionList = getInstructions(m);
                ListIterator<Instruction> iterator =instructionList.listIterator();
                final Stack<Boolean> shouldProceed = new Stack<>();

                while (iterator.hasNext()){
                    shouldProceed.push(true);
                    Instruction i = iterator.next();

                    if(i.opcode().bytecode() != INVOKESTATIC) continue;  //this is not an invokestatic instruction
                    int methi = ((InvokeInstruction) i).method().index();
                    if (poolMarks[methi] == 0) continue;    //Skip if marked as a pattern Method

                    MemberRefEntry ref = (MemberRefEntry) classModel.constantPool().entryByIndex(methi);
                    String methName = ref.nameAndType().name().stringValue();
                    String methType = ref.nameAndType().type().stringValue();

                    MethodModel conm = null;
                    for (MethodModel mm : classModel.methods()) {
                        if (mm.methodName().stringValue().equals(methName) && mm.methodType().stringValue().equals(methType)) {
                            conm = mm;
                        }
                    }
                    if(conm == null) continue;

                    PoolEntry con = Constants.get(conm);
                    if(quiet){
                        System.out.println();
                        System.err.println("$$$$$$$$$$$$$$$----------------------------------------------------------------Patching Method: " +  m.methodName() + "------------------------------------------------------------------");
                    }

                    if(con instanceof InvokeDynamicEntry){
                        Instruction i2 = findPop(instructionList, iterator.previousIndex());
                        int ref2i;
                        MethodRefEntry methodEntry = null;

                        if(i2 != null && i2.opcode().bytecode() == INVOKEVIRTUAL && poolMarks[ref2i = ((InvokeInstruction) i2).method().index()] == 'D'){
                            methodEntry = (MethodRefEntry) newClassModel.constantPool().entryByIndex(ref2i);
                        }

                        if(methodEntry == null || !"invokeExact".equals(methodEntry.nameAndType().name().stringValue())){
                            System.err.println(m+": Failed to create invokedynamic at "+i.opcode().bytecode());
                            continue;
                        }

                        String invType = methodEntry.type().stringValue();
                        String bsmType = IndySignatures.get(conm);
                        if (!invType.equals(bsmType)) {
                            System.err.println(m+": warning: "+conm+" call type and local invoke type differ: " + bsmType+", " + invType);
                        }

                        assert (i.sizeInBytes() == 3 || i2.sizeInBytes() == 3);
                        System.err.println("----------------------------------------------------------------Transforming Method INDY Instructions & Creating New ClassModels------------------------------------------------------------------}}}");
                        if (!quiet) System.err.println(":::Transfmoring the Method: "+ m.methodName() +" instruction: " + i + " invokedynamic: " + con.index() );
                        MethodModel finalConm = conm;
                        codeTransform = (b, e) ->{
                            String a1 = null, a2 = null;
                            if(e instanceof InvokeInstruction){
                                a1 = ((InvokeInstruction) e).method().name().stringValue();
                                a2 = finalConm.methodName().stringValue();
                            }
                            if (e instanceof InvokeInstruction && Objects.equals(a1, a2)) {
                                System.err.println(">> Removing instruction invokestatic for Method: " + ((InvokeInstruction) e).name());
                                b.andThen(b);
                            } else if (shouldProceed.peek() && e instanceof InvokeInstruction && ((InvokeInstruction) e).method().equals(((InvokeInstruction) i2).method())) {
                                System.err.println(">> Removing instruction invokevirtual for Method: " + ((InvokeInstruction) e).name());
                                b.andThen(b);
                                System.out.println(">> Adding invokedynamic instruction and nop instead of invoke virtual: " + ((InvokeDynamicEntry) con).name());
                                b.invokeDynamicInstruction((InvokeDynamicEntry) con).nop();

                                shouldProceed.pop();
                                shouldProceed.push(false);
                            } else {
                                b.with(e);
                            }
                        };
                        classTransform = ClassTransform.transformingMethodBodies(filter, codeTransform);

                        newClassModel = of(StackMapsOption.GENERATE_STACK_MAPS).parse(
                               of(StackMapsOption.GENERATE_STACK_MAPS).transform(newClassModel, classTransform)
                        );

                        System.out.println();
                    } else {
                        assert(i.sizeInBytes() == 3);
                        System.err.println("----------------------------------------------------------------Transforming Method LDC Instructions & Creating New ClassModels------------------------------------------------------------------}}}");
                        MethodModel finalConm = conm;
                        codeTransform = (b, e) ->{
                            String a1 = null, a2 = null;
                            if(e instanceof InvokeInstruction){
                                a1 = ((InvokeInstruction) e).method().name().stringValue();
                                a2 = finalConm.methodName().stringValue();
                            }
                            if(e instanceof InvokeInstruction && Objects.equals(a1, a2)){
                                System.err.println(":::Transfmoring the Method: "+ m.methodName() +" instruction: invokestatic " + ((InvokeInstruction) e).type() + " to ldc: " + ((LoadableConstantEntry) con).index() );
                                b.constantInstruction(Opcode.LDC_W,  ((LoadableConstantEntry) con).constantValue());
                            } else b.with(e);
                        };
                        classTransform = ClassTransform.transformingMethodBodies(filter, codeTransform);
                        newClassModel = of(StackMapsOption.GENERATE_STACK_MAPS).parse(
                             of(StackMapsOption.GENERATE_STACK_MAPS).transform(newClassModel, classTransform));
                    }
                    shouldProceed.pop();
                }
            }
            newClassModel = removePatternMethodsAndVerify(newClassModel);

            return newClassModel;
        }

        ClassModel removePatternMethodsAndVerify(ClassModel classModel){

            ClassModel newClassModel = of(StackMapsOption.GENERATE_STACK_MAPS).parse(
                    of(StackMapsOption.GENERATE_STACK_MAPS).transform(classModel, (b, e) ->
                    {
                        if (!(e instanceof MethodModel mm &&
                                (mm.methodName().stringValue().startsWith("MH_") ||
                                        mm.methodName().stringValue().startsWith("MT_") ||
                                        mm.methodName().stringValue().startsWith("INDY_"))
                        )) b.with(e);
                        else System.err.println("Removing pattern method: " + ((MethodModel) e).methodName());
                    })
            );
            ClassHierarchyResolver classHierarchyResolver = classDesc -> ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();

            try {
                List<VerifyError> errors = of(StackMapsOption.GENERATE_STACK_MAPS,ClassHierarchyResolverOption.of(classHierarchyResolver)).verify(newClassModel);
                if (!errors.isEmpty()) {
                    for (VerifyError e : errors) {
                        System.err.println(e.getMessage());
                    }
                    throw new IOException("Verification failed");
                } else System.out.println("Verification passed");} catch (IOException ignored) {

            }

            return newClassModel;
        }

        Instruction findPop( List<Instruction> instructionList, int currentIndex){
            JVMState jvm = new JVMState();

            ListIterator<Instruction> newIter = instructionList.listIterator(currentIndex + 1);
        decode:
            while (newIter.hasNext()) {
                Instruction i = newIter.next();
                String pops = INSTRUCTION_POPS[i.opcode().bytecode()];

                if(pops == null) break;
                if (jvm.stackMotion(i.opcode().bytecode()))  continue decode;
                if (pops.indexOf('Q') >= 0 && i instanceof InvokeInstruction in) {
                    MemberRefEntry ref = (MemberRefEntry) classModel.constantPool().entryByIndex(in.method().index());
                    String methType = ref.nameAndType().type().stringValue();
                    String type = simplifyType(methType);

                    switch (i.opcode().bytecode()){
                        case GETSTATIC:
                        case GETFIELD:
                        case PUTSTATIC:
                        case PUTFIELD:
                            pops = pops.replace("Q", type);
                            break;
                        default:
                            if (!type.startsWith("("))
                                throw new InternalError(i.toString());
                            pops = pops.replace("Q$Q", type.substring(1).replace(")","$"));
                            break;
                    }
                    System.out.println("special type: "+type+" => "+pops);
                }
                int npops = pops.indexOf('$');
                if (npops < 0)  throw new InternalError();
                if (npops > jvm.sp())  return i;
                List<Object> args = jvm.args(npops);
                int k = 0;
                for (Object x : args) {
                    char have = (Character) x;
                    char want = pops.charAt(k++);
                    if (have == 'X' || want == 'X')  continue;
                    if (have != want)  break decode;
                }
                if (pops.charAt(k++) != '$')  break decode;
                args.clear();
                while (k < pops.length())
                    args.add(pops.charAt(k++));
            }
            System.err.println("*** bailout on jvm: "+jvm.stack);
            return null;
        }

         boolean findPatternMethods() {
            boolean found = false;
            for(char mark : "THI".toCharArray()) {
                for(MethodModel m : classModel.methods()){
                    if (!Modifier.isPrivate(m.flags().flagsMask())) continue;
                    if (!Modifier.isStatic(m.flags().flagsMask())) continue;
                    if(nameAndTypeMark(m.methodName().index(), m.methodType().index()) == mark) {
                        PoolEntry entry = scanPattern(m, mark);
                        if (entry == null) continue;
                        Constants.put(m, entry);
                        found = true;
                    }
                }
            }
            return found;
        }

        ClassModel transformFromCPBuilder(ClassModel oldClassModel, ConstantPoolBuilder cpBuilder){
            byte[] new_bytes = of(StackMapsOption.GENERATE_STACK_MAPS).transform(oldClassModel, ClassTransform.endHandler(clb -> {
                for (PoolEntry entry: cpBuilder) {
                    if (entry instanceof Utf8Entry utf8Entry) {
                        clb.constantPool().utf8Entry(utf8Entry.stringValue());
                        continue;
                    }
                    if (entry instanceof NameAndTypeEntry nameAndTypeEntry) {
                        clb.constantPool().nameAndTypeEntry(nameAndTypeEntry.name(), nameAndTypeEntry.type());
                        continue;
                    }
                    if (entry instanceof MethodTypeEntry methodTypeEntry) {
                        clb.constantPool().methodTypeEntry(methodTypeEntry.descriptor());
                        continue;
                    }
                    if (entry instanceof MethodHandleEntry methodHandleEntry) {
                        clb.constantPool().methodHandleEntry(methodHandleEntry.kind(), methodHandleEntry.reference());
                        continue;
                    }
                    if (entry instanceof MethodRefEntry methodRefEntry) {
                        clb.constantPool().methodRefEntry(methodRefEntry.owner(), methodRefEntry.nameAndType());
                        continue;
                    }
                    if (entry instanceof FieldRefEntry fieldRefEntry) {
                        clb.constantPool().fieldRefEntry(fieldRefEntry.owner(), fieldRefEntry.nameAndType());
                        continue;
                    }
                    if (entry instanceof ClassEntry classEntry) {
                        clb.constantPool().classEntry(classEntry.name());
                        continue;
                    }
                    if (entry instanceof StringEntry stringEntry) {
                        clb.constantPool().stringEntry(stringEntry.utf8());
                        continue;
                    }
                    if (entry instanceof IntegerEntry integerEntry) {
                        clb.constantPool().intEntry(integerEntry.intValue());
                        continue;
                    }
                    if (entry instanceof FloatEntry floatEntry) {
                        clb.constantPool().floatEntry(floatEntry.floatValue());
                        continue;
                    }
                    if (entry instanceof LongEntry longEntry) {
                        clb.constantPool().longEntry(longEntry.longValue());
                        continue;
                    }
                    if (entry instanceof DoubleEntry doubleEntry) {
                        clb.constantPool().doubleEntry(doubleEntry.doubleValue());
                        continue;
                    }
                    if (entry instanceof InterfaceMethodRefEntry interfaceMethodRefEntry) {
                        clb.constantPool().interfaceMethodRefEntry(interfaceMethodRefEntry.owner(), interfaceMethodRefEntry.nameAndType());
                        continue;
                    }
                    if (entry instanceof InvokeDynamicEntry invokeDynamicEntry) {
                        clb.constantPool().invokeDynamicEntry(invokeDynamicEntry.bootstrap(), invokeDynamicEntry.nameAndType());
                        continue;
                    }
                    if (entry instanceof ModuleEntry moduleEntry) {
                        clb.constantPool().moduleEntry(moduleEntry.name());
                        continue;
                    }
                    if (entry instanceof PackageEntry packageEntry) {
                        clb.constantPool().packageEntry(packageEntry.name());
                    }
                }

                for (int i = 0; i < cpBuilder.bootstrapMethodCount(); i++) {
                    clb.constantPool().bsmEntry(cpBuilder.bootstrapMethodEntry(i).bootstrapMethod(), cpBuilder.bootstrapMethodEntry(i).arguments());
                }
            }));

            return of(StackMapsOption.GENERATE_STACK_MAPS).parse(new_bytes);
        }

        void reportPatternMethods(boolean quietly, boolean allowMatchFailure) {
            if (!quietly && !Constants.keySet().isEmpty())
                System.err.println("pattern methods removed: "+Constants.keySet());
            for (MethodModel m : classModel.methods()) {
                if (nameMark(m.methodName().stringValue()) != 0 &&
                        Constants.get(m) == null) {
                    String failure = "method has a special name but fails to match pattern: "+ m.methodName();
                    if (!allowMatchFailure)
                        throw new IllegalArgumentException(failure);
                    else if (!quietly)
                        System.err.println("warning: "+failure);
                }
            }
            if (!quiet)  System.err.flush();
        }

        /**
         * Initializes the marks for the constant pool entries.
         * <p>
         * This method iterates through the constant pool and assigns marks to each entry
         * based on its type and value. These marks are used to identify specific types of
         * constant pool entries .
         * <p>
         * The method iterates until no changes are made to the pool marks array in a complete pass.
         * This ensures that all dependent entries are processed correctly.
         *
         * @return true if any marks were changed, false otherwise.
         */
        boolean initializeMarks() {
            boolean anyMarksChanged = false;
            for (;;) {
                boolean someMarksChangedInLoop = false;
                for (PoolEntry poolEntry : classModel.constantPool()) {
                    // Get the index directly from PoolEntry
                    if (poolEntry == null) {
                        continue; // Skip null entries
                    }
                    int cpIndex = poolEntry.index();

                    char mark = poolMarks[cpIndex];
                    if (mark != 0) {
                        continue;
                    }

                    switch (poolEntry.tag()) {
                        case TAG_UTF8:
                            mark = nameMark(((Utf8Entry) poolEntry).stringValue());
                            break;
                        case TAG_NAMEANDTYPE:
                            NameAndTypeEntry nameAndTypeEntry = (NameAndTypeEntry) poolEntry;
                            int ref1 = nameAndTypeEntry.name().index();
                            int ref2 = nameAndTypeEntry.type().index();
                            mark = nameAndTypeMark(ref1, ref2);
                            break;
                        case TAG_CLASS: {
                            int nameIndex = ((ClassEntry) poolEntry).name().index();
                            char nameMark = poolMarks[nameIndex];
                            if ("DJ".indexOf(nameMark) >= 0) {
                                mark = nameMark;
                            }
                            break;
                        }
                        case TAG_FIELDREF:
                        case TAG_METHODREF: {
                            MemberRefEntry memberRefEntry = (MemberRefEntry) poolEntry;
                            int classIndex = memberRefEntry.owner().index();
                            int nameAndTypeIndex = memberRefEntry.nameAndType().index();
                            char classMark = poolMarks[classIndex];
                            if (classMark != 0) {
                                mark = classMark;  // java.lang.invoke.* or java.lang.* method
                                break;
                            }
                            String cls = (classModel.constantPool().entryByIndex(classIndex) instanceof ClassEntry) ?
                                    ((ClassEntry) classModel.constantPool().entryByIndex(classIndex)).name().stringValue() : "";
                            if (cls.equals(classModel.thisClass().name().stringValue())) {
                                mark = switch (poolMarks[nameAndTypeIndex]) {
                                    case 'T', 'H', 'I' -> poolMarks[nameAndTypeIndex];
                                    default -> mark;
                                };
                            }
                            break;
                        }
                        default:
                            break;
                    }

                    if (mark != 0) {
                        poolMarks[cpIndex] = mark;
                        someMarksChangedInLoop = true;
                    }
                }
                if (!someMarksChangedInLoop) {
                    break;
                }
                anyMarksChanged = true;
            }
            return anyMarksChanged;
        }

        char nameMark(String s) {
            if (s.startsWith("MT_"))                return 'T';
            else if (s.startsWith("MH_"))           return 'H';
            else if (s.startsWith("INDY_"))         return 'I';
            else if (s.startsWith("java/lang/invoke/"))  return 'D';
            else if (s.startsWith("java/lang/"))    return 'J';
            return 0;
        }

        char nameAndTypeMark(int ref1, int ref2){
            char mark = poolMarks[ref1];
            if (mark == 0) return 0;
            String descriptor = (classModel.constantPool().entryByIndex(ref2) instanceof Utf8Entry) ? ((Utf8Entry) classModel.constantPool().entryByIndex(ref2)).stringValue() : "";
            String requiredType;
            switch (poolMarks[ref1]){
                case 'H', 'I': requiredType = "()Ljava/lang/invoke/MethodHandle;";  break;
                case 'T': requiredType = "()Ljava/lang/invoke/MethodType;";    break;
                default:  return 0;
            }
            if(matchType(descriptor, requiredType)) return mark;
            return 0;
        }

        boolean matchType(String descr, String requiredType) {
            if (descr.equals(requiredType))  return true;
            return false;
        }

        private class JVMState {
            final List<Object> stack = new ArrayList<>();
            int sp() { return stack.size(); }
            void push(Object x) { stack.add(x); }
            void push2(Object x) { stack.add(EMPTY_SLOT); stack.add(x); }
            void pushAt(int pos, Object x) { stack.add(stack.size()+pos, x); }
            Object pop() { return stack.remove(sp()-1); }
            Object top() { return stack.get(sp()-1); }
            List<Object> args(boolean hasRecv, String type) {
                return args(argsize(type) + (hasRecv ? 1 : 0));
            }
            List<Object> args(int argsize) {
                return stack.subList(sp()-argsize, sp());
            }
            boolean stackMotion(int bc) {
                switch (bc) {
                case POP:    pop();             break;
                case POP2:   pop(); pop();      break;
                case SWAP:   pushAt(-1, pop()); break;
                case DUP:    push(top());       break;
                case DUP_X1: pushAt(-2, top()); break;
                case DUP_X2: pushAt(-3, top()); break;
                // ? also: dup2{,_x1,_x2}
                default:  return false;
                }
                return true;
            }
        }
        private final String EMPTY_SLOT = "_";

        private void removeEmptyJVMSlots(List<Object> args) {
            for (; ; ) {
                int i = args.indexOf(EMPTY_SLOT);
                if (i >= 0 && i + 1 < args.size()
                        && (args.get(i + 1) instanceof LongEntry ||
                        args.get(i + 1) instanceof DoubleEntry))
                    args.remove(i);
                else break;
            }
        }

        private List<Instruction> getInstructions(MethodModel method) {
            return method.code().get().elementStream()
                    .filter(Instruction.class::isInstance)
                    .map(Instruction.class::cast)
                    .collect(Collectors.toList());
        }

        private PoolEntry scanPattern(MethodModel method, char patternMark) {
            if(verbose) System.err.println("Scanning the method: " + method.methodName().stringValue() + "for the pattern mark: " + patternMark);
            int wantedTag = switch (patternMark) {
                case 'T' -> TAG_METHODTYPE;
                case 'H' -> TAG_METHODHANDLE;
                case 'I' -> TAG_INVOKEDYNAMIC;
                default -> throw new InternalError();
            };
            List<Instruction> instructions = getInstructions(method);
            JVMState jvm = new JVMState();
            ConstantPool pool = classModel.constantPool();
            int branchCount = 0;
            Object arg;
            List<Object> args;
            List<Object> bsmArgs = null;  // args for invokeGeneric
        decode:
            for(Instruction instruction : instructions){

                int bc = instruction.opcode().bytecode();
                switch (bc){
                    case LDC,LDC_W:           jvm.push(((ConstantInstruction.LoadConstantInstruction) instruction).constantEntry()); break;
                    case LDC2_W:              jvm.push2(((ConstantInstruction.LoadConstantInstruction) instruction).constantEntry()); break;
                    case ACONST_NULL:         jvm.push(null); break;
                    case BIPUSH, SIPUSH:      jvm.push(((ConstantInstruction) instruction).constantValue()); break;

                    case ANEWARRAY :
                        arg = jvm.pop();
                        if( !(arg instanceof Integer)) break decode;
                        arg = Arrays.asList(new Object[(Integer)arg]);
                        jvm.push(arg);
                        break;
                    case DUP:
                        jvm.push(jvm.top()); break;
                    case AASTORE:
                        args = jvm.args(3);  // array, index, value
                        if (args.get(0) instanceof List &&
                                args.get(1) instanceof Integer) {
                            @SuppressWarnings("unchecked")
                            List<Object> arg0 = (List<Object>)args.get(0);
                            arg0.set( (Integer)args.get(1), args.get(2) );
                        }
                        args.clear();
                        break;
                    case NEW:
                        String type = ((NewObjectInstruction) instruction).className().name().stringValue();
                        switch (type) {
                            case "java/lang/StringBuilder":
                                jvm.push("StringBuilder");
                                continue decode;
                        }
                        break decode;
                    case GETSTATIC:
                    {
                        int fieldId = ((FieldInstruction) instruction).field().index();
                        char mark = poolMarks[fieldId];
                        if (mark == 'J') {
                            int classIndex = ((FieldInstruction) instruction).field().owner().index();
                            int nameIndex = ((FieldInstruction) instruction).field().name().index();
                            String name = ((Utf8Entry) classModel.constantPool().entryByIndex(nameIndex)).stringValue();
                            if ("TYPE".equals(name)) {
                                String wrapperName = ((ClassEntry) pool.entryByIndex(classIndex)).name().stringValue().replace('/', '.');
                                //Primitive type descriptor
                                Class<?> primClass;
                                try {
                                    primClass = (Class<?>) Class.forName(wrapperName).getField(name).get(null);
                                } catch (Exception e) {
                                    throw new InternalError("cannot load " + wrapperName + "." + name);
                                }
                                jvm.push(primClass);
                                break;
                            }
                        }
                        //Unknown Field; keep going
                        jvm.push(UNKNOWN_CON);
                        break;
                    }
                    case PUTSTATIC:
                    {
                        if (patternMark != 'I') break decode;
                        jvm.pop();
                        //Unknown Field; keep going
                        break;
                    }

                    case INVOKESTATIC:
                    case INVOKEVIRTUAL:
                    case INVOKESPECIAL:
                    {
                        boolean hasReceiver = (bc != INVOKESTATIC);
                        int methodIndex = ((InvokeInstruction) instruction).method().index();
                        char mark = poolMarks[methodIndex];
                        MemberRefEntry ref = (MemberRefEntry) classModel.constantPool().entryByIndex(methodIndex);
                        String methClass = ref.owner().name().stringValue();
                        String methType = ref.nameAndType().type().stringValue();
                        String methName = ref.nameAndType().name().stringValue();
                        System.out.println("invoke " + methName + " : " + ref + " : " + methType);
                        args = jvm.args(hasReceiver, methType);
                        String intrinsic = null;
                        PoolEntry con;
                        if (mark == 'D' || mark == 'J') {
                            intrinsic = methName;
                            if (mark == 'J') {
                                String cls = methClass;
                                cls = cls.substring(1 + cls.lastIndexOf('/'));
                                intrinsic = cls + "." + intrinsic;
                            }
                            System.out.println("recognized intrinsic " + intrinsic);
                            byte refKind = -1;
                            switch (intrinsic) {
                                case "findGetter":          refKind = (byte) GETTER.refKind;            break;
                                case "findStaticGetter":    refKind = (byte) STATIC_GETTER.refKind;     break;
                                case "findSetter":          refKind = (byte) SETTER.refKind;            break;
                                case "findStaticSetter":    refKind = (byte) STATIC_SETTER.refKind;     break;
                                case "findVirtual":         refKind = (byte) VIRTUAL.refKind;           break;
                                case "findStatic":          refKind = (byte) STATIC.refKind;            break;
                                case "findSpecial":         refKind = (byte) SPECIAL.refKind;           break;
                                case "findConstructor":     refKind = (byte) CONSTRUCTOR.refKind;       break;
                            }
                            if (refKind >= 0 && (con = parseMemberLookup(refKind, args)) != null) {
                                args.clear(); args.add(con);
                                continue;
                            }
                        }
                        MethodModel ownMethod = null;
                        if (mark == 'T' || mark == 'H' || mark == 'I') {
                            for (MethodModel m : classModel.methods()) {
                                if (m.methodName().stringValue().equals(methName) && m.methodType().stringValue().equals(methType)) {
                                    ownMethod = m;
                                }
                            }
                        }
                        switch (intrinsic == null ? "" : intrinsic) {
                            case "fromMethodDescriptorString":
                                con = makeMethodTypeCon(args.get(0));
                                args.clear(); args.add(con);
                                continue;
                            case "methodType": {
                                flattenVarargs(args);
                                StringBuilder buf = new StringBuilder();
                                String rtype = null;
                                for(Object typeArg : args) {
                                    if (typeArg instanceof Class) {
                                        Class<?> argClass = (Class<?>) typeArg;
                                        if (argClass.isPrimitive()) {
                                            char tchar;
                                            switch (argClass.getName()) {
                                                case "void":    tchar = 'V'; break;
                                                case "boolean": tchar = 'Z'; break;
                                                case "byte":    tchar = 'B'; break;
                                                case "char":    tchar = 'C'; break;
                                                case "short":   tchar = 'S'; break;
                                                case "int":     tchar = 'I'; break;
                                                case "long":    tchar = 'J'; break;
                                                case "float":   tchar = 'F'; break;
                                                case "double":  tchar = 'D'; break;
                                                default:  throw new InternalError(argClass.toString());
                                            }
                                            buf.append(tchar);
                                        } else {
                                            buf.append('L').append(argClass.getName().replace('.','/')).append(';');
                                        }
                                    } else if (typeArg instanceof PoolEntry) {
                                        PoolEntry argCon = (PoolEntry) typeArg;
                                        if(argCon.tag() == TAG_CLASS) {
                                            String cn = ((ClassEntry) argCon).name().stringValue();
                                            if (cn.endsWith(";"))
                                                buf.append(cn);
                                            else
                                                buf.append('L').append(cn).append(';');
                                        } else {
                                            break decode;
                                        }
                                    } else {
                                        break decode;
                                    }
                                    if (rtype == null) {
                                        // first arg is treated differently
                                        rtype = buf.toString();
                                        buf.setLength(0);
                                        buf.append('(');
                                    }
                                }
                                buf.append(')').append(rtype);
                                con = con = makeMethodTypeCon(buf.toString());
                                args.clear(); args.add(con);
                                continue;
                            }
                            case "lookup":
                            case "dynamicInvoker":
                                args.clear(); args.add(intrinsic);
                                continue;
                            case "lookupClass":
                                if(args.equals(Arrays.asList("lookup"))) {
                                    args.clear(); args.add(classModel.thisClass());
                                    continue;
                                }
                                break;
                            case "invoke":
                            case "invokeGeneric":
                            case "invokeWithArguments":
                                if (patternMark != 'I')  break decode;
                                if ("invokeWithArguments".equals(intrinsic))
                                    flattenVarargs(args);
                                bsmArgs = new ArrayList<>(args);
                                args.clear(); args.add("invokeGeneric");
                                continue;
                            case "Integer.valueOf":
                            case "Float.valueOf":
                            case "Long.valueOf":
                            case "Double.valueOf":
                                removeEmptyJVMSlots(args);
                                if(args.size() == 1 ) {
                                    arg = args.remove(0);
                                    if (arg instanceof Number) {
                                        args.add(arg); continue;
                                    }
                                }
                                break decode;
                            case "StringBuilder.append":
                                removeEmptyJVMSlots(args);
                                args.subList(1, args.size()).clear();
                                continue;
                            case "StringBuilder.toString":
                                args.clear();
                                args.add(intrinsic);
                                continue;
                        }
                        if(!hasReceiver && ownMethod != null && patternMark != 0) {
                            con = Constants.get(ownMethod);
                            if (con == null)  break decode;
                            args.clear(); args.add(con);
                            continue;
                        } else if (methType.endsWith(")V")) {
                            args.clear();
                            continue;
                        }
                        break decode;
                    }
                    case ARETURN:
                    {
                        ++branchCount;
                        if(bsmArgs != null){
                            // parse bsmArgs as (MH, lookup, String, MT, [extra])
                            PoolEntry indyCon = makeInvokeDynamicCon(bsmArgs);
                            if (indyCon != null) {
                                PoolEntry typeCon = (PoolEntry) bsmArgs.get(3);
                                IndySignatures.put(method, ((MethodTypeEntry) typeCon).descriptor().stringValue());
                                return indyCon;
                            }
                            System.err.println(method+": inscrutable bsm arguments: "+bsmArgs);
                            break decode;
                        }
                        arg = jvm.pop();
                        if(branchCount == 2 && UNKNOWN_CON.equals(arg))
                            break;
                        if((arg instanceof PoolEntry) && ((PoolEntry) arg).tag() == wantedTag)
                            return (PoolEntry) arg;
                        break decode;
                    }
                    default:
                        if(jvm.stackMotion(instruction.opcode().bytecode())) break;
                        if (bc >= ICONST_M1 && bc <= DCONST_1)
                        { jvm.push(INSTRUCTION_CONSTANTS[bc - ICONST_M1]); break; }
                        if (patternMark == 'I') {
                            if (bc == ALOAD || bc >= ALOAD_0 && bc <= ALOAD_3)
                            { jvm.push(UNKNOWN_CON); break; }
                            if (bc == ASTORE || bc >= ASTORE_0 && bc <= ASTORE_3)
                            { jvm.pop(); break; }
                            switch (bc) {
                                case GETFIELD:
                                case AALOAD:
                                    jvm.push(UNKNOWN_CON); break;
                                case IFNULL:
                                case IFNONNULL:
                                    // ignore branch target
                                    if (++branchCount != 1)  break decode;
                                    jvm.pop();
                                    break;
                                case CHECKCAST:
                                    arg = jvm.top();
                                    if ("invokeWithArguments".equals(arg) ||
                                            "invokeGeneric".equals(arg))
                                        break;
                                    break decode;
                                default:
                                    break decode;  // bail out
                            }
                            continue decode;
                        }
                        break decode;  // bail out
                }
            }
            System.err.println(method+": bailout ==> jvm stack: "+jvm.stack);
            return null;
        }
        private final String UNKNOWN_CON = "<unknown>";

        private void flattenVarargs(List<Object> args) {
            int size = args.size();
            if (size > 0 && args.get(size - 1) instanceof List) {
                List<?> removedArg = (List<?>) args.remove(size - 1);
                args.addAll(removedArg);
            }
        }

        private PoolEntry makeMethodTypeCon(Object x){
            Utf8Entry utf8Entry;

            if (x instanceof String) {
                utf8Entry = poolBuilder.utf8Entry((String) x);
            } else if (x instanceof PoolEntry && ((PoolEntry) x).tag() == TAG_STRING) {
                utf8Entry = ((StringEntry) x).utf8();
            } else {
                return null;
            }

            return poolBuilder.methodTypeEntry(utf8Entry);
        }

        private PoolEntry parseMemberLookup(byte refKind, List<Object> args){
            //E.g.: lookup().findStatic(Foo.class, "name", MethodType)
            if(args.size() != 4) return null;
            int argi = 0;
            if(!"lookup".equals(args.get(argi++))) return null;

            NameAndTypeEntry nt;
            Utf8Entry name, type;
            ClassEntry cl;
            Object con;

            if(!((con = args.get(argi++)) instanceof ClassEntry)) return null;
            cl = (ClassEntry) con;

            if(!((con = args.get(argi++)) instanceof StringEntry)) return null;
            name = ((StringEntry) con).utf8();

            if(((con = args.get(argi++)) instanceof MethodTypeEntry) || (con instanceof ClassEntry)){
                assert con instanceof MethodTypeEntry;
                type = ((MethodTypeEntry) con).descriptor();
            } else return null;

            nt = poolBuilder.nameAndTypeEntry(name,type);

            MemberRefEntry ref;
            if(refKind <= (byte) STATIC_SETTER.refKind){
                 ref = poolBuilder.fieldRefEntry(cl, nt);
                return poolBuilder.methodHandleEntry(refKind, ref);
            }
            else if(refKind == (byte) INTERFACE_VIRTUAL.refKind){
                ref = poolBuilder.interfaceMethodRefEntry(cl, nt);
                return poolBuilder.methodHandleEntry(refKind, ref);
            }
            else{
                ref = poolBuilder.methodRefEntry(cl, nt);
            }
            return poolBuilder.methodHandleEntry(refKind, ref);
        }

        private PoolEntry makeInvokeDynamicCon(List<Object> args) {
            // E.g.: MH_bsm.invokeGeneric(lookup(), "name", MethodType, "extraArg")
            removeEmptyJVMSlots(args);
            if(args.size() < 4) return null;
            int argi = 0;
            Object con;
            Utf8Entry name, type;
            NameAndTypeEntry nt;
            MethodHandleEntry bsm;

            if (!((con = args.get(argi++)) instanceof MethodHandleEntry)) return null;
            bsm = ((MethodHandleEntry) con);

            if (!"lookup".equals(args.get(argi++)))  return null;
            if (!((con = args.get(argi++)) instanceof StringEntry)) return null;
            name = ((StringEntry) con).utf8();

            if (!((con = args.get(argi++)) instanceof MethodTypeEntry)) return null;
            type = ((MethodTypeEntry) con).descriptor();

            nt = poolBuilder.nameAndTypeEntry(name, type);

            List<Object> extraArgs = new ArrayList<Object>();
            if (argi < args.size()) {
                extraArgs.addAll(args.subList(argi, args.size() - 1));
                Object lastArg = args.get(args.size() - 1);
                if (lastArg instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> lastArgs = (List<Object>) lastArg;
                    removeEmptyJVMSlots(lastArgs);
                    extraArgs.addAll(lastArgs);
                } else {
                    extraArgs.add(lastArg);
                }
            }
            List<LoadableConstantEntry> extraArgConstants = new ArrayList<>();
            for (Object x : extraArgs) {
                if (x instanceof Number) {
                    if (x instanceof Integer) { x = poolBuilder.intEntry((Integer) x); }
                    if (x instanceof Float)   { x = poolBuilder.floatEntry((Float) x); }
                    if (x instanceof Long)    { x = poolBuilder.longEntry((Long) x); }
                    if (x instanceof Double)  { x = poolBuilder.doubleEntry((Double) x); }
                }
                if (!(x instanceof PoolEntry)) {
                    System.err.println("warning: unrecognized BSM argument "+x);
                    return null;
                }
                assert x instanceof LoadableConstantEntry;
                extraArgConstants.add(((LoadableConstantEntry) x));
            }

            List<Object[]> specs = bootstrap_MethodSpecifiers();
            int specIndex = -1;
            Object[] spec = new Object[]{ bsm.index(), extraArgConstants };
            for (Object[] spec1 : specs) {
                if (Arrays.equals(spec1, spec)) {
                    specIndex = specs.indexOf(spec1);
                    if (verbose)  System.err.println("reusing BSM specifier: "+spec1[0]+spec1[1]);
                    break;
                }
            }
            if (specIndex == -1) {
                specs.add(spec);
                if (verbose)  System.err.println("adding BSM specifier: "+spec[0]+spec[1]);
            }

            BootstrapMethodEntry bsmEntry = poolBuilder.bsmEntry(bsm, extraArgConstants);
            return poolBuilder.invokeDynamicEntry(bsmEntry, nt);
        }

        List<Object[]> bootstrap_MethodSpecifiers() {
            List<Object[]> specs = new ArrayList<>();
            int count = classModel.constantPool().bootstrapMethodCount();
            if (count == 0){
                poolBuilder.utf8Entry("BootstrapMethods");
                return specs;
            }

            for (int i = 0; i < count; i++) {
                int bsmRef = classModel.constantPool().bootstrapMethodEntry(i).bsmIndex();
                List<LoadableConstantEntry> bsmArgs = new ArrayList<>();
                for (LoadableConstantEntry lce : classModel.constantPool().bootstrapMethodEntry(i).arguments()){
                    bsmArgs.add(lce);

                }
                specs.add(new Object[]{ bsmRef, bsmArgs});
            }
            return specs;
        }
    }

    private byte[] openInputIntoBytes(File f) throws IOException{
        try{
            return Files.readAllBytes(f.toPath());
        }
        catch(IOException e){
            throw new IOException("Error reading file: "+f);
        }
    }

    private ClassModel parseClassFile(File f) throws IOException{
        byte[] bytes = openInputIntoBytes(f);

        ClassHierarchyResolver classHierarchyResolver = classDesc -> {
            // Treat all classes as interfaces
            return ClassHierarchyResolver.ClassHierarchyInfo.ofInterface();
        };

        try {
            List<VerifyError> errors = of(ClassHierarchyResolverOption.of(classHierarchyResolver)).verify(bytes);
            if (!errors.isEmpty()) {
                for (VerifyError e : errors) {
                    System.err.println(e.getMessage());
                }
                throw new IOException("Verification failed");
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        return ClassFile.of().parse(bytes);
    }

    private static final Object[] INSTRUCTION_CONSTANTS = {
        -1, 0, 1, 2, 3, 4, 5, 0L, 1L, 0.0F, 1.0F, 2.0F, 0.0D, 1.0D
    };

    private static final String INSTRUCTION_FORMATS =
        "nop$ aconst_null$L iconst_m1$I iconst_0$I iconst_1$I "+
        "iconst_2$I iconst_3$I iconst_4$I iconst_5$I lconst_0$J_ "+
        "lconst_1$J_ fconst_0$F fconst_1$F fconst_2$F dconst_0$D_ "+
        "dconst_1$D_ bipush=bx$I sipush=bxx$I ldc=bk$X ldc_w=bkk$X "+
        "ldc2_w=bkk$X_ iload=bl/wbll$I lload=bl/wbll$J_ fload=bl/wbll$F "+
        "dload=bl/wbll$D_ aload=bl/wbll$L iload_0$I iload_1$I "+
        "iload_2$I iload_3$I lload_0$J_ lload_1$J_ lload_2$J_ "+
        "lload_3$J_ fload_0$F fload_1$F fload_2$F fload_3$F dload_0$D_ "+
        "dload_1$D_ dload_2$D_ dload_3$D_ aload_0$L aload_1$L "+
        "aload_2$L aload_3$L iaload$LI$I laload$LI$J_ faload$LI$F "+
        "daload$LI$D_ aaload$LI$L baload$LI$I caload$LI$I saload$LI$I "+
        "istore=bl/wbll$I$ lstore=bl/wbll$J_$ fstore=bl/wbll$F$ "+
        "dstore=bl/wbll$D_$ astore=bl/wbll$L$ istore_0$I$ istore_1$I$ "+
        "istore_2$I$ istore_3$I$ lstore_0$J_$ lstore_1$J_$ "+
        "lstore_2$J_$ lstore_3$J_$ fstore_0$F$ fstore_1$F$ fstore_2$F$ "+
        "fstore_3$F$ dstore_0$D_$ dstore_1$D_$ dstore_2$D_$ "+
        "dstore_3$D_$ astore_0$L$ astore_1$L$ astore_2$L$ astore_3$L$ "+
        "iastore$LII$ lastore$LIJ_$ fastore$LIF$ dastore$LID_$ "+
        "aastore$LIL$ bastore$LII$ castore$LII$ sastore$LII$ pop$X$ "+
        "pop2$XX$ dup$X$XX dup_x1$XX$XXX dup_x2$XXX$XXXX dup2$XX$XXXX "+
        "dup2_x1$XXX$XXXXX dup2_x2$XXXX$XXXXXX swap$XX$XX "+
        "iadd$II$I ladd$J_J_$J_ fadd$FF$F dadd$D_D_$D_ isub$II$I "+
        "lsub$J_J_$J_ fsub$FF$F dsub$D_D_$D_ imul$II$I lmul$J_J_$J_ "+
        "fmul$FF$F dmul$D_D_$D_ idiv$II$I ldiv$J_J_$J_ fdiv$FF$F "+
        "ddiv$D_D_$D_ irem$II$I lrem$J_J_$J_ frem$FF$F drem$D_D_$D_ "+
        "ineg$I$I lneg$J_$J_ fneg$F$F dneg$D_$D_ ishl$II$I lshl$J_I$J_ "+
        "ishr$II$I lshr$J_I$J_ iushr$II$I lushr$J_I$J_ iand$II$I "+
        "land$J_J_$J_ ior$II$I lor$J_J_$J_ ixor$II$I lxor$J_J_$J_ "+
        "iinc=blx/wbllxx$ i2l$I$J_ i2f$I$F i2d$I$D_ l2i$J_$I l2f$J_$F "+
        "l2d$J_$D_ f2i$F$I f2l$F$J_ f2d$F$D_ d2i$D_$I d2l$D_$J_ "+
        "d2f$D_$F i2b$I$I i2c$I$I i2s$I$I lcmp fcmpl fcmpg dcmpl dcmpg "+
        "ifeq=boo ifne=boo iflt=boo ifge=boo ifgt=boo ifle=boo "+
        "if_icmpeq=boo if_icmpne=boo if_icmplt=boo if_icmpge=boo "+
        "if_icmpgt=boo if_icmple=boo if_acmpeq=boo if_acmpne=boo "+
        "goto=boo jsr=boo ret=bl/wbll tableswitch=* lookupswitch=* "+
        "ireturn lreturn freturn dreturn areturn return "+
        "getstatic=bkf$Q putstatic=bkf$Q$ getfield=bkf$L$Q "+
        "putfield=bkf$LQ$ invokevirtual=bkm$LQ$Q "+
        "invokespecial=bkm$LQ$Q invokestatic=bkm$Q$Q "+
        "invokeinterface=bkixx$LQ$Q invokedynamic=bkd__$Q$Q new=bkc$L "+
        "newarray=bx$I$L anewarray=bkc$I$L arraylength$L$I athrow "+
        "checkcast=bkc$L$L instanceof=bkc$L$I monitorenter$L "+
        "monitorexit$L wide=* multianewarray=bkcx ifnull=boo "+
        "ifnonnull=boo goto_w=boooo jsr_w=boooo ";
    private static final String[] INSTRUCTION_NAMES;
    private static final String[] INSTRUCTION_POPS;
    private static final int[] INSTRUCTION_INFO;
    static {
        String[] insns = INSTRUCTION_FORMATS.split(" ");
        assert(insns[LOOKUPSWITCH].startsWith("lookupswitch"));
        assert(insns[TABLESWITCH].startsWith("tableswitch"));
        assert(insns[WIDE].startsWith("wide"));
        assert(insns[INVOKEDYNAMIC].startsWith("invokedynamic"));
        int[] info = new int[256];
        String[] names = new String[256];
        String[] pops = new String[256];
        for (int i = 0; i < insns.length; i++) {
            String insn = insns[i];
            int dl = insn.indexOf('$');
            if (dl > 0) {
                String p = insn.substring(dl+1);
                if (p.indexOf('$') < 0)  p = "$" + p;
                pops[i] = p;
                insn = insn.substring(0, dl);
            }
            int eq = insn.indexOf('=');
            if (eq < 0) {
                info[i] = 1;
                names[i] = insn;
                continue;
            }
            names[i] = insn.substring(0, eq);
            String fmt = insn.substring(eq+1);
            if (fmt.equals("*")) {
                info[i] = 0;
                continue;
            }
            int sl = fmt.indexOf('/');
            if (sl < 0) {
                info[i] = (char) fmt.length();
            } else {
                String wfmt = fmt.substring(sl+1);
                fmt = fmt.substring(0, sl);
                info[i] = (char)( fmt.length() + (wfmt.length() * 16) );
            }
        }
        INSTRUCTION_INFO = info;
        INSTRUCTION_NAMES = names;
        INSTRUCTION_POPS = pops;
    }

    static String simplifyType(String type) {
        String simpleType = OBJ_SIGNATURE.matcher(type).replaceAll("L");
        assert(simpleType.matches("^\\([A-Z]*\\)[A-Z]$"));
        // change (DD)D to (D_D_)D_
        simpleType = WIDE_SIGNATURE.matcher(simpleType).replaceAll("\\0_");
        return simpleType;
    }
    static int argsize(String type) {
        return simplifyType(type).length()-3;
    }
    private static final Pattern OBJ_SIGNATURE = Pattern.compile("\\[*L[^;]*;|\\[+[A-Z]");
    private static final Pattern WIDE_SIGNATURE = Pattern.compile("[JD]");
}
