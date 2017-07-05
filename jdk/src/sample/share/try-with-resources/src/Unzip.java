/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation, and proper error handling, might not be present in
 * this sample code.
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Extract (unzip) a file to the current directory.
 */
public class Unzip {

    /**
     * The main method for the Unzip program. Run the program with an empty
     * argument list to see possible arguments.
     *
     * @param args the argument list for {@code Unzip}.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: Unzip zipfile");
        }
        final Path destDir = Paths.get(".");
        /*
         * Create AutoCloseable FileSystem. It will be closed automatically
         * after the try block.
         */
        try (FileSystem zipFileSystem = FileSystems.newFileSystem(Paths.get(args[0]), null)) {

            Path top = zipFileSystem.getPath("/");
            Files.walk(top).skip(1).forEach(file -> {
                Path target = destDir.resolve(top.relativize(file).toString());
                System.out.println("Extracting " + target);
                try {
                    Files.copy(file, target, REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException | IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
