package compiler.lib.test_generator;

import java.util.HashMap;
import java.util.Map;

import static compiler.lib.test_generator.InputTemplate.*;

public class Template4 extends Template{
    public Template4(){}
    public String getTemplate(String variable){
        String statics= """
            int $x, $y;
            boolean $flag=\\{bool};
            """;
        String method="""
            int $a;
            if ($flag) {
                $a = \\{val1};
            } else {
                $a = \\{val2};
            }
            // y = 34; // Make it more interesting
            if ($a > \\{val2}) {
                $x = \\{val3};
            } else {
                $x = \\{val4};
            }
            """;
        String template=statics+method;
        String template_com= avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(integerValues);
        String val2 = getRandomValueAsString(integerValues);
        String val3 = getRandomValueAsString(integerValues);
        String val4 = getRandomValueAsString(integerValues);
        String bool = getRandomValue(new String[]{"false", "true"});
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("val4", val4);
        replacements.put("bool", bool);



        return doReplacements(template_com,replacements);
    }
}
