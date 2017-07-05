/*
 * Copyright (c) 1996, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.io;

public class ByteToCharISO2022JP extends ByteToCharJIS0208 {

    private static final int ASCII = 0;                 // ESC ( B
    private static final int JISX0201_1976 = 1;         // ESC ( J
    private static final int JISX0208_1978 = 2;         // ESC $ @
    private static final int JISX0208_1983 = 3;         // ESC $ B
    private static final int JISX0201_1976_KANA = 4;    // ESC ( I
    private static final int SHIFTOUT = 5;              // SO (0x0e)

    private int currentState;
    private int savedSize;
    private byte[] savedBytes;

    public ByteToCharISO2022JP() {
        super();
        savedBytes = new byte[2];
        currentState = ASCII;
        savedSize = 0;
    }

    public int flush(char [] output, int outStart, int outEnd)
        throws MalformedInputException
    {
        if (savedSize != 0) {
            savedSize = 0;
            currentState = ASCII;
            badInputLength = 0;
            throw new MalformedInputException();
        }
        byteOff = charOff = 0;
        return 0;
    }


    /**
     * Character conversion
     */
    public int convert(byte[] input, int inOff, int inEnd,
                       char[] output, int outOff, int outEnd)
        throws UnknownCharacterException, MalformedInputException,
               ConversionBufferFullException
    {
        int previousState = ASCII;
        int inputSize = 0;
        char outputChar = '\uFFFD';
        // readOff keeps the actual buffer's pointer.
        // byteOff keeps original buffer's pointer.
        int readOff = byteOff = inOff;

        if (savedSize != 0) {
            if (savedBytes[0] == 0x1b) { // ESC
                if ((savedSize == 2 &&
                     (savedBytes[1] == 0x28 &&
                      input[0] != 'B' &&
                      input[0] != 'J' &&
                      input[0] != 'I') &&
                     (savedBytes[1] == 0x24 &&
                      input[0] != '@' &&
                      input[0] != 'B')) ||
                    ((savedSize == 1) &&
                     (input[0] != 0x28 &&
                      input[0] != 0x24))) {
                    badInputLength = 0;
                    throw new MalformedInputException();
                }
                if ((inEnd - inOff) == 1 && savedSize == 1 &&
                    savedBytes[0] == 0x1b) {
                    savedSize = 2;
                    savedBytes[1] = input[0];
                    byteOff++;
                    return 0;
                }
            }
            byte[] newBuf = new byte[inEnd - inOff + savedSize];
            for (int i = 0; i < savedSize; i++) {
                newBuf[i] = savedBytes[i];
            }
            System.arraycopy(input, inOff, newBuf, savedSize, inEnd - inOff);
            byteOff -= savedSize;
            input = newBuf;
            inOff = 0;
            inEnd = newBuf.length;
            savedSize = 0;
        }

        charOff = outOff;
        readOff = inOff;

        while(readOff < inEnd) {
            int byte1, byte2, byte3;
            boolean noOutput = false;

            // Is there room in the output buffer for the result?
            if (charOff >= outEnd) {
                throw new ConversionBufferFullException();
            }

            // Get the input byte
            byte1 = input[readOff] & 0xFF;
            inputSize = 1;

            if ((byte1 & (byte)0x80) != 0){
                badInputLength = 1;
                throw new MalformedInputException();
            }

            // Is this a escape sequence?
            while (byte1 == 0x1b || byte1 == 0x0e || byte1 == 0x0f) {
                if (byte1 == 0x1b){  // ESC
                    if (readOff + inputSize + 1 >= inEnd) {
                        if (readOff + inputSize >= inEnd) {
                            savedSize = 1;
                            savedBytes[0] = (byte)byte1;
                        } else {
                            savedSize = 2;
                            savedBytes[0] = (byte)byte1;
                            savedBytes[1] = input[readOff + inputSize];
                            inputSize++;
                        }
                        break;
                    }
                    byte2 = input[readOff + inputSize] & 0xFF;
                    inputSize++;
                    if ((byte2 & (byte)0x80) != 0){
                        badInputLength = 2;
                        throw new MalformedInputException();
                    }
                    if (byte2 == 0x28){
                        byte3 = input[readOff + inputSize] & 0xFF;
                        inputSize++;
                        if (byte3 == 'B'){
                            currentState = ASCII;
                        } else if (byte3 == 'J'){
                            currentState = JISX0201_1976;
                        } else if (byte3 == 'I'){
                            currentState = JISX0201_1976_KANA;
                        } else {
                            // illegal ESC sequence
                            badInputLength = 3;
                            throw new MalformedInputException();
                        }
                    } else if (byte2 == '$'){
                        byte3 = input[readOff + inputSize] & 0xFF;
                        inputSize++;
                        if ((byte3 & (byte)0x80) != 0){
                            badInputLength = 3;
                            throw new MalformedInputException();
                        }
                        if (byte3 == '@'){
                            currentState = JISX0208_1978;
                        } else if (byte3 == 'B'){
                            currentState = JISX0208_1983;
                        } else {
                            // illegal ESC sequence
                            badInputLength = 3;
                            throw new MalformedInputException();
                        }
                    } else {
                        // illegal ESC sequence
                        badInputLength = 2;
                        throw new MalformedInputException();
                    }
                    if (readOff + inputSize >= inEnd) {
                        noOutput = true;
                        break;
                    } else {
                        byte1 = input[readOff + inputSize];
                        inputSize++;
                    }
                } else if (byte1 == 0x0e){  // shift out for one byte kana
                    previousState = currentState;
                    currentState = SHIFTOUT;
                    if (readOff + inputSize >= inEnd) {
                        noOutput = true;
                        break;
                    }
                    byte1 = input[readOff + inputSize];
                    inputSize++;
                    if ((byte1 & (byte)0x80) != 0){
                        badInputLength = 1;
                        throw new MalformedInputException();
                    }
                } else if (byte1 == 0x0f){  // shift in for previous mode
                    currentState = previousState;
                    if (readOff + inputSize >= inEnd) {
                        noOutput = true;
                        break;
                    }
                    byte1 = input[readOff + inputSize];
                    inputSize++;
                    if ((byte1 & (byte)0x80) != 0){
                        badInputLength = 1;
                        throw new MalformedInputException();
                    }
                }
            }
            if (noOutput || savedSize != 0) {
                byteOff += inputSize;
                break;
            }
            noOutput = false;
            switch (currentState){
              case ASCII:
                outputChar = (char)(byte1 & 0xff);
                break;
              case JISX0201_1976:
                switch (byte1) {
                  case 0x5c:
                    outputChar = '\u00a5';
                    break;
                  case 0x7e:
                    outputChar = '\u203e';
                    break;
                  default:
                    outputChar = (char)byte1;
                    break;
                }
                break;
              case JISX0208_1978:
              case JISX0208_1983:
                if (readOff + inputSize >= inEnd) {
                    savedSize = 1;
                    savedBytes[0] = (byte)byte1;
                    break;
                }
                byte2 = input[readOff + inputSize] & 0xff;
                inputSize++;
                if ((byte2 & (byte)0x80) != 0){
                    badInputLength = 1;
                    throw new MalformedInputException();
                }
                // jisx0208Chars table convert FULLWIDTH_REVERSE_SOLIDUS
                // 0x2140 to REVERSE_SOLIDUS (BACKSLASH) 0x5c.
                // This behavior causes problem because
                // 0x5c is special escape character for java.
                if (byte1 == 0x21 && byte2 == 0x40) {
                    outputChar = '\uFF3C';
                } else {
                    try {
                        outputChar = getUnicode(byte1, byte2);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        outputChar = '\uFFFD';
                    }
                }
                break;
              case JISX0201_1976_KANA:
              case SHIFTOUT:
                if (byte1 > 0x60) {
                    badInputLength = 1;
                    throw new MalformedInputException();
                }
                outputChar = (char)(byte1 + 0xff40);
                break;
            }

            if (savedSize != 0) {
                byteOff += inputSize;
                break;
            }

            if (outputChar == '\uFFFD') {
                if (subMode)
                    outputChar = subChars[0];
                else {
                    badInputLength = inputSize;
                    throw new UnknownCharacterException();
                }
            }
            readOff += inputSize;
            byteOff += inputSize;
            output[charOff++] = outputChar;
        }

        return charOff - outOff;
    }

    public void reset() {
        byteOff = charOff = 0;
        currentState = ASCII;
        savedSize = 0;
    }

    public String getCharacterEncoding() {
        return "ISO2022JP";
    }
}
