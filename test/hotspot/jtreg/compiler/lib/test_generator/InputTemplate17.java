package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate17 extends InputTemplate {
    @Override
    public CodeSegment getTemplate() {
        String imports= """
                """;
        String statics = """
            //InputTemplate17
            public static boolean flag = \\{bool};
            """;
        String call = """
                test_\\{uniqueId}();
                """;
        String method = """
            public static void test_\\{uniqueId}() {
                int i = \\{val};
                while (true) {
                    // Found as loop head in ciTypeFlow, but both path inside loop -> head not cloned.
                    // As a result, this head has the safepoint as backedge instead of the loop exit test
                    // and we cannot create a counted loop (yet). We first need to partial peel.
                    if (flag) {
                        \\{template1}
                    }
                    // Loop exit test.
                    if (i < 5) {
                        \\{template2}
                        return;
                    }
                    // <-- Partial Peeling CUT -->
                    // Safepoint
                    i--;
                }
            }
            """;


        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template3();
        Template template2 = new Template4();
        String template_nes1= template1.getTemplate("i");
        String template_nes2= template2.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String bool = getRandomValue(new String[]{"true", "false"});
        String uniqueId = String.valueOf(numTest);
        replacements.put("val", val1);
        replacements.put("bool", bool);
        replacements.put("uniqueId", uniqueId);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);

        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[0];
    }

    @Override
    public int getNumberOfTests() {
        return 1;
    }
    public int getNumberOfTestMethods(){
        return 1;
    }
}
