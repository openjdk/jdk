/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.tools.jlink.internal.plugins.asm.AsmPools;
import jdk.tools.jlink.internal.plugins.asm.AsmPlugin;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import jdk.tools.jlink.internal.plugins.asm.AsmModulePool;
import jdk.tools.jlink.internal.plugins.optim.ForNameFolding;
import jdk.tools.jlink.internal.plugins.optim.ReflectionOptimizer.TypeResolver;
import jdk.tools.jlink.plugin.PluginException;

/**
 *
 * Optimize Classes following various strategies. Strategies are implementation
 * of <code>ClassOptimizer</code> and <code>MethodOptimizer</code>.
 */
public final class OptimizationPlugin extends AsmPlugin {

    public static final String NAME = "class-optim";
    public static final String LOG  = "log";
    public static final String ALL = "all";
    public static final String FORNAME_REMOVAL = "forName-folding";

    /**
     * Default resolver. A resolver that retrieve types that are in an
     * accessible package, are public or are located in the same package as the
     * caller.
     */
    private static final class DefaultTypeResolver implements TypeResolver {

        private final Set<String> packages;
        private final AsmPools pools;

        DefaultTypeResolver(AsmPools pools, AsmModulePool modulePool) {
            Objects.requireNonNull(pools);
            Objects.requireNonNull(modulePool);
            this.pools = pools;
            packages = pools.getGlobalPool().getAccessiblePackages(modulePool.getModuleName());
        }

        @Override
        public ClassReader resolve(ClassNode cn, MethodNode mn, String type) {
            int classIndex = cn.name.lastIndexOf("/");
            String callerPkg = classIndex == -1 ? ""
                    : cn.name.substring(0, classIndex);
            int typeClassIndex = type.lastIndexOf("/");
            String pkg = typeClassIndex == - 1 ? ""
                    : type.substring(0, typeClassIndex);
            ClassReader reader = null;
            if (packages.contains(pkg) || pkg.equals(callerPkg)) {
                ClassReader r = pools.getGlobalPool().getClassReader(type);
                if (r != null) {
                    // if not private
                    if ((r.getAccess() & Opcodes.ACC_PRIVATE)
                            != Opcodes.ACC_PRIVATE) {
                        // public
                        if (((r.getAccess() & Opcodes.ACC_PUBLIC)
                                == Opcodes.ACC_PUBLIC)) {
                            reader = r;
                        } else if (pkg.equals(callerPkg)) {
                            reader = r;
                        }
                    }
                }
            }
            return reader;
        }
    }

    public interface Optimizer {

        void close() throws IOException;
    }

    public interface ClassOptimizer extends Optimizer {

        boolean optimize(Consumer<String> logger, AsmPools pools,
                AsmModulePool modulePool,
                ClassNode cn) throws Exception;
    }

    public interface MethodOptimizer extends Optimizer {

        boolean optimize(Consumer<String> logger, AsmPools pools,
                AsmModulePool modulePool,
                ClassNode cn, MethodNode m, TypeResolver resolver) throws Exception;
    }

    private List<Optimizer> optimizers = new ArrayList<>();

    private OutputStream stream;
    private int numMethods;

    private void log(String content) {
        if (stream != null) {
            try {
                content = content + "\n";
                stream.write(content.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                System.err.println(ex);
            }
        }
    }

    private void close() throws IOException {
        log("Num analyzed methods " + numMethods);

        for (Optimizer optimizer : optimizers) {
            try {
                optimizer.close();
            } catch (IOException ex) {
                System.err.println("Error closing optimizer " + ex);
            }
        }
        if (stream != null) {
            stream.close();
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void visit(AsmPools pools) {
        try {
            for (AsmModulePool p : pools.getModulePools()) {
                DefaultTypeResolver resolver = new DefaultTypeResolver(pools, p);
                p.visitClassReaders((reader) -> {
                    ClassWriter w = null;
                    try {
                        w = optimize(pools, p, reader, resolver);
                    } catch (IOException ex) {
                        throw new PluginException("Problem optimizing "
                                + reader.getClassName(), ex);
                    }
                    return w;
                });
            }
        } finally {
            try {
                close();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    private ClassWriter optimize(AsmPools pools, AsmModulePool modulePool,
            ClassReader reader, TypeResolver resolver)
            throws IOException {
        ClassNode cn = new ClassNode();
        ClassWriter writer = null;
        if ((reader.getAccess() & Opcodes.ACC_INTERFACE) == 0) {
            reader.accept(cn, ClassReader.EXPAND_FRAMES);
            boolean optimized = false;
            for (Optimizer optimizer : optimizers) {
                if (optimizer instanceof ClassOptimizer) {
                    try {
                        boolean optim = ((ClassOptimizer) optimizer).
                                optimize(this::log, pools, modulePool, cn);
                        if (optim) {
                            optimized = true;
                        }
                    } catch (Throwable ex) {
                        throw new PluginException("Exception optimizing "
                                + reader.getClassName(), ex);
                    }
                } else {
                    MethodOptimizer moptimizer = (MethodOptimizer) optimizer;
                    for (MethodNode m : cn.methods) {
                        if ((m.access & Opcodes.ACC_ABSTRACT) == 0
                                && (m.access & Opcodes.ACC_NATIVE) == 0) {
                            numMethods += 1;
                            try {
                                boolean optim = moptimizer.
                                        optimize(this::log, pools, modulePool, cn,
                                                m, resolver);
                                if (optim) {
                                    optimized = true;
                                }
                            } catch (Throwable ex) {
                                throw new PluginException("Exception optimizing "
                                        + reader.getClassName() + "." + m.name, ex);
                            }

                        }
                    }
                }
            }

            if (optimized) {
                writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                try {
                    // add a validation layer in between to check for class vallidity
                    CheckClassAdapter ca = new CheckClassAdapter(writer);
                    cn.accept(ca);
                } catch (Exception ex) {
                    throw new PluginException("Exception optimizing class " + cn.name, ex);
                }
            }
        }
        return writer;
    }

    @Override
    public String getDescription() {
        return PluginsResourceBundle.getDescription(NAME);
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public String getArgumentsDescription() {
       return PluginsResourceBundle.getArgument(NAME);
    }

    @Override
    public void configure(Map<String, String> config) {
        String strategies = config.get(NAME);
        String[] arr = strategies.split(",");
        for (String s : arr) {
            if (s.equals(ALL)) {
                optimizers.clear();
                optimizers.add(new ForNameFolding());
                break;
            } else if (s.equals(FORNAME_REMOVAL)) {
                optimizers.add(new ForNameFolding());
            } else {
                throw new PluginException("Unknown optimization");
            }
        }
        String f = config.get(LOG);
        if (f != null) {
            try {
                stream = new FileOutputStream(f);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    @Override
    public Set<PluginType> getType() {
        Set<PluginType> set = new HashSet<>();
        set.add(CATEGORY.TRANSFORMER);
        return Collections.unmodifiableSet(set);
    }
}
