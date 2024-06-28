package compiler.lib.test_generator;

import java.math.BigInteger;
import java.util.Map;

import static compiler.lib.test_generator.TemplateGenerator.fillTemplate;

public class InputTemplate {
    static  BigInteger num;
    static  int init = 0;
    static  int limit = 0;
    static  int stride = 0;
    static  String arithm = "";

    public InputTemplate(BigInteger num, @TemplateGenerator.IntParam int init, @TemplateGenerator.IntParam int limit, @TemplateGenerator.IntParam(nonZero = true) int stride, @TemplateGenerator.StringParam(values = {"+", "-"}) String arithm) {
        this.num = num;
        this.init = init;
        this.limit = limit;
        this.stride = stride;
        this.arithm = arithm;
    }
    static String thing = "...";


    public static CodeSegment getTemplate() {
        Result res=new Result();

        res.statics = """
                ...
                """;

        res.call = "...";


        res.method = """
                 ...
                """;

        Map<String, String> replacements = getRandomReplacements(num);
        res.call =fillTemplate(res.call, Map.of(
                "num", num.toString()));
        res.method = fillTemplate(res.method, replacements);

        return new CodeSegment(res.statics,res.call,res.method,num);// String with replacements or a class with statics, call and method as fields;

    }
    private static Map<String, String> getRandomReplacements(BigInteger num) {

        return Map.of();
    }
}
