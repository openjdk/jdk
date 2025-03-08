package compiler.lib.test_generator;

import static compiler.lib.test_generator.TemplateGenerator.performReplacements;
import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template6 extends Template {
    public Template6() {}
    public String getTemplate(String variable){
        String statics= """
                int $a;
                int $b;
                Boolean $flag=\\{bool1};
                Boolean $flag2=\\{bool2};
                int $iFld;
                """;
        String method="""
            for (int $i = \\{var}; $i < \\{limit}; $i \\{arithm}= \\{stride}); // Make sure to run with loop opts.
            \s
                if ($flag) {
                    $a = \\{val1};
                } else {
                    $a = \\{val2};
                }
                // Region
            \s
                // --- BLOCK start ---
            \s
                // CMoveI(Bool(CmpI(flag2))), a, 23)
                if ($flag2) {
                    $b = \\{val1}; // Use b = a to have an additional Bool -> then Split If only clones down CmpI
                } else {
                    $b = \\{val3};
                }
                $iFld = $b; // iFld = CMoveI -> make sure CMoveI is inside BLOCK
            \s
                // --- BLOCK end ---
            \s
                if ($a > \\{val3}) { // If to split -> need to empty BLOCK
                    $iFld = \\{val1};
                }
            \s
                if ($flag2) { // Reuse of Bool(CmpI(flag2)) such that we need to clone CmpI(flag2) down
                    $iFld = \\{val2};
                }
            """;
        String template=statics+method;
        String template_com= avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        String limit = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String stride = getRandomValueAsString(INTEGER_VALUES_NON_ZERO);
        String arithm = getRandomValue(new String[]{"+", "-"});
        String bool1 = getRandomValue(new String[]{"false", "true"});
        String bool2 = getRandomValue(new String[]{"false", "true"});
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("limit", limit);
        replacements.put("stride", stride);
        replacements.put("arithm", arithm);
        replacements.put("bool1", bool1);
        replacements.put("bool2", bool2);
        replacements.put("var", variable);
        return performReplacements(template_com,replacements);
    }
}
