package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate13 extends InputTemplate {
    public InputTemplate13() {}

    @Override
    public CodeSegment getTemplate() {

        String imports= """
                """;

        String statics = """
                //InputTemplate13
                static int[] iArr = new int[\\{size}];
                static int[] iArr2 = new int[\\{size}];
                """;

        String call = """
                for (int i = \\{init1}; i < \\{limit1}; i++) {
                    test_\\{uniqueId}();
                }
                """;


        String method = """
                static void test_\\{uniqueId}() {
                    int z = 0;
                    for (int i = \\{init2}; i < \\{size}; i++) {
                        iArr[i] = 12; // Normal Loop Predication can be applied for this check (profiled loop predicate due to the loop exit below).
                        // This loop exit enables Range Check Elimination
                        if (i > 56) {
                            \\{template1}
                            break;
                        }
                        // After pre/main/post, this check can be folded because i > 0 will always be true (main loop starts with i = 1).
                        if (i > 0) {
                            z = 3;
                            \\{template2}
                        }
                        // Becomes iArr[i+3] after pre/main/post and we can apply Range Check Elimination.
                        iArr2[i + z] = 34;
                    }
                }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1=new Template1();
        Template template2=new Template4();
        String template_nes1=template1.getTemplate("i");
        String template_nes2=template2.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String size = getRandomValueAsString(ARRAY_SIZES);
        String init2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String uniqueId = String.valueOf(numTest);
        replacements.put("init1", init1);
        replacements.put("limit1", limit1);
        replacements.put("init2", init2);
        replacements.put("uniqueId", uniqueId);
        replacements.put("size", size);
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

    @Override
    public int getNumberOfTestMethods() {
        return 1;
    }
}
