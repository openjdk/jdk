package compiler.lib.test_generator;
import java.util.HashMap;
import java.util.Map;

public class InputTemplate5 extends InputTemplate {
    public InputTemplate5() {}

    @Override
    public CodeSegment getTemplate() {
        String imports= """
                """;

        String statics = """
                //InputTemplate5
                static int N;
                """;

        String call = """
            for (int i = \\{init1}; i < \\{limit}; i++) {
                test_\\{uniqueId}(\\{num});
            }
                """;

        String method = """
            public static void test_\\{uniqueId}(int limit) {
                boolean a[] = new boolean[\\{size}];
                for (int i = 0; i < \\{size}; i++) {
                    a[i] = 80.1f > i;
                    \\{template2}
                }
                \\{template1}
            }
            """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template4();
        Template template2 = new Template10();
        String template_nes1= template1.getTemplate("j");
        String template_nes2= template2.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String limit = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String size = getRandomValueAsString(ARRAY_SIZES);
        String num = getRandomValueAsString(INTEGER_VALUES);
        String uniqueId = String.valueOf(numTest);
        replacements.put("init1", init1);
        replacements.put("limit", limit);
        replacements.put("size", size);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
        replacements.put("num", num);
        replacements.put("uniqueId", uniqueId);
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
