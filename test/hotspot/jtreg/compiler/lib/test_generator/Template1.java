package compiler.lib.test_generator;
import java.util.HashMap;
import java.util.Map;
import static compiler.lib.test_generator.InputTemplate.*;
public class Template1 extends Template {
    public String getTemplate(String variable){
        String statics= """
                int $a =\\{val};
                long $b = \\{var};
                """;
        String method="""
            do {
                $a--;
                $b++;
            } while ($a > 0);
            """;
        String template=statics+method;
        String template_com= avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val = getRandomValueAsString(integerValues);
        replacements.put("val", val);
        replacements.put("var", variable);
        return doReplacements(template_com,replacements);
    }
}
