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

import java.io.File;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.constant.ConstantDesc;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.DirectMethodHandleDesc.Kind.*;

/**
 * Transform one or more class files to incorporate JSR 292 features,
 * such as {@code invokedynamic}.
 * <p>
 * This is a standalone program in a single source file.
 * In this form, it may be useful for test harnesses, small experiments, and javadoc examples.
 * Copies of this file may show up in multiple locations for standalone usage.
 * The primary maintained location of this file is as follows:
 * <a href="http://kenai.com/projects/ninja/sources/indify-repo/content/src/indify/Indify.java">
 * http://kenai.com/projects/ninja/sources/indify-repo/content/src/indify/Indify.java</a>
 * <p>
 * Static private methods named MH_x and MT_x (where x is arbitrary)
 * must be stereotyped generators of MethodHandle and MethodType
 * constants.  All calls to them are transformed to {@code CONSTANT_MethodHandle}
 * and {@code CONSTANT_MethodType} "ldc" instructions.
 * The stereotyped code must create method types by calls to {@code methodType} or
 * {@code fromMethodDescriptorString}.  The "lookup" argument must be created
 * by calls to {@code java.lang.invoke.MethodHandles#lookup MethodHandles.lookup}.
 * The class and string arguments must be constant.
 * The following methods of {@code java.lang.invoke.MethodHandle.Lookup Lookup} are
 * allowed for method handle creation: {@code findStatic}, {@code findVirtual},
 * {@code findConstructor}, {@code findSpecial},
 * {@code findGetter}, {@code findSetter},
 * {@code findStaticGetter}, or {@code findStaticSetter}.
 * The call to one of these methods must be followed immediately
 * by an {@code areturn} instruction.
 * The net result of the call to the MH_x or MT_x method must be
 * the creation of a constant method handle.  Thus, replacing calls
 * to MH_x or MT_x methods by {@code ldc} instructions should leave
 * the meaning of the program unchanged.
 * <p>
 * Static private methods named INDY_x must be stereotyped generators
 * of {@code invokedynamic} call sites.
 * All calls to them must be immediately followed by
 * {@code invokeExact} calls.
 * All such pairs of calls are transformed to {@code invokedynamic}
 * instructions.  Each INDY_x method must begin with a call to a
 * MH_x method, which is taken to be its bootstrap method.
 * The method must be immediately invoked (via {@code invokeGeneric}
 * on constant lookup, name, and type arguments.  An object array of
 * constants may also be appended to the {@code invokeGeneric call}.
 * This call must be cast to {@code CallSite}, and the result must be
 * immediately followed by a call to {@code dynamicInvoker}, with the
 * resulting method handle returned.
 * <p>
 * The net result of all of these actions is equivalent to the JVM's
 * execution of an {@code invokedynamic} instruction in the unlinked state.
 * Running this code once should produce the same results as running
 * the corresponding {@code invokedynamic} instruction.
 * In order to model the caching behavior, the code of an INDY_x
 * method is allowed to begin with getstatic, aaload, and if_acmpne
 * instructions which load a static method handle value and return it
 * if the value is non-null.
 * <p>
 * Example usage:
 * <blockquote><pre>
$ JAVA_HOME=(some recent OpenJDK 7 build)
$ ant
$ $JAVA_HOME/bin/java -cp build/classes indify.Indify --overwrite --dest build/testout build/classes/indify/Example.class
$ $JAVA_HOME/bin/java -cp build/classes indify.Example
MT = (java.lang.Object)java.lang.Object
MH = adder(int,int)java.lang.Integer
adder(1,2) = 3
calling indy:  42
$ $JAVA_HOME/bin/java -cp build/testout indify.Example
(same output as above)
 * </pre></blockquote>
 * <p>
 * A version of this transformation built on top of <a href="http://asm.ow2.org/">http://asm.ow2.org/</a> would be welcome.
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

        if ("--java".equals(avl.getFirst())) {
            avl.removeFirst();
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

    /**
     *  Execute the given application under a class loader which indifies all application classes.
     *
     * @param av an array of strings where the first element is the fully qualified name
     *           of the main class to be executed, and the remaining elements are arguments
     *           to be passed to the main method of the specified class.
     * @throws Exception if there is an error during class loading, method retrieval, or invocation.
     */
    public void runApplication(String... av) throws Exception {
        List<String> avl = new ArrayList<>(Arrays.asList(av));
        String mainClassName = avl.removeFirst();
        av = avl.toArray(new String[0]);
        Class<?> mainClass = Class.forName(mainClassName, true, makeClassLoader());
        Method main = mainClass.getMethod("main", String[].class);
        try { main.setAccessible(true); } catch (SecurityException ignored) { }
        main.invoke(null, (Object) av);
    }

    /**
     * Parses a list of options and arguments, configuring the application's settings based on the provided options.
     *
     * @param av a list of strings representing the options and arguments to be parsed.
     *           Options are expected to start with a hyphen ('-') or double hyphen ('--').
     *           Arguments that do not start with a hyphen are considered as positional arguments and terminate the options parsing.
     * @throws IOException if an I/O error occurs during the processing of options.
     * @throws IllegalArgumentException if an unrecognized flag is encountered.
     * @throws RuntimeException if no output destination is specified and the overwrite option is not enabled.
     */
    public void parseOptions(List<String> av) throws IOException {
        for (; !av.isEmpty(); av.remove(0)) {
            String a = av.getFirst();
            if (a.startsWith("-")) {
                String a2 = null;
                int eq = a.indexOf('=');
                if (eq > 0) {
                    a2 = maybeExpandProperties(a.substring(eq+1));
                    a = a.substring(0, eq+1);
                }
                switch (a) {
                case "--java":
                    return;  // keep this argument
                case "-d": case "--dest": case "-d=": case "--dest=":
                    dest = new File(a2 != null ? a2 : maybeExpandProperties(av.remove(1)));
                    break;
                case "-cp": case "--classpath":
                    classpath = maybeExpandProperties(av.remove(1)).split("["+File.pathSeparatorChar+"]");
                    break;
                case "-k": case "--keepgoing": case "--keepgoing=":
                    keepgoing = booleanOption(a2);  // print errors but keep going
                    break;
                case "--expand-properties": case "--expand-properties=":
                    expandProperties = booleanOption(a2);  // expand property references in subsequent arguments
                    break;
                case "--verify-specifier-count": case "--verify-specifier-count=":
                        assert a2 != null;
                        verifySpecifierCount = Integer.parseInt(a2);
                    break;
                case "--overwrite": case "--overwrite=":
                    overwrite = booleanOption(a2);  // overwrite output files
                    break;
                case "--all": case "--all=":
                    all = booleanOption(a2);  // copy all classes, even if no patterns
                    break;
                case "-q": case "--quiet": case "--quiet=":
                    quiet = booleanOption(a2);  // less output
                    break;
                case "-v": case "--verbose": case "--verbose=":
                    verbose = booleanOption(a2);  // more output
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
            av.replaceAll(this::maybeExpandProperties);
        }
    }

    /**
     * Converts a string representation of a boolean option to its boolean value.
     * If the input string is null, it returns true by default.
     *
     * @param s the string representation of the boolean option. Accepted values for true are "true", "yes", "on", and "1".
     *          Accepted values for false are "false", "no", "off", and "0".
     * @return the boolean value corresponding to the input string.
     * @throws IllegalArgumentException if the input string is not recognized as a valid boolean option.
     */
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
            indifyJar(f, dest); //Not yet implemented
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
        if (verbose)  System.err.println("reading " + f);
        ClassModel model = parseClassFile(f);
        Logic logic = new Logic(model);
        Boolean changed = logic.transform();
        logic.reportPatternMethods(quiet, keepgoing);
        if (changed || all) {
            File outfile;
            if (dest != null) {
                ensureDirectory(dest);
                outfile = classPathFile(dest, model.thisClass().name().stringValue());
            } else {
                outfile = f;  // overwrite input file, no matter where it is
            }
            Files.write(outfile.toPath(), transformToBytes(logic.classModel));
            if (!quiet) System.err.println("wrote "+outfile);
        }
    }

    byte[] transformToBytes(ClassModel classModel) {
        return of().transform(classModel, ClassTransform.ACCEPT_ALL);
    }

    File classPathFile(File pathDir, String className) {
        String qualname = className.replace('.','/')+".class";
        qualname = qualname.replace('/', File.separatorChar);
        return new File(pathDir, qualname);
    }

    public void indifyJar(File f, Object dest) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void indifyTree(File f, File dest) throws IOException {
        if (verbose)  System.err.println("reading directory: "+f);
        for (File f2 : Objects.requireNonNull(f.listFiles((dir, name) -> {
            if (name.endsWith(".class")) return true;
            if (name.contains(".")) return false;
            // return true if it might be a package name:
            return Character.isJavaIdentifierStart(name.charAt(0));
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
                } catch (ClassNotFoundException | IOException ex) {
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
            Boolean changed = logic.transform();
            if (verbose && !changed) System.err.println("(no change)");
            logic.reportPatternMethods(!verbose, keepgoing);
            byte[] newBytes = transformToBytes(logic.classModel);

            return defineClass(null, newBytes, 0, newBytes.length);
        }
    }

    private class Logic {
        ClassModel classModel;
        ConstantPoolBuilder poolBuilder;  //Builder for the new constant pool
        final char[] poolMarks;
        final Map<String, PoolEntry> constants = new HashMap<>();
        Logic(ClassModel classModel){
            this.classModel = classModel;
            poolBuilder = ConstantPoolBuilder.of(classModel);
            poolMarks = new char[classModel.constantPool().size()];
        }

        Boolean transform() {
            if (!initializeMarks()) return false;
            if (!findPatternMethods()) return false;

            Deque<PoolEntry> pendingIndy = new ArrayDeque<>(); //Holding the pending invokedynamic constant to replace the invokeExact

            CodeTransform codeTransform = (b, e) -> {
                if (e instanceof InvokeInstruction invokeInstruction) {
                    String methodInvoked = invokeInstruction.method().name().stringValue();

                    if (invokeInstruction.opcode().bytecode() == INVOKEVIRTUAL &&
                            !pendingIndy.isEmpty() &&
                            methodInvoked.equals("invokeExact")) {
                        b.invokeDynamicInstruction((InvokeDynamicEntry) pendingIndy.pop());
                        if (!quiet) System.err.println("Removing <<invokeExact>> invocation on MethodHandle");
                        return;
                    }

                    if (invokeInstruction.opcode().bytecode() != INVOKESTATIC) {
                        b.with(e); // Not an INVOKESTATIC instruction, keep it as is
                        return;
                    }

                    if (poolMarks[invokeInstruction.method().index()] == 0) {
                        b.with(e); // Skip if not marked
                        return;
                    }

                    // Is it a pattern method?
                    if (!constants.containsKey(methodInvoked)) {
                        b.with(e);
                        return;
                    }

                    PoolEntry newConstant = constants.get(methodInvoked);

                    if (newConstant instanceof InvokeDynamicEntry) {
                        pendingIndy.push(newConstant);
                        if (!quiet) {
                            System.err.println(":::Transforming the Method Class for: " + invokeInstruction.method().name() +
                                    "  to => invokedynamic: " +
                                    ((InvokeDynamicEntry) newConstant).nameAndType());
                        }

                        if (!quiet) System.err.println("Removing instruction invokestatic for Method: " + invokeInstruction.name());
                        b.nop();
                    } else {
                        if (!quiet) {
                            System.err.println(":::Transforming the Method Call of: " + invokeInstruction.method().name() +
                                    " to => ldc: " + newConstant.index());
                        }
                        b.ldc((LoadableConstantEntry) newConstant);
                    }
                } else {
                    b.with(e);
                }
            };

            // Remove all pattern methods from the class model
            removePatternMethods(quiet);

            // Apply the transformation to the class model
            classModel = of().parse(of().transform(classModel, ClassTransform.transformingMethodBodies(codeTransform)));

            return true;
        }

        void removePatternMethods(boolean quietly) {
            classModel = of().parse(
                    of().transform(classModel, (b, e) ->
                    {
                        if (!(e instanceof MethodModel mm &&
                                (mm.methodName().stringValue().startsWith("MH_") ||
                                        mm.methodName().stringValue().startsWith("MT_") ||
                                        mm.methodName().stringValue().startsWith("INDY_"))
                        )) b.with(e);
                        else{
                            if(!quietly) System.err.println("Removing pattern method: " + ((MethodModel) e).methodName());
                        }
                    })
            );
        }

        boolean findPatternMethods() {
            boolean found = false;
            for (char mark : "THI".toCharArray()) {
                for (MethodModel m : classModel.methods()) {
                    if (!Modifier.isPrivate(m.flags().flagsMask())) continue;
                    if (!Modifier.isStatic(m.flags().flagsMask())) continue;
                    if (nameAndTypeMark(m.methodName(), m.methodType()) == mark) {
                        PoolEntry entry = scanPattern(m, mark);
                        if (entry == null) continue;
                        constants.put(m.methodName().stringValue(), entry);
                        found = true;
                    }
                }
            }
            return found;
        }

        void reportPatternMethods(boolean quietly, boolean allowMatchFailure) {
            if (!quietly && !constants.keySet().isEmpty())
                System.err.println("pattern methods removed: " + constants.keySet());
            for (MethodModel m : classModel.methods()) {
                if (nameMark(m.methodName().stringValue()) != 0 &&
                        constants.get(m.methodName().stringValue()) == null) {
                    String failure = "method has a special name but fails to match pattern: " + m.methodName();
                    if (!allowMatchFailure)
                        throw new IllegalArgumentException(failure);
                    else if (!quietly)
                        System.err.println("warning: " + failure);
                }
            }
            if (!quiet) System.err.flush();
        }

        boolean initializeMarks() {
            boolean anyMarkChanged = false;
            for (PoolEntry entry : classModel.constantPool()) {
                char mark = 0;
                if (poolMarks[entry.index()] != 0) continue;

                if (entry instanceof Utf8Entry utf8Entry) {
                    mark = nameMark(utf8Entry.stringValue());
                }
                if (entry instanceof ClassEntry classEntry) {
                    mark = nameMark(classEntry.asInternalName());
                }
                if (entry instanceof StringEntry stringEntry) {
                    mark = nameMark(stringEntry.stringValue());
                }
                if (entry instanceof NameAndTypeEntry nameAndTypeEntry) {
                    mark = nameAndTypeMark(nameAndTypeEntry.name(), nameAndTypeEntry.type());
                }
                if (entry instanceof MemberRefEntry memberRefEntry) {
                    poolMarks[memberRefEntry.owner().index()] = nameMark(memberRefEntry.owner().asInternalName());
                    if (poolMarks[memberRefEntry.owner().index()] != 0) {
                        mark = poolMarks[memberRefEntry.owner().index()];
                    }
                    else {
                        if (memberRefEntry.owner().equals(classModel.thisClass())) {
                            mark = nameMark(memberRefEntry.name().stringValue());
                        }
                    }
                }
                poolMarks[entry.index()] = mark;
                anyMarkChanged = true;
            }
            return anyMarkChanged;
        }

        char nameAndTypeMark(Utf8Entry name, Utf8Entry type){
            char mark = poolMarks[name.index()] = nameMark(name.stringValue());
            if (mark == 0) return 0;
            String descriptor = type.stringValue();
            String requiredType;
            switch (mark) {
                case 'H', 'I': requiredType = "()Ljava/lang/invoke/MethodHandle;";  break;
                case 'T': requiredType = "()Ljava/lang/invoke/MethodType;";    break;
                default:  return 0;
            }
            if (matchType(descriptor, requiredType)) return mark;
            return 0;
        }

        char nameMark(String s) {
            if (s.startsWith("MT_"))                return 'T';
            else if (s.startsWith("MH_"))           return 'H';
            else if (s.startsWith("INDY_"))         return 'I';
            else if (s.startsWith("java/lang/invoke/"))  return 'D';
            else if (s.startsWith("java/lang/"))    return 'J';
            return 0;
        }

        boolean matchType(String descr, String requiredType) {
            return descr.equals(requiredType);
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
            List<Object> bsmArgs = null;  // args to invokeGeneric
        decode:
            for(Instruction instruction : instructions){

                int bc = instruction.opcode().bytecode();
                String UNKNOWN_CON = "<unknown>";
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
                        if (type.equals("java/lang/StringBuilder")) {
                            jvm.push("StringBuilder");
                            continue;
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
                                con = makeMethodTypeCon(args.getFirst());
                                args.clear(); args.add(con);
                                continue;
                            case "methodType": {
                                flattenVarargs(args);
                                StringBuilder buf = new StringBuilder();
                                String rtype = null;
                                for(Object typeArg : args) {
                                    if (typeArg instanceof Class<?> argClass) {
                                        buf.append(argClass.descriptorString());
                                    }else if (typeArg instanceof PoolEntry argCon) {
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
                                con = makeMethodTypeCon(buf.toString());
                                args.clear(); args.add(con);
                                continue;
                            }
                            case "lookup":
                            case "dynamicInvoker":
                                args.clear(); args.add(intrinsic);
                                continue;
                            case "lookupClass":
                                if(args.equals(List.of("lookup"))) {
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
                                    arg = args.removeFirst();
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
                        if(!hasReceiver && ownMethod != null) {
                            con = constants.get(ownMethod.methodName().stringValue());
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
                                return indyCon;
                            }else {
                                System.err.println("Failed to create invokedynamic instruction for the method: " + method.methodName());
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
                        if (bc >= ICONST_M1 && bc <= DCONST_1) {
                            jvm.push(instruction.opcode().constantValue()); break;
                        }
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
                            continue;
                        }
                        break decode;  // bail out
                }
            }
            System.err.println(method+": bailout ==> jvm stack: "+jvm.stack);
            return null;
        }

        private void flattenVarargs(List<Object> args) {
            int size = args.size();
            if (size > 0 && args.get(size - 1) instanceof List) {
                List<?> removedArg = (List<?>) args.remove(size - 1);
                args.addAll(removedArg);
            }
        }

        private PoolEntry makeMethodTypeCon(Object x){
           if (x instanceof StringEntry stringEntry){
                return poolBuilder.methodTypeEntry(stringEntry.utf8());
            } else {
               return poolBuilder.methodTypeEntry(poolBuilder.utf8Entry((String) x));

            }
        }

        private PoolEntry parseMemberLookup(byte refKind, List<Object> args){
            //E.g.: lookup().findStatic(Foo.class, "name", MethodType)
            if(args.size() != 4) return null;

            NameAndTypeEntry nameAndTypeEntry;
            Utf8Entry name, type;
            ClassEntry ownerClass;

            if(!"lookup".equals(args.getFirst())) return null;

            if(args.get(1) instanceof ClassEntry classEntry) ownerClass = classEntry;
            else return null;

            if(args.get(2) instanceof StringEntry stringEntry) name = stringEntry.utf8();
            else return null;

            if(args.get(3) instanceof MethodTypeEntry methodTypeEntry) type = methodTypeEntry.descriptor();
            else return null;

            nameAndTypeEntry = poolBuilder.nameAndTypeEntry(name,type);

            MemberRefEntry ref;
            if(refKind <= (byte) STATIC_SETTER.refKind){
                ref = poolBuilder.fieldRefEntry(ownerClass, nameAndTypeEntry);
                return poolBuilder.methodHandleEntry(refKind, ref);
            }
            else if(refKind == (byte) INTERFACE_VIRTUAL.refKind){
                ref = poolBuilder.interfaceMethodRefEntry(ownerClass, nameAndTypeEntry);
                return poolBuilder.methodHandleEntry(refKind, ref);
            }
            else{
                ref = poolBuilder.methodRefEntry(ownerClass, nameAndTypeEntry);
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
            NameAndTypeEntry nameAndTypeEntry;
            MethodHandleEntry bootstrapMethod;

            if (!((con = args.get(argi++)) instanceof MethodHandleEntry)) return null;
            bootstrapMethod = ((MethodHandleEntry) con);

            if (!"lookup".equals(args.get(argi++)))  return null;
            if (!((con = args.get(argi++)) instanceof StringEntry)) return null;
            name = ((StringEntry) con).utf8();

            if (!((con = args.get(argi++)) instanceof MethodTypeEntry)) return null;
            type = ((MethodTypeEntry) con).descriptor();

            nameAndTypeEntry = poolBuilder.nameAndTypeEntry(name, type);

            List<Object> extraArgs = new ArrayList<>();
            if (argi < args.size()) {
                extraArgs.addAll(args.subList(argi, args.size() - 1));
                Object lastArg = args.getLast();
                if (lastArg instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> lastArgs = (List<Object>) lastArg;
                    removeEmptyJVMSlots(lastArgs);
                    extraArgs.addAll(lastArgs);
                } else {
                    extraArgs.add(lastArg);
                }
            }
            List<ConstantDesc> extraArgConstantDescs = new ArrayList<>();
            for (Object x : extraArgs) {
                if (x instanceof Number) {
                    extraArgConstantDescs.add(((ConstantDesc) x));
                    continue;
                }
                if (!(x instanceof PoolEntry)) {
                    System.err.println("warning: unrecognized BSM argument "+x);
                    return null;
                }
                assert x instanceof LoadableConstantEntry;
                extraArgConstantDescs.add((((LoadableConstantEntry) x).constantValue()));
            }

            BootstrapMethodEntry bsmEntry = poolBuilder.bsmEntry(bootstrapMethod.asSymbol(), extraArgConstantDescs);
            return poolBuilder.invokeDynamicEntry(bsmEntry, nameAndTypeEntry);
        }
    }

    private ClassModel parseClassFile(File f) throws IOException {
        byte[] bytes = Files.readAllBytes(f.toPath());

        try {
            List<VerifyError> errors = of().verify(bytes);
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

    static String simplifyType(String type) {
        String simpleType = OBJ_SIGNATURE.matcher(type).replaceAll("L");
        assert (simpleType.matches("^\\([A-Z]*\\)[A-Z]$"));
        // change (DD)D to (D_D_)D_
        simpleType = WIDE_SIGNATURE.matcher(simpleType).replaceAll("\\0_");
        return simpleType;
    }

    static int argsize(String type) {
        return simplifyType(type).length() - 3;
    }

    private static final Pattern OBJ_SIGNATURE = Pattern.compile("\\[*L[^;]*;|\\[+[A-Z]");
    private static final Pattern WIDE_SIGNATURE = Pattern.compile("[JD]");
}
