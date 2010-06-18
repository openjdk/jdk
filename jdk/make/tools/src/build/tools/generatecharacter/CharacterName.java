package build.tools.generatecharacter;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;

public class CharacterName {

    public static void main(String[] args) {
        FileReader reader = null;
        try {
            if (args.length != 2) {
                System.err.println("Usage: java CharacterName UniocdeData.txt uniName.dat");
                System.exit(1);
            }

            reader = new FileReader(args[0]);
            BufferedReader bfr = new BufferedReader(reader);
            String line = null;

            StringBuilder namePool = new StringBuilder();
            byte[] cpPoolBytes = new byte[0x100000];
            ByteBuffer cpBB = ByteBuffer.wrap(cpPoolBytes);
            int lastCp = 0;
            int cpNum = 0;

            while ((line = bfr.readLine()) != null) {
                if (line.startsWith("#"))
                    continue;
                UnicodeSpec spec = UnicodeSpec.parse(line);
                if (spec != null) {
                    int cp = spec.getCodePoint();
                    String name = spec.getName();
                    cpNum++;
                    if (name.equals("<control>") && spec.getOldName() != null) {
                        if (spec.getOldName().length() != 0)
                            name = spec.getOldName();
                        else
                            continue;
                    } else if (name.startsWith("<")) {
                        /*
                          3400    <CJK Ideograph Extension A, First>
                          4db5    <CJK Ideograph Extension A, Last>
                          4e00    <CJK Ideograph, First>
                          9fc3    <CJK Ideograph, Last>
                          ac00    <Hangul Syllable, First>
                          d7a3    <Hangul Syllable, Last>
                          d800    <Non Private Use High Surrogate, First>
                          db7f    <Non Private Use High Surrogate, Last>
                          db80    <Private Use High Surrogate, First>
                          dbff    <Private Use High Surrogate, Last>
                          dc00    <Low Surrogate, First>
                          dfff    <Low Surrogate, Last>
                          e000    <Private Use, First>
                          f8ff    <Private Use, Last>
                         20000    <CJK Ideograph Extension B, First>
                         2a6d6    <CJK Ideograph Extension B, Last>
                         f0000    <Plane 15 Private Use, First>
                         ffffd    <Plane 15 Private Use, Last>
                        */
                        continue;
                    }

                    if (cp == lastCp + 1) {
                        cpBB.put((byte)name.length());
                    } else {
                        cpBB.put((byte)0);  // segment start flag
                        cpBB.putInt((name.length() << 24) | (cp & 0xffffff));
                    }
                    namePool.append(name);
                    lastCp = cp;
                }
            }

            byte[] namePoolBytes = namePool.toString().getBytes("ASCII");
            int cpLen = cpBB.position();
            int total = cpLen + namePoolBytes.length;

            DataOutputStream dos = new DataOutputStream(
                                       new DeflaterOutputStream(
                                           new FileOutputStream(args[1])));
            dos.writeInt(total);  // total
            dos.writeInt(cpLen);  // nameOff
            dos.write(cpPoolBytes, 0, cpLen);
            dos.write(namePoolBytes);
            dos.close();

        } catch (Throwable e) {
            System.out.println("Unexpected exception:");
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ee) { ee.printStackTrace(); }
            }
        }
    }
}
