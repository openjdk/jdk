package build.tools.runtimelink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.internal.module.ModulePath;

@SuppressWarnings("try")
public class JmodsReader implements JimageDiffGenerator.ImageResource {

    private final ModuleFinder finder;

    public JmodsReader(Path packagedModulesDir) {
        List<Path> pa;
        try {
            pa = Files.list(packagedModulesDir).filter(p -> !p.endsWith(".jmod")).collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Listing of jmods directory failed!", e);
        }
        Path[] paths = pa.toArray(new Path[0]);
        this.finder = ModulePath.of(Runtime.version(), true, paths);
    }

    @Override
    public void close() throws Exception {
        // nothing
    }

    @Override
    public List<String> getEntries() {
        List<String> all = new ArrayList<>();
        try {
            Set<ModuleReference> allMods = finder.findAll();
            for (ModuleReference mRef: allMods) {
                String moduleName = mRef.descriptor().name();
                ModuleReader reader = mRef.open();
                List<String> perModule = reader.list().map(a -> {return "/" + moduleName + "/" + a;}).collect(Collectors.toList());
                all.addAll(perModule);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Collections.sort(all);
        return all;
    }

    @Override
    public byte[] getResourceBytes(String name) {
        int secondSlash = name.indexOf("/", 1);
        String moduleName = null;
        if (secondSlash != -1) {
            moduleName = name.substring(1, secondSlash);
        }
        if (moduleName == null) {
            throw new IllegalArgumentException("Module name not found in " + name);
        }
        ModuleReference ref = finder.find(moduleName).orElseThrow();
        String refName = name.substring(secondSlash + 1); // omit the leading slash
        try (ModuleReader reader = ref.open()) {
            return reader.open(refName).orElseThrow().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
