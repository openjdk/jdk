package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate20 extends InputTemplate {
    @Override
    public CodeSegment getTemplate() {
        String imports= """
                """;
        String statics = """
                static long lFld;
                static A a = new A();
                static boolean flag;
                static class A {
                    int i;
                }
                """;
        String call = "test_\\{uniqueId}();\n";
        String method = """
            public static void test_\\{uniqueId}() {
                long limit = lFld;
                for (int i =\\{init}; i < \\{limit}; i \\{arithm}= \\{stride}) {
                    // Use stride > Integer.MAX_VALUE such that LongCountedLoopNode is not split further into loop nests.
                    for (long j = 0; j < limit; j+=2147483648L) {
                        a.i += 34; // NullCheck with trap on false path -> reason to peel
                        \\{thing}
                        if (j > 0) { // After peeling: j > 0 always true -> loop folded away
                            break;
                        }
                    }
                }
            }
            """;
        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Map<String, String> replacements = new HashMap<>();
        String init = getRandomValueAsString(integerValues);
        String limit = getRandomValueAsString(integerValues);
        String stride = getRandomValueAsString(integerValuesNonZero);
        String arithm = getRandomValue(new String[]{"+", "-"});
        String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        String uniqueId = String.valueOf(numTest);
        replacements.put("init", init);
        replacements.put("limit", limit);
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);
        replacements.put("thing", thing);
        replacements.put("uniqueId", uniqueId);
        return replacements;


    }

    @Override
    public String[] getCompileFlags() {
        return new String[]{
            "-Xcomp",
            "-XX:-CreateCoredumpOnCrash"
        };
    }

    @Override
    public int getNumberOfTests() {
        return 13;
    }

    @Override
    public int getNumberOfTestMethods() {
        return 1;
    }
}
