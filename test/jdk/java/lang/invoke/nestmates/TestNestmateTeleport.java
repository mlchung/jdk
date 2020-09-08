/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 8199386
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.compiler.CompilerUtils
 *        p.TestNestmateTeleport
 * @run testng/othervm p.TestNestmateTeleport
 */

package p;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandles.lookup;

import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.Utils;
import org.testng.annotations.*;
import static org.testng.Assert.*;

/*
 * Class C, D, E are in the same nest.  No InnerClasses attribute.
 *
 * C and D and E can access private members of C and D and E symbolically
 * C is a subclass of ThreadLocal and C does not have a bridge method.
 *
 * A full power lookup on C can access private members of C and D and E,
 * i.e. equivalent to the bytecode behavior
 *
 * E can access C symbolically but it does not have access to
 * the protected method C::initialValue which is inherited from
 * another package (ThreadLocal::initialValue).
 *
 * For comparison, F and F.Inner classes are nestmates and
 * also have InnerClasses attribute.
 * F.Inner classes are in the same package member of F.
 * F.Inner can call F::initialValue that is inherited from
 * ThreadLocal::initialValue.
 *
 * Lookup on F.Inner can teleport to F to call findSpecial
 * obtaining a method handle of ThreadLocal::initialValue so
 * that a cross-package protected method can be invoked by F.Inner.
 *
 * Similarly, E and C are in the same nest.  It should be able to
 * achieve the same result via Lookup::in.
 *
 * A full-power lookup on C can findSpecial to get the method handle
 * for ThreadLocal::initialValue with specialCaller == C.class
 * (specialCaller must be identical to the lookup class).
 */
public class TestNestmateTeleport {
    private static final Path SRC_DIR = Paths.get(Utils.TEST_SRC, "src");
    private static final Path CLASSES = Paths.get("classes");
    private static final Path DEST = Paths.get("dest");

    private static Class<?> C, D, E;

    @BeforeTest
    public void setup() throws Exception {
        Files.createDirectories(CLASSES);
        Files.createDirectories(DEST.resolve("p"));
        compileSources(SRC_DIR, CLASSES);
        // add NestHost and NestMembers attribute to class file of C, D, E
        // change the name() method from public to private
        String host = "p/C";
        Set<String> members = Set.of("p/D", "p/E");
        C = lookup().defineClass(classFileWithNestMembers("p/C.class", members));
        D = lookup().defineClass(classFileWithNestHost("p/D.class", host));
        E = lookup().defineClass(classFileWithNestHost("p/E.class", host));

        // check nest membership
        checkNestMembership(C, "C", C);
        checkNestMembership(D, "D", C);
        checkNestMembership(E, "E", C);
    }

    @Test
    public void staticNestMembership() throws Exception {
        // C::test invokes private D::name and E::name method
        Method m1 = C.getMethod("test");
        m1.invoke(null);

        // E::test invokes private C::accessD method and
        // C::initialValue protected method inherited from java.lang.ThreadLocal
        Method m2 = E.getMethod("test");
        m2.invoke(null);
    }

    @Test
    public void nestedTypes() throws Exception {
        Class<?> f = lookup().defineClass(Files.readAllBytes(CLASSES.resolve("p/F.class")));
        Class<?> fInner = lookup().defineClass(Files.readAllBytes(CLASSES.resolve("p/F$Inner.class")));
        // Inner::test invokes private F::name method and
        // F::initialValue protected method inherited from java.lang.ThreadLocal
        Method m3 = fInner.getMethod("test");
        m3.invoke(null);
    }

    static byte[] classFileWithNestHost(String path, String host) throws IOException {
        return addNestHost(CLASSES.resolve(path), DEST.resolve(path), host);
    }

    static byte[] classFileWithNestMembers(String path, Set<String> members) throws IOException {
        return addNestMembers(CLASSES.resolve(path), DEST.resolve(path), members);
    }

