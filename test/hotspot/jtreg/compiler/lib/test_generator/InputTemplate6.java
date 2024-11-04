package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate6 extends InputTemplate {
    public InputTemplate6() {}

    @Override
    public CodeSegment getTemplate() {

        String imports= """
                import java.lang.Math;
                """;

        String statics = """
                //InputTemplate6
                private static final int SIZE = \\{size};
                private static int val = \\{Val1};
                private static short[] a = new short[SIZE];
                private static short[] b = new short[SIZE];
                """;

        String call = """
                a[SIZE-1] = \\{Val2};
                bar_\\{uniqueId}();
                System.out.println(b[SIZE-1]);
                """;
        String method = """
                 public static void bar_\\{uniqueId}() {
                     for (int i = \\{init}; i < SIZE; i++) {
                         b[i] = (short) Math.min(a[i], val);
                         \\{template1}
                     }
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template2();
        String template_nes1= template1.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String size = getRandomValueAsString(ARRAY_SIZES);
        String Val1 = getRandomValueAsString(INTEGER_VALUES);
        String Val2 = getRandomValueAsString(SHORT_VALUES);
        String init = getRandomValueAsString(INTEGER_VALUES);
        String uniqueId = String.valueOf(numTest);
        replacements.put("size", size);
        replacements.put("Val1", Val1);
        replacements.put("Val2", Val2);
        replacements.put("init", init);
        replacements.put("template1", template_nes1);
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
