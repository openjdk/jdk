package compiler.lib.test_generator;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static compiler.lib.test_generator.InputTemplate.doReplacements;

public abstract class Template {
    public Template() {}

    public static String reassemble(String dec, String cod) {
        String assembe_template = """
                \\{declare}
                \\{code}
                """;
        Map<String, String> replacement_code = Map.ofEntries(
                Map.entry("declare", dec),
                Map.entry("code", cod)
        );
        return doReplacements(assembe_template, replacement_code);
    }

    public static String avoid_conflict(String temp,int num){
        StringBuffer result = new StringBuffer();
        String regex="\\$(\\w+)";
        Pattern pat = Pattern.compile(regex);
        Matcher mat = pat.matcher(temp);
        while(mat.find()){
            String replacement = mat.group(1)+num;
            mat.appendReplacement(result, replacement);
        }
        mat.appendTail(result);
        return result.toString();
    }

    public String getTemplate(List<String> values){
        return "";
    }
}
