package compiler.lib.test_generator;
import static compiler.lib.test_generator.TemplateGenerator.performReplacements;
import java.util.HashMap;
import java.util.Map;
import static compiler.lib.test_generator.InputTemplate.*;
public class Template12 extends Template {
    public String getTemplate(String variable){
        String statics= """
                int $out;=\\{val1};
                boolean $cond = \\{bool};
                int $i = \\{var};
                """;
        String method="""
            while ($i < \\{max_val1}) {
                int $j = \\{var};
                while ($j < \\{max_val2}) {
                    $out = \\{val2};
                    if ($cond) {
                        break;
                    }
                    int $k = \\{var};
                    while ($k < \\{max_val3}) {
                        $k++;
                    }
                    $j++;
                }
                i++;
            }
            """;
        String template=statics+method;
        String template_com= avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String max_val1 = getRandomValueAsString(INTEGER_VALUES);
        String max_val2 = getRandomValueAsString(INTEGER_VALUES);
        String max_val3 = getRandomValueAsString(INTEGER_VALUES);
        String bool = getRandomValue(new String[]{"true", "false"});
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("bool", bool);
        replacements.put("max_val1", max_val1);
        replacements.put("max_val2", max_val2);
        replacements.put("max_val3", max_val3);
        replacements.put("var", variable);
        return performReplacements(template_com,replacements);
    }
}
