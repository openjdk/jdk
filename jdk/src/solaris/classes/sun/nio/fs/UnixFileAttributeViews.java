/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.nio.fs;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

import static sun.nio.fs.UnixNativeDispatcher.*;

class UnixFileAttributeViews {

    static class Basic extends AbstractBasicFileAttributeView {
        protected final UnixPath file;
        protected final boolean followLinks;

        Basic(UnixPath file, boolean followLinks) {
            this.file = file;
            this.followLinks = followLinks;
        }

        @Override
        public BasicFileAttributes readAttributes() throws IOException {
            file.checkRead();
            try {
                 UnixFileAttributes attrs =
                     UnixFileAttributes.get(file, followLinks);
                 return attrs.asBasicFileAttributes();
            } catch (UnixException x) {
                x.rethrowAsIOException(file);
                return null;    // keep compiler happy
            }
        }

        @Override
        public void setTimes(FileTime lastModifiedTime,
                             FileTime lastAccessTime,
                             FileTime createTime) throws IOException
        {
            // null => don't change
            if (lastModifiedTime == null && lastAccessTime == null) {
                // no effect
                return;
            }

            // permission check
            file.checkWrite();

            int fd = file.openForAttributeAccess(followLinks);
            try {
                // if not changing both attributes then need existing attributes
                if (lastModifiedTime == null || lastAccessTime == null) {
                    try {
                        UnixFileAttributes attrs = UnixFileAttributes.get(fd);
                        if (lastModifiedTime == null)
                            lastModifiedTime = attrs.lastModifiedTime();
                        if (lastAccessTime == null)
                            lastAccessTime = attrs.lastAccessTime();
                    } catch (UnixException x) {
                        x.rethrowAsIOException(file);
                    }
                }

                // uptime times
                long modValue = lastModifiedTime.to(TimeUnit.MICROSECONDS);
                long accessValue= lastAccessTime.to(TimeUnit.MICROSECONDS);

                boolean retry = false;
                try {
                    futimes(fd, accessValue, modValue);
                } catch (UnixException x) {
                    // if futimes fails with EINVAL and one/both of the times is
                    // negative then we adjust the value to the epoch and retry.
                    if (x.errno() == UnixConstants.EINVAL &&
                        (modValue < 0L || accessValue < 0L)) {
                        retry = true;
                    } else {
                        x.rethrowAsIOException(file);
                    }
                }
                if (retry) {
                    if (modValue < 0L) modValue = 0L;
                    if (accessValue < 0L) accessValue= 0L;
                    try {
                        futimes(fd, accessValue, modValue);
                    } catch (UnixException x) {
                        x.rethrowAsIOException(file);
                    }
                }
            } finally {
                close(fd);
            }
        }
    }

    private static class Posix extends Basic implements PosixFileAttributeView {
        private static final String PERMISSIONS_NAME = "permissions";
        private static final String OWNER_NAME = "owner";
        private static final String GROUP_NAME = "group";

        Posix(UnixPath file, boolean followLinks) {
            super(file, followLinks);
        }

        final void checkReadExtended() {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                file.checkRead();
                sm.checkPermission(new RuntimePermission("accessUserInformation"));
            }
        }