    static void checkNestMembership(Class<?> c, String expected, Class<?> host) throws Exception {
        if (Class.forName(c.getName()) != c) {
            throw new RuntimeException("mismatched " + c);
        }
        Class<?> h = c.getNestHost();
        if (h != host) {
            throw new RuntimeException("mismatched host " + h.getName() + " expected " + host.getName());
        }

        Method m = c.getDeclaredMethod("name");
        if (!Modifier.isPrivate(m.getModifiers())) {
            throw new RuntimeException("expected private method: " + m);
        }

        m.setAccessible(true);
        String name = (String)m.invoke(c.newInstance());
        if (!expected.equals(name)) {
            throw new RuntimeException("expected " + expected + " got " + name);
        }
    }

    static void compileSources(Path sourceFile, Path dest, String... options) throws IOException {
        Stream<String> ops = Stream.of("-cp", Utils.TEST_CLASSES + File.pathSeparator + CLASSES);
        if (options != null && options.length > 0) {
            ops = Stream.concat(ops, Arrays.stream(options));
        }
        if (!CompilerUtils.compile(sourceFile, dest, ops.toArray(String[]::new))) {
            throw new RuntimeException("Compilation of the test failed: " + sourceFile);
        }
    }

    private static byte[] addNestHost(Path source, Path dest, String host) throws IOException {
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(dest)) {
            NestmateExtender extender = NestmateExtender.newExtender(in);
            extender.nestHost(host);
            byte[] bytes = extender.toByteArray();
            out.write(bytes);
            return bytes;
        }
    }

    private static byte[] addNestMembers(Path source, Path dest, Set<String> members) throws IOException {
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(dest)) {
            NestmateExtender extender = NestmateExtender.newExtender(in);
            extender.nestMembers(members);
            byte[] bytes = extender.toByteArray();
            out.write(bytes);
            return bytes;
        }
    }

    static class NestmateExtender {
        // the input stream to read the original module-info.class
        private final InputStream in;

        // the value of the ModuleMainClass attribute
        private String host;

        // the value for the ModuleTarget attribute
        private Set<String> members;

        private NestmateExtender(InputStream in) {
            this.in = in;
        }

        /**
         * Sets the nest host in the NestHost attribute
         */
        public NestmateExtender nestHost(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the nest members in the NestMembers attribute
         */
        public NestmateExtender nestMembers(Set<String> members) {
            this.members = members;
            return this;
        }

        /**
         * Outputs the modified class file to the given output stream.
         * Once this method has been called then the NestmateExtender object should
         * be discarded.
         */
        public void write(OutputStream out) throws IOException {
            // emit to the output stream
            out.write(toByteArray());
        }

        /**
         * Returns the bytes of the modified class file.
         * Once this method has been called then the NestmateExtender object should
         * be discarded.
         */
        public byte[] toByteArray() throws IOException {
            if (host != null && members != null) {
                throw new IllegalStateException("cannot set both the nest host and members");
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
                    + ClassWriter.COMPUTE_FRAMES);

            ClassReader cr = new ClassReader(in);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM7, cw) {
                @Override
                public void visitNestHost(final String nestHost) {
                    throw new IllegalArgumentException("should not have NestHost attribute: " + nestHost);
                }
                @Override
                public void visitNestMember(final String nestMember) {
                    throw new IllegalArgumentException("should not have NestMembers attribute: " + nestMember);
                }
                @Override
                public MethodVisitor visitMethod(final int access,
                                                 final String name,
                                                 final String descriptor,
                                                 final String signature,
                                                 final String[] exceptions) {
                    int modifiers = access;
                    if (name.equals("name") || name.equals("lookup")) {
                        modifiers = (access & ~ACC_PUBLIC) | ACC_PRIVATE;
                    }
                    return super.visitMethod(modifiers, name, descriptor, signature, exceptions);
                }
            };
            cr.accept(cv, 0);

            // add NestHost or NestMembers attributes
            if (host != null) {
                cw.visitNestHost(host);
            }

            if (members != null) {
                for (String name : members) {
                    cw.visitNestMember(name);
                }
            }
            return cw.toByteArray();
        }

        public static NestmateExtender newExtender(InputStream in) {
            return new NestmateExtender(in);
        }
    }
}
