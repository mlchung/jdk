/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This application is meant to be run to create a classlist file representing
 * common use.
 *
 * The classlist is produced by adding -XX:DumpLoadedClassList=classlist
 */
package build.tools.classlist;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetAddress;
import java.nio.file.FileSystems;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import java.util.logging.*;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.DateFormat;

// AsSpreader
import java.lang.reflect.*;
import java.util.concurrent.Callable;

import static java.util.stream.Collectors.*;

/**
 * This class is used to generate a classlist during build. Intent
 * is to touch a reasonable amount of JDK classes that are commonly
 * loaded and used early.
 */
public class HelloClasslist {

    private static final Logger LOGGER = Logger.getLogger("Hello");

    public static void main(String ... args) throws Throwable {

        FileSystems.getDefault();

        List<String> strings = Arrays.asList("Hello", "World!", "From: ",
                InetAddress.getLoopbackAddress().toString());

        String helloWorld = strings.parallelStream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(joining(","));

        Stream.of(helloWorld.split("([,x-z]{1,3})([\\s]*)"))
                .map(String::toString)
                .forEach(System.out::println);

        // Common concatenation patterns
        int i = args.length;
        String s = String.valueOf(i);

        String SS     = s + s;
        String CS     = "string" + s;
        String SC     = s + "string";
        String SCS    = s + "string" + s;
        String CSS    = "string" + s + s;
        String CSC    = "string" + s + "string";
        String SSC    = s + s + "string";
        String CSCS   = "string" + s + "string" + s;
        String SCSC   = s + "string" + s + "string";
        String CSCSC  = "string" + s + "string" + s + "string";
        String SCSCS  = s + "string" + s + "string" + s;
        String SSCSS  = s + s + "string" + s + s;
        String S5     = s + s + s + s + s;
        String S6     = s + s + s + s + s + s;
        String S7     = s + s + s + s + s + s + s;
        String S8     = s + s + s + s + s + s + s + s;
        String S9     = s + s + s + s + s + s + s + s + s;
        String S10    = s + s + s + s + s + s + s + s + s + s;

        String CI     = "string" + i;
        String IC     = i + "string";
        String SI     = s + i;
        String IS     = i + s;
        String CIS    = "string" + i + s;
        String CSCI   = "string" + s + "string" + i;
        String CIC    = "string" + i + "string";
        String CICI   = "string" + i + "string" + i;

        float f = 0.1f;
        String CF     = "string" + f;
        String CFS    = "string" + f + s;
        String CSCF   = "string" + s + "string" + f;

        char c = 'a';
        String CC     = "string" + c;
        String CCS    = "string" + c + s;
        String CSCC   = "string" + s + "string" + c;

        long l = System.currentTimeMillis();
        String CJ     = "string" + l;
        String JC     = l + "string";
        String CJC    = "string" + l + "string";
        String CJCJ   = "string" + l + "string" + l;
        String CJCJC  = "string" + l + "string" + l + "string";
        double d = i / 2.0;
        String CD     = "string" + d;
        String CDS    = "string" + d + s;
        String CSCD   = "string" + s + "string" + d;

        String newDate = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                LocalDateTime.now(ZoneId.of("GMT")));

        String oldDate = String.format("%s%n",
                DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.ROOT)
                        .format(new Date()));

        // A selection of trivial and common reflection operations
        var instance = HelloClasslist.class.getConstructor().newInstance();
        HelloClasslist.class.getMethod("staticMethod_V").invoke(null);
        var obj = HelloClasslist.class.getMethod("staticMethod_L_L", Object.class).invoke(null, instance);
        HelloClasslist.class.getField("field").get(instance);

        // A selection of trivial and relatively common MH operations
        invoke(MethodHandles.identity(double.class), 1.0);
        invoke(MethodHandles.identity(int.class), 1);
        invoke(MethodHandles.identity(String.class), "x");

        invoke(handle("staticMethod_V", MethodType.methodType(void.class)));
        AsSpreader.main(args);

        LOGGER.log(Level.FINE, "New Date: " + newDate + " - old: " + oldDate);
    }

    public HelloClasslist() {}

    public String field = "someValue";

    public static void staticMethod_V() {}

    public static Object staticMethod_L_L(Object o) { return o; }

    private static MethodHandle handle(String name, MethodType type) throws Throwable {
        return MethodHandles.lookup().findStatic(HelloClasslist.class, name, type);
    }

    private static Object invoke(MethodHandle mh, Object ... args) throws Throwable {
        try {
            for (Object o : args) {
                mh = MethodHandles.insertArguments(mh, 0, o);
            }
            return mh.invoke();
        } catch (Throwable t) {
            LOGGER.warning("Failed to find, link and/or invoke " + mh.toString() + ": " + t.getMessage());
            throw t;
        }
    }

    static class AsSpreader {
        public static void main(String... args) throws Throwable {
            AsSpreader test = new AsSpreader(Nested.class.getDeclaredMethods());
            Method m0 = ((Callable<Method>)(() -> {
                for (Method m : test.methods) {
                    if (Modifier.isStatic(m.getModifiers())) {
                        return m;
                    }
                }
                return null;
            })).call();
            Method m1 = ((Callable<Method>)(() -> {
                for (Method m : test.methods) {
                    if (!Modifier.isStatic(m.getModifiers())) {
                        return m;
                    }
                }
                return null;
            })).call();

            Method m = AsSpreader.class.getDeclaredMethod("m", int.class, long.class, Object.class);
            Object[] a = new Object[] { new Object() };
            m0.invoke(null, a);
            m1.invoke(new Nested(), a);
            m.invoke(null, new Object[] { 1, (long)2, new Object()});
        }

        private Method[] methods;
        AsSpreader(Method[] methods) {
            this.methods = methods;
        }

        static Object m(int i, long l, Object o) {
            return o;
        }
        static class Nested {
        //    static int x(int p) { return p;}

            Object im00(Object p) { return p; }

            static Object m00(Object p) {return p;}

            static Object m01(Object p) {return p;}

            static Object m02(Object p) {return p;}

            static Object m03(Object p) {return p;}

            static Object m04(Object p) {return p;}

            static Object m05(Object p) {return p;}

            static Object m06(Object p) {return p;}

            static Object m07(Object p) {return p;}

            static Object m08(Object p) {return p;}

            static Object m09(Object p) {return p;}

            static Object m0A(Object p) {return p;}

            static Object m0B(Object p) {return p;}

            static Object m0C(Object p) {return p;}

            static Object m0D(Object p) {return p;}

            static Object m0E(Object p) {return p;}

            static Object m0F(Object p) {return p;}

            static Object m10(Object p) {return p;}

            static Object m11(Object p) {return p;}

            static Object m12(Object p) {return p;}

            static Object m13(Object p) {return p;}

            static Object m14(Object p) {return p;}

            static Object m15(Object p) {return p;}

            static Object m16(Object p) {return p;}

            static Object m17(Object p) {return p;}

            static Object m18(Object p) {return p;}

            static Object m19(Object p) {return p;}

            static Object m1A(Object p) {return p;}

            static Object m1B(Object p) {return p;}

            static Object m1C(Object p) {return p;}

            static Object m1D(Object p) {return p;}

            static Object m1E(Object p) {return p;}

            static Object m1F(Object p) {return p;}
        }

    }
}
