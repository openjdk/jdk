package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate5 extends InputTemplate {
    public InputTemplate5() {}

    @Override
    public CodeSegment getTemplate() {
        String template_nes = """
            int a1 = 77;
            int b1 = 0;
            do {
                a1--;
                b1++;
            } while (a1 > 0);
            """;
        String imports= """
                """;

        String statics = """
                static int N;
                """;

        String call = """
                for (int i = \\{init1}; i < \\{limit}; i++) {
                             test(\\{num});
                         }
                """;

        String method = """
                 public static void test_\\{uniqueId}(int limit) {
                         boolean a[] = new boolean[\\{size}];
                         for (int i = \\{init2}; i < limit; i++) {
                             a[i] = 80.1f > i;
                         }
                     }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements() {
        Map<String, String> replacements = new HashMap<>();

        String init1 = getRandomValueAsString(integerValues);
        String init2 = getRandomValueAsString(integerValues);
        String limit = getRandomValueAsString(integerValues);
        String size = getRandomValueAsString(integerValues);
        String num = getRandomValueAsString(integerValues);
        //String stride = getRandomValueAsString(integerValuesNonZero);
        //String arithm = getRandomValue(new String[]{"false", "true"});
        //String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        String uniqueId = getUniqueId();

        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit", limit);
        replacements.put("size", size);
        replacements.put("num", num);
        //replacements.put("arithm", arithm);
        //replacements.put("stride", stride);
        //replacements.put("thing", thing);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }

    @Override
    public String[] getCompileFlags() {
        return new String[0];
    }

    @Override
    public int getNumberOfTests() {
        return 10;
    }

}
