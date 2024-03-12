package tools.javac.combo;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

public class ComboWatcher implements TestWatcher, AfterAllCallback {
    private final Set<String> errors = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        if (context.getRequiredTestInstance() instanceof JavacTemplateTestBase instance) {
            errors.addAll(instance.diags.errorKeys());
            if (instance instanceof CompilationTestCase) {
                // Make sure offending template ends up in log file on failure
                System.err.printf("Diagnostics: %s%nTemplate: %s%n", instance.diags.errorKeys(),
                        instance.sourceFiles.stream().map(SourceFile::template).toList());
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        if (errors.isEmpty()) return;
        System.err.println("Errors found in tests: " + errors);
    }
}
