package compiler.lib.ir_framework.shared;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SystemProperty {
    public record Mode(boolean caseSensitive, String def) {
        static public Mode Make() {
            return new Mode(false, null);
        }

        public Mode withCaseSensitive(boolean c) {
            return new Mode(c, this.def);
        }

        public Mode withDefault(String def) {
            return new Mode(this.caseSensitive, def);
        }

        public static Mode CASE_INSENSITIVE_EMPTY_DEFAULT = Make().withCaseSensitive(false).withDefault("");
    }

    static public String getCaseInsensitive(Mode mode, String... keys) {
        java.util.function.Function<String, String> normalize =
                mode.caseSensitive()
                        ? (x -> x)
                        : String::toLowerCase;
        List<String> normalized_keys = Arrays.stream(keys).map(normalize).toList();
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (k instanceof String && v instanceof String) {
                String ks = normalize.apply((String)k);
                if (normalized_keys.contains(ks)) {
                    return (String)v;
                }
            }
        }
        return mode.def();
    }

    static public String getTestList() {
        return getCaseInsensitive(Mode.CASE_INSENSITIVE_EMPTY_DEFAULT, "test", "tests");
    }

    static public String getExcludeList() {
        return getCaseInsensitive(Mode.CASE_INSENSITIVE_EMPTY_DEFAULT, "exclude", "excludes");
    }
}