        final void checkWriteExtended() {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                file.checkWrite();
                sm.checkPermission(new RuntimePermission("accessUserInformation"));
            }
        }

        @Override
        public String name() {
            return "posix";
        }

        @Override
        public Object getAttribute(String attribute) throws IOException {
            if (attribute.equals(PERMISSIONS_NAME))
                return readAttributes().permissions();
            if (attribute.equals(OWNER_NAME))
                return readAttributes().owner();
            if (attribute.equals(GROUP_NAME))
                return readAttributes().group();
            return super.getAttribute(attribute);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setAttribute(String attribute, Object value)
            throws IOException
        {
            if (attribute.equals(PERMISSIONS_NAME)) {
                setPermissions((Set<PosixFilePermission>)value);
                return;
            }
            if (attribute.equals(OWNER_NAME)) {
                setOwner((UserPrincipal)value);
                return;
            }
            if (attribute.equals(GROUP_NAME)) {
                setGroup((GroupPrincipal)value);
                return;
            }
            super.setAttribute(attribute, value);
        }

        /**
         * Invoked by readAttributes or sub-classes to add all matching posix
         * attributes to the builder
         */
        final void addPosixAttributesToBuilder(PosixFileAttributes attrs,
                                               AttributesBuilder builder)
        {
            if (builder.match(PERMISSIONS_NAME))
                builder.add(PERMISSIONS_NAME, attrs.permissions());
            if (builder.match(OWNER_NAME))
                 builder.add(OWNER_NAME, attrs.owner());
            if (builder.match(GROUP_NAME))
                builder.add(GROUP_NAME, attrs.group());
        }

        @Override
        public Map<String,?> readAttributes(String[] attributes)
            throws IOException
        {
            AttributesBuilder builder = AttributesBuilder.create(attributes);
            PosixFileAttributes attrs = readAttributes();
            addBasicAttributesToBuilder(attrs, builder);
            addPosixAttributesToBuilder(attrs, builder);
            return builder.unmodifiableMap();
        }

        @Override
        public UnixFileAttributes readAttributes() throws IOException {
            checkReadExtended();
            try {
                 return UnixFileAttributes.get(file, followLinks);
            } catch (UnixException x) {
                x.rethrowAsIOException(file);
                return null;    // keep compiler happy
            }
        }

        // chmod
        final void setMode(int mode) throws IOException {
            checkWriteExtended();
            try {
                if (followLinks) {
                    chmod(file, mode);
                } else {
                    int fd = file.openForAttributeAccess(false);
                    try {
                        fchmod(fd, mode);
                    } finally {
                        close(fd);
                    }
                }
            } catch (UnixException x) {
                x.rethrowAsIOException(file);
            }
        }

        // chown
        final void setOwners(int uid, int gid) throws IOException {
            checkWriteExtended();
            try {
                if (followLinks) {
                    chown(file, uid, gid);
                } else {
                    lchown(file, uid, gid);
                }
            } catch (UnixException x) {
                x.rethrowAsIOException(file);
            }
        }

        @Override
        public void setPermissions(Set<PosixFilePermission> perms)
            throws IOException
        {
            setMode(UnixFileModeAttribute.toUnixMode(perms));
        }

        @Override
        public void setOwner(UserPrincipal owner)
            throws IOException
        {
            if (owner == null)
                throw new NullPointerException("'owner' is null");
            if (!(owner instanceof UnixUserPrincipals.User))
                throw new ProviderMismatchException();
            if (owner instanceof UnixUserPrincipals.Group)
                throw new IOException("'owner' parameter can't be a group");
            int uid = ((UnixUserPrincipals.User)owner).uid();
            setOwners(uid, -1);
        }

        @Override
        public UserPrincipal getOwner() throws IOException {
            return readAttributes().owner();
        }

        @Override
        public void setGroup(GroupPrincipal group)
            throws IOException
        {
            if (group == null)
                throw new NullPointerException("'owner' is null");
            if (!(group instanceof UnixUserPrincipals.Group))
                throw new ProviderMismatchException();
            int gid = ((UnixUserPrincipals.Group)group).gid();
            setOwners(-1, gid);
        }
    }

    private static class Unix extends Posix {
        private static final String MODE_NAME = "mode";
        private static final String INO_NAME = "ino";
        private static final String DEV_NAME = "dev";
        private static final String RDEV_NAME = "rdev";
        private static final String NLINK_NAME = "nlink";
        private static final String UID_NAME = "uid";
        private static final String GID_NAME = "gid";
        private static final String CTIME_NAME = "ctime";

        Unix(UnixPath file, boolean followLinks) {
            super(file, followLinks);
        }

        @Override
        public String name() {
            return "unix";
        }

        @Override
        public Object getAttribute(String attribute) throws IOException {
            if (attribute.equals(MODE_NAME))
                return readAttributes().mode();
            if (attribute.equals(INO_NAME))
                return readAttributes().ino();
            if (attribute.equals(DEV_NAME))
                return readAttributes().dev();
            if (attribute.equals(RDEV_NAME))
                return readAttributes().rdev();
            if (attribute.equals(NLINK_NAME))
                return readAttributes().nlink();
            if (attribute.equals(UID_NAME))
                return readAttributes().uid();
            if (attribute.equals(GID_NAME))
                return readAttributes().gid();
            if (attribute.equals(CTIME_NAME))
                return readAttributes().ctime();
            return super.getAttribute(attribute);
        }

        @Override
        public void setAttribute(String attribute, Object value)
            throws IOException
        {
            if (attribute.equals(MODE_NAME)) {
                setMode((Integer)value);
                return;
            }
            if (attribute.equals(UID_NAME)) {
                setOwners((Integer)value, -1);
                return;
            }
            if (attribute.equals(GID_NAME)) {
                setOwners(-1, (Integer)value);
                return;
            }
            super.setAttribute(attribute, value);
        }

        @Override
        public Map<String,?> readAttributes(String[] attributes)
            throws IOException
        {
            AttributesBuilder builder = AttributesBuilder.create(attributes);
            UnixFileAttributes attrs = readAttributes();
            addBasicAttributesToBuilder(attrs, builder);
            addPosixAttributesToBuilder(attrs, builder);
            if (builder.match(MODE_NAME))
                builder.add(MODE_NAME, attrs.mode());
            if (builder.match(INO_NAME))
                builder.add(INO_NAME, attrs.ino());
            if (builder.match(DEV_NAME))
                builder.add(DEV_NAME, attrs.dev());
            if (builder.match(RDEV_NAME))
                builder.add(RDEV_NAME, attrs.rdev());
            if (builder.match(NLINK_NAME))
                builder.add(NLINK_NAME, attrs.nlink());
            if (builder.match(UID_NAME))
                builder.add(UID_NAME, attrs.uid());
            if (builder.match(GID_NAME))
                builder.add(GID_NAME, attrs.gid());
            if (builder.match(CTIME_NAME))
                builder.add(CTIME_NAME, attrs.ctime());
            return builder.unmodifiableMap();
        }
    }

    static Basic createBasicView(UnixPath file, boolean followLinks) {
        return new Basic(file, followLinks);
    }

    static Posix createPosixView(UnixPath file, boolean followLinks) {
        return new Posix(file, followLinks);
    }

    static Unix createUnixView(UnixPath file, boolean followLinks) {
        return new Unix(file, followLinks);
    }

    static FileOwnerAttributeViewImpl createOwnerView(UnixPath file, boolean followLinks) {
        return new FileOwnerAttributeViewImpl(createPosixView(file, followLinks));
    }
}
