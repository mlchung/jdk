/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

/**
 * Thrown if an application attempts to get an invocation handler that
 * it does not have access to.
 *
 * @see Proxy#getInvocationHandler(Object)
 * @see <a href="Proxy.html#default-methods">Default Method Invocation</a>
 * @since 16
 */
public class InaccessibleInvocationHandlerException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = -4157778732787155200L;

    /**
     * Constructs an {@code InaccessibleInvocationHandlerException} with
     * no detail message.
     */
    public InaccessibleInvocationHandlerException() {
    }

    /**
     * Constructs an {@code InaccessibleInvocationHandlerException} with
     * the given detail message.
     *
     * @param msg The detail message
     */
    public InaccessibleInvocationHandlerException(String msg) {
        super(msg);
    }

}
