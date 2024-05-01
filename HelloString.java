import javax.management.RuntimeErrorException;

class HelloString {
    public static void main(String[] args) {
        String haystack;
        String needle;

        if (false) {
            for (Encoding ae : Encoding.values())
            //Encoding ae = Encoding.UU;
                (new HelloString(ae))
                    .test1()
                    .test2();
        } else if (false) {
            needle = "1234567890";
            needle = needle + needle + needle + needle + needle + "1";
            haystack = "Hi Hello, "+needle+"World!1234123456789012345678";
        } else if (false) {
            // [acb][adb]
            haystack = "aaacbb";
            needle = "acb";
            System.out.println(haystack.indexOf2(needle, 1));
        } else if (true) {
            HelloString t = new HelloString(Encoding.LL);
            needle = t.newNeedle(3, -1);
            haystack = t.newHaystack(5, needle, 1);
            System.out.println(haystack.indexOf2(needle, 1));
        }

        //System.out.println(args[1].indexOf2("Hello\u0f0f", 1));
        //String foo = args[1]+"\u0f0f";

    }

    HelloString test0() { // Test 'trivial cases'
        // Need to disable checks in String.java
        // if (0==needle_len) return haystack_off;
        if (3 != "Hello".indexOf2("", 3)) {System.out.println("FAILED: if (0==needle_len) return haystack_off");}
        //if (0==haystack_len) return -1;
        if (-1 != "".indexOf2("Hello", 3)) {System.out.println("FAILED: if (0==haystack_len) return -1");}
        //if (needle_len>haystack_len) return -1;
        if (-1 != "Hello".indexOf2("HelloWorld", 3)) {System.out.println("FAILED: if (needle_len>haystack_len) return -1");}
        return this;
    }

    HelloString test1() { // Test expected to find
        int scope = 32*5+16+8;
        for (int nSize = 3; nSize<scope; nSize++) {
            for (int hSize = nSize; hSize<scope; hSize++) {
                String needle = newNeedle(nSize, -1);
                for (int i = 1; i<hSize-nSize; i++) {
                    //if (hSize-(nSize+i)<32) continue; //Not implemented
                    System.out.println("("+ae.name()+") Trying needle["+nSize+"] in haystack["+hSize+"] at offset["+i+"]");
                    String haystack = newHaystack(hSize, needle, i);
                    int found = haystack.indexOf2(needle, 1);
                    if (i != found) {
                        System.out.println("    FAILED: " + found + " " + haystack + "["+needle+"]");
                    }
                }
            }
        }
        return this;
    }

    HelloString test2() { // Test needle with one mismatched character
        int scope = 32*5+16+8;
        for (int nSize = 3; nSize<scope; nSize++) {
            for (int hSize = nSize; hSize<scope; hSize++) {
                String needle = newNeedle(nSize, -1);
                for (int badPosition = 1; badPosition < nSize-1; badPosition+=1) {
                    String badNeedle = newNeedle(nSize, badPosition);
                    for (int i = 1; i<hSize-nSize; i++) {
                        //if (hSize-(nSize+i)<16) continue; //Not implemented
                        System.out.println("("+ae.name()+") Trying needle["+nSize+"]["+badPosition+"] in haystack["+hSize+"] at offset["+i+"]");
                        String haystack = newHaystack(hSize, needle, i);
                        int found = haystack.indexOf2(badNeedle, 1);
                        if (-1 != found) {
                            System.out.println("    FAILED: False " + found + " " + haystack + "["+needle+"]["+badNeedle+"]");
                        }
                    }
                }
            }
        }
        return this;
    }

    enum Encoding {LL, UU, UL; }
    final char a;
    final char aa;
    final char b;
    final char c;
    final char d;
    final Encoding ae;
    HelloString(Encoding _ae) {
        ae = _ae;
        switch (ae) {
            case LL:
                a = 'a';
                aa = a;
                b = 'b';
                c = 'c';
                d = 'd';
                break;
            case UU:
                a = '\u0061';
                aa = a;
                b = '\u0062';
                c = '\u1063';
                d = '\u0064';
                break;
            default: //case UL:
                a = 'a';
                aa = '\u1061';
                b = 'b';
                c = 'c';
                d = 'd';
                break;
        }
    }
    // aaaa+accc(d?)cccb+bbbbbbbbbb
    String newNeedle(int size, int badPosition) {
        StringBuilder needle = new StringBuilder(size);
        needle.append(a);
        for (int i=1; i<size-1; i++) {
            if (i == badPosition)
                needle.append(d);
            else
                needle.append(c);
        }
        needle.append(b);
        return needle.toString();
    }

    String newHaystack(int size, String needle, int nPosition) {
        if (nPosition+needle.length()>size) {throw new RuntimeException("Fix testcase");}
        StringBuilder haystack = new StringBuilder(size);
        int i = 0;
        for (; i<nPosition; i++) {
            haystack.append(aa);
        }
        haystack.append(needle);
        i += needle.length();
        for (; i<size; i++) {
            haystack.append(b);
        }
        return haystack.toString();
    }
}

// ./build/linux-x86_64-server-fastdebug/images/jdk/bin/java -Xcomp -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions HelloString.java
