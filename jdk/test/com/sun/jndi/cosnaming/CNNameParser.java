/* @test
 * @bug 4238914
 * @summary Tests that JNDI/COS naming parser supports the syntax
 * defined in the new INS standard.
 */

import javax.naming.*;

public class CNNameParser {

    public static void main(String[] args) throws Exception {

        NameParser parser = new com.sun.jndi.cosnaming.CNNameParser();

        for (int i = 0; i < compounds.length; i++) {
            checkCompound(parser, compounds[i], compoundComps[i]);
        }
    }

    private static void checkName(Name name, String[] comps) throws Exception {
        if (name.size() != comps.length) {
            throw new Exception(
                "test failed; incorrect component count in " + name + "; " +
                "expecting " + comps.length + " got " + name.size());
        }
        for (int i = 0; i < name.size(); i++) {
            if (!comps[i].equals(name.get(i))) {
                throw new Exception (
                    "test failed; invalid component in " + name + "; " +
                    "expecting '" + comps[i] + "' got '" + name.get(i) + "'");
            }
        }
    }

    private static void checkCompound(NameParser parser,
        String input, String[] comps) throws Exception {
        checkName(parser.parse(input), comps);
    }

    private static final String[] compounds = {
        "a/b/c",
        "a.b/c.d",
        "a",
        ".",
        "a.",
        "c.d",
        ".e",
        "a/x\\/y\\/z/b",
        "a\\.b.c\\.d/e.f",
        "a/b\\\\/c",
        "x\\\\.y",
        "x\\.y",
        "x.\\\\y",
        "x.y\\\\",
        "\\\\x.y",
        "a.b\\.c/d"
    };

    private static final String[][] compoundComps = {
        {"a", "b", "c"},
        {"a.b", "c.d"},
        {"a"},
        {"."},
        {"a"},
        {"c.d"},
        {".e"},
        {"a", "x\\/y\\/z", "b"},
        {"a\\.b.c\\.d", "e.f"},
        {"a", "b\\\\", "c"},
        {"x\\\\.y"},
        {"x\\.y"},
        {"x.\\\\y"},
        {"x.y\\\\"},
        {"\\\\x.y"},
        {"a.b\\.c", "d"},
    };
}
