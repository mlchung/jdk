/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jlink.internal.plugins;

import jdk.tools.jlink.internal.Platform;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.plugin.ResourcePoolEntry.Type;
import jdk.tools.jlink.plugin.ResourcePoolModule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Plugin to copy the given license files from the legal directory
 * to other locations.  If the platform supports symbolic links,
 * it will create a symbolic link to the given license files;
 * otherwise, it will make a copy.
 */
public final class CopyLicensePlugin extends AbstractPlugin {
    private final Map<String, String> licenses = new LinkedHashMap<>();
    public CopyLicensePlugin() {
        super("copy-license");
    }

    @Override
    public Category getType() {
        return Category.ADDER;
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public boolean hasRawArgument() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        var arg = config.get(getName());
        if (arg == null)
            throw new AssertionError();
        String[] values = arg.split(",");
        for (String v : values) {
            int i = v.indexOf(":");
            if (i == -1) {
                throw new IllegalArgumentException(getName() + ": invalid plugin argument " + v);
            }
            String from = v.substring(0, i);
            String to = v.substring(i+1);
            if (from.indexOf("/") == -1) {
                throw new IllegalArgumentException(getName() + ": invalid plugin argument " + from);
            }
            if (licenses.containsKey(from)) {
                throw new IllegalArgumentException(getName() + ": duplicated source " + from);
            }
            if (licenses.containsValue(to)) {
                throw new IllegalArgumentException(getName() + ": duplicated destination " + to);
            }
            licenses.put(from, to);
        }
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        in.transformAndCopy(Function.identity(), out);
        boolean copy = isWindows(in);
        for (String source : licenses.keySet()) {
            ResourcePoolEntry entry = findSource(in, source);
            if (entry == null) {
                throw new PluginException(source + " does not exist");
            }
            if (entry.type() != Type.LEGAL_NOTICE) {
                throw new PluginException(source + " not of legal notice type");
            }
            String dest = "/java.base/" + licenses.get(source);
            ResourcePoolEntry newEntry = copy
                    ? ResourcePoolEntry.create(dest, Type.TOP, entry.contentBytes())
                    : ResourcePoolEntry.createSymLink(dest, Type.TOP, entry);
            out.add(newEntry);
        }
        return out.build();
    }

    private boolean isWindows(ResourcePool resPool) {
        String value = resPool.moduleView()
                .findModule("java.base")
                .map(ResourcePoolModule::targetPlatform)
                .orElse(null);
        if (value == null) {
            throw new PluginException("ModuleTarget attribute is missing for java.base module");
        }
        Platform platform = Platform.parsePlatform(value);
        return platform.os() == Platform.OperatingSystem.WINDOWS;
    }

    private ResourcePoolEntry findSource(ResourcePool resPool, String source) {
        int i = source.indexOf("/");
        var mn = source.substring(0, i);
        var fn = source.substring(i+1);

        String entry = "/" + mn + "/legal/" + source;
        var om = resPool.moduleView().findModule(mn);
        if (om.isEmpty())
            return null;
        return om.get().findEntry(entry).orElse(null);
    }
}
