package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate6 extends InputTemplate {
    public InputTemplate6() {}

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
                java.lang.Math
                """;

        String statics = """
                private static final int SIZE = \\{size};              
                private static int val = \\{Val1};
                private static short[] a = new short[SIZE];
                private static short[] b = new short[SIZE];
                """;

        String call = "a[100] = \\{Val2};\n" +
                "        bar_\\{uniqueId}();\n" +
                "        System.out.println(b[100]);";

        String method = """
                 public static void bar_\\{uniqueId}() {
                         for (int i = \\{init}; i < SIZE; i++) {
                             b[i] = (short) Math.min(a[i], val);
                         }
                     }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements() {
        Map<String, String> replacements = new HashMap<>();

        String size = getRandomValueAsString(integerValues);
        String Val1 = getRandomValueAsString(integerValues);
        String Val2 = getRandomValueAsString(shortValues);
        String init = getRandomValueAsString(integerValues);
        //String stride = getRandomValueAsString(integerValuesNonZero);
       // String arithm = getRandomValue(new String[]{"+", "-"});
        //String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
        String uniqueId = getUniqueId();

        replacements.put("size", size);
        replacements.put("Val1", Val1);
        replacements.put("Val2", Val2);
        replacements.put("init", init);
        /*
        replacements.put("arithm", arithm);
        replacements.put("stride", stride);
        replacements.put("thing", thing);

         */
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
