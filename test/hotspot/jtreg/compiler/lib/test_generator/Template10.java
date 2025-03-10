package compiler.lib.test_generator;
import static compiler.lib.test_generator.TemplateGenerator.performReplacements;
import java.util.HashMap;
import java.util.Map;
import static compiler.lib.test_generator.InputTemplate.*;
public class Template10 extends Template {
    public Template10() {}
    public String getTemplate(String variable){
        String statics= """
                boolean $flag=\\{bool};
                long $lFld=\\{val1};
                int[]iArr=new int[\\{size}];
                """;
        String method="""
            long $l1 = \\{val2};
            long $l2 = \\{val3};
                int $zero = \\{val2};
                int $limit = \\{val3};
                for (; $limit < \\{limit1}; $limit \\{arithm}= \\{stride});
                for (int $i = \\{val3}; $i < $limit; $i++) {
                    $zero = 0;
                }
                for (int $i = \\{var}; $i < \\{size}; $i++) {
                    iArr[$i] = \\{val2}; // Just a reason to pre/main/post (trigger more loop opts)
                    if ($flag) { // Triggers Loop Peeling before CCP
                       $l1=$zero;
                    }
                    if ($zero > $i) { // Folded away after CCP.
                        // DivLs add 30 to the loop body count and we hit LoopUnrollLimit. Add more
                        // statements/DivLs if you want to use a higher LoopUnrollLimit value.
                        // After CCP, these statements are folded away and we can unroll this loop.
                        $l1 /= $lFld;
                        $l2 /= $lFld;
                    }
                }
            """;
        String template=statics+method;
        String template_com= avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        String limit1 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String limit2 = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String size = getRandomValueAsString(ARRAY_SIZES);
        String stride = getRandomValueAsString(INTEGER_VALUES_NON_ZERO);
        String arithm = getRandomValue(new String[]{"+", "-"});
        String bool = getRandomValue(new String[]{"false", "true"});
        replacements.put("val1", val1);
        replacements.put("val3", val3);
        replacements.put("stride", stride);
        replacements.put("arithm", arithm);
        replacements.put("bool", bool);
        replacements.put("val2", val2);
        replacements.put("limit1", limit1);
        replacements.put("limit2", limit2);
        replacements.put("size", size);
        replacements.put("var", variable);
        return performReplacements(template_com,replacements);
    }
}
