package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

public class InputTemplate9 extends InputTemplate {
    public InputTemplate9() {}

    @Override
    public CodeSegment getTemplate() {

        String imports= """
                """;

        String statics = """
                //InputTemplate9
                static int iFld, iFld2, iFld3;
                """;

        String call = "test_\\{uniqueId}();\n";

        String method = """
                public static void test_\\{uniqueId}() {
                     int x = \\{val1};
                     int y = \\{val2};
                     for (int i = \\{init1}; i < \\{limit1}; i++) {
                         // Single iteration loop prevents Parallel IV for outer loop and splitting MulI thru phi
                         for (int j = \\{init2}; j < \\{limit2}; j++) { // (**)
                             y++;
                             \\{template1}
                         }
                          // MulI "23 * (y - 1)" has 4 uses (1-4) outside of the loop (all uses have get_loop() == _ltree_root)
                          // while its get_ctrl() is inside the loop at the loop exit projection of (**). We can therefore sink
                          // MulI on all paths in try_sink_out_of_loop().
                         int toSink = \\{val3} * (y - 1);
                         try {
                             // Usage of 'i' prevents Loop Predication
                             x = \\{val1} / (i + iFld);     // (1)
                             x = \\{val1} / (i + 1 + iFld); // (2)
                             \\{template2}
                         } catch (Exception e) {}
                         x = toSink; // Make sure that MulI is stored in Safepoints of (1) and (2) and has
                     }
                     iFld2 = \\{val3} * (y - 1); // (3)
                     iFld3 = \\{val3}* (y - 1); // (4)
                 }
                """;

        return new CodeSegment(statics, call, method,imports);
    }

    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template2();
        Template template2 = new Template10();
        String template_nes1= template1.getTemplate("j");
        String template_nes2= template2.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String init2 = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String uniqueId = String.valueOf(numTest);
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
        replacements.put("uniqueId", uniqueId);
        return replacements;
    }
    @Override
    public String[] getCompileFlags() {
        return new String[]{"-Xcomp",
        "-XX:CompileOnly=Test::test*"};
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
