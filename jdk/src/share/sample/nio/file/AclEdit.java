/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Sample utility for editing a file's ACL.
 */

public class AclEdit {

    // parse string as list of ACE permissions separated by /
    static Set<AclEntryPermission> parsePermissions(String permsString) {
        Set<AclEntryPermission> perms = new HashSet<AclEntryPermission>();
        String[] result = permsString.split("/");
        for (String s : result) {
            if (s.equals(""))
                continue;
            try {
                perms.add(AclEntryPermission.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException x) {
                System.err.format("Invalid permission '%s'\n", s);
                System.exit(-1);
            }
        }
        return perms;
    }

    // parse string as list of ACE flags separated by /
    static Set<AclEntryFlag> parseFlags(String flagsString) {
        Set<AclEntryFlag> flags = new HashSet<AclEntryFlag>();
        String[] result = flagsString.split("/");
        for (String s : result) {
            if (s.equals(""))
                continue;
            try {
                flags.add(AclEntryFlag.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException x) {
                System.err.format("Invalid flag '%s'\n", s);
                System.exit(-1);
            }
        }
        return flags;
    }

    // parse ACE type
    static AclEntryType parseType(String typeString) {
        // FIXME: support audit and alarm types in the future
        if (typeString.equalsIgnoreCase("allow"))
            return AclEntryType.ALLOW;
        if (typeString.equalsIgnoreCase("deny"))
            return AclEntryType.DENY;
        System.err.format("Invalid type '%s'\n", typeString);
        System.exit(-1);
        return null;    // keep compiler happy
    }

    /**
     * Parse string of the form:
     *   [user|group:]<username|groupname>:<perms>[:flags]:<allow|deny>
     */
    static AclEntry parseAceString(String s,
                                   UserPrincipalLookupService lookupService)
    {
        String[] result = s.split(":");

        // must have at least 3 components (username:perms:type)
        if (result.length < 3)
            usage();

        int index = 0;
        int remaining = result.length;

        // optional first component can indicate user or group type
        boolean isGroup = false;
        if (result[index].equalsIgnoreCase("user") ||
            result[index].equalsIgnoreCase("group"))
        {
            if (--remaining < 3)
                usage();
            isGroup = result[index++].equalsIgnoreCase("group");
        }

        // user and permissions required
        String userString = result[index++]; remaining--;
        String permsString = result[index++]; remaining--;

        // flags are optional
        String flagsString = "";
        String typeString = null;
        if (remaining == 1) {
            typeString = result[index++];
        } else {
            if (remaining == 2) {
                flagsString = result[index++];
                typeString = result[index++];
            } else {
                usage();
            }
        }

        // lookup UserPrincipal
        UserPrincipal user = null;
        try {
            user = (isGroup) ?
                lookupService.lookupPrincipalByGroupName(userString) :
                lookupService.lookupPrincipalByName(userString);
        } catch (UserPrincipalNotFoundException x) {
            System.err.format("Invalid %s '%s'\n",
                ((isGroup) ? "group" : "user"),
                userString);
            System.exit(-1);
        } catch (IOException x) {
            System.err.format("Lookup of '%s' failed: %s\n", userString, x);
            System.exit(-1);
        }

        // map string representation of permissions, flags, and type
        Set<AclEntryPermission> perms = parsePermissions(permsString);
        Set<AclEntryFlag> flags = parseFlags(flagsString);
        AclEntryType type = parseType(typeString);

        // build the ACL entry
        return AclEntry.newBuilder()
            .setType(type)
            .setPrincipal(user)
            .setPermissions(perms).setFlags(flags).build();
    }

    static void usage() {
        System.err.println("usage: java AclEdit [ACL-operation] file");
        System.err.println("");
        System.err.println("Example 1: Prepends access control entry to the begining of the myfile's ACL");
        System.err.println("       java AclEdit A+alice:read_data/read_attributes:allow myfile");
        System.err.println("");
        System.err.println("Example 2: Remove the entry at index 6 of myfile's ACL");
        System.err.println("       java AclEdit A6- myfile");
        System.err.println("");
        System.err.println("Example 3: Replace the entry at index 2 of myfile's ACL");
        System.err.println("       java AclEdit A2=bob:write_data/append_data:deny myfile");
        System.exit(-1);
    }

    static enum Action {
        PRINT,
        ADD,
        REMOVE,
        REPLACE;
    }

    /**
     * Main class: parses arguments and prints or edits ACL
     */
    public static void main(String[] args) throws IOException {
        Action action = null;
        int index = -1;
        String entryString = null;

        // parse arguments
        if (args.length < 1 || args[0].equals("-help") || args[0].equals("-?"))
            usage();

        if (args.length == 1) {
            action = Action.PRINT;
        } else {
            String s = args[0];

            // A[index]+entry
            if (Pattern.matches("^A[0-9]*\\+.*", s)) {
                String[] result = s.split("\\+", 2);
                if (result.length == 2) {
                    if (result[0].length() < 2) {
                        index = 0;
                    } else {
                        index = Integer.parseInt(result[0].substring(1));
                    }
                    entryString = result[1];
                    action = Action.ADD;
                }
            }

            // Aindex-
            if (Pattern.matches("^A[0-9]+\\-", s)) {
                String[] result = s.split("\\-", 2);
                if (result.length == 2) {
                    index = Integer.parseInt(result[0].substring(1));
                    entryString = result[1];
                    action = Action.REMOVE;
                }
            }

            // Aindex=entry
            if (Pattern.matches("^A[0-9]+=.*", s)) {
                String[] result = s.split("=", 2);
                if (result.length == 2) {
                    index = Integer.parseInt(result[0].substring(1));
                    entryString = result[1];
                    action = Action.REPLACE;
                }
            }
        }
        if (action == null)
            usage();

        int fileArg = (action == Action.PRINT) ? 0 : 1;
        Path file = Paths.get(args[fileArg]);

        // read file's ACL
        AclFileAttributeView view =
            Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (view == null) {
            System.err.println("ACLs not supported on this platform");
            System.exit(-1);
        }
        List<AclEntry> acl = view.getAcl();

        switch (action) {
            // print ACL
            case PRINT : {
                for (int i=0; i<acl.size(); i++) {
                    System.out.format("%5d: %s\n", i, acl.get(i));
                }
                break;
            }

            // add ACE to existing ACL
            case ADD: {
                AclEntry entry = parseAceString(entryString, file
                    .getFileSystem().getUserPrincipalLookupService());
                if (index >= acl.size()) {
                    acl.add(entry);
                } else {
                    acl.add(index, entry);
                }
                view.setAcl(acl);
                break;
            }

            // remove ACE
            case REMOVE: {
                if (index >= acl.size()) {
                    System.err.format("Index '%d' is invalid", index);
                    System.exit(-1);
                }
                acl.remove(index);
                view.setAcl(acl);
                break;
            }

            // replace ACE
            case REPLACE: {
                if (index >= acl.size()) {
                    System.err.format("Index '%d' is invalid", index);
                    System.exit(-1);
                }
                AclEntry entry = parseAceString(entryString, file
                    .getFileSystem().getUserPrincipalLookupService());
                acl.set(index, entry);
                view.setAcl(acl);
                break;
            }
        }
    }
}
