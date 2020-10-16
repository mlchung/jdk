package java.lang.reflect;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * {@code InvocationHandler.WithLookup} is a functional super-interface of plain
 * {@linkplain InvocationHandler}. It is invoked with additional {@code proxyLookup}
 * parameter needed to invoke super default methods of proxy interfaces via
 * {@linkplain #invokeDefaultMethod(MethodHandles.Lookup, Object, Method, Object...)}.
 * If you don't need to invoke super default methods of proxy interfaces, you can implement
 * plain {@linkplain InvocationHandler} which doesnt receive the {@code proxyLookup}
 * parameter and can be used as a handler even for proxies implementing interfaces
 * inaccessible to code creating such proxies.
 * Invocation handlers directly implementing this interface may only be used as
 * handlers for proxies implementing interfaces accessible to code creating such
 * proxies via
 * {@linkplain Proxy#newProxyInstance(ClassLoader, Class[], InvocationHandlerWithLookup)}.
 *
 * @see InvocationHandler
 * @since 16
 */
@FunctionalInterface
public interface InvocationHandlerWithLookup {

    /**
     * Processes a method invocation on a proxy instance and returns
     * the result.  This method will be invoked on an invocation handler
     * when a method is invoked on a proxy instance that it is
     * associated with.
     *
     * @param proxyLookup the full privileged {@code Lookup} with proxy
     *                    lookup class needed to invoke
     *                    {@linkplain #invokeDefaultMethod(MethodHandles.Lookup, Object, Method, Object...)}
     *
     * @param   proxy the proxy instance that the method was invoked on
     *
     * @param   method the {@code Method} instance corresponding to
     * the interface method invoked on the proxy instance.  The declaring
     * class of the {@code Method} object will be the interface that
     * the method was declared in, which may be a superinterface of the
     * proxy interface that the proxy class inherits the method through.
     *
     * @param   args an array of objects containing the values of the
     * arguments passed in the method invocation on the proxy instance,
     * or {@code null} if interface method takes no arguments.
     * Arguments of primitive types are wrapped in instances of the
     * appropriate primitive wrapper class, such as
     * {@code java.lang.Integer} or {@code java.lang.Boolean}.
     *
     * @return  the value to return from the method invocation on the
     * proxy instance.  If the declared return type of the interface
     * method is a primitive type, then the value returned by
     * this method must be an instance of the corresponding primitive
     * wrapper class; otherwise, it must be a type assignable to the
     * declared return type.  If the value returned by this method is
     * {@code null} and the interface method's return type is
     * primitive, then a {@code NullPointerException} will be
     * thrown by the method invocation on the proxy instance.  If the
     * value returned by this method is otherwise not compatible with
     * the interface method's declared return type as described above,
     * a {@code ClassCastException} will be thrown by the method
     * invocation on the proxy instance.
     *
     * @throws  Throwable the exception to throw from the method
     * invocation on the proxy instance.  The exception's type must be
     * assignable either to any of the exception types declared in the
     * {@code throws} clause of the interface method or to the
     * unchecked exception types {@code java.lang.RuntimeException}
     * or {@code java.lang.Error}.  If a checked exception is
     * thrown by this method that is not assignable to any of the
     * exception types declared in the {@code throws} clause of
     * the interface method, then an
     * {@link UndeclaredThrowableException} containing the
     * exception that was thrown by this method will be thrown by the
     * method invocation on the proxy instance.
     *
     * @see     UndeclaredThrowableException
     * @see     InvocationHandler#invoke(Object, Method, Object[])
     */
    Object invoke(MethodHandles.Lookup proxyLookup, Object proxy, Method method, Object[] args) throws Throwable;

    /**
     * Invokes the specified default method on the given {@code proxy} instance with
     * the given parameters.  The given {@code method} must be a default method
     * declared in a proxy interface of the {@code proxy}'s class or inherited
     * from its superinterface directly or indirectly.
     * <p>
     * This method behaves as if called from an {@code invokespecial} instruction
     * from the proxy class as the caller equivalent to the invocation of
     * {@code X.super.m(A* a)} where {@code X} is a proxy interface and
     * the call to {@code X.super::m(A*)} is resolved to the given {@code method}.
     * <p>
     * For example, interface {@code A} and {@code B} both declare a default
     * implementation of method {@code m}. Interface {@code C} extends {@code A}
     * and it inherits the default method {@code m} from its superinterface {@code A}.
     *
     * <blockquote><pre>{@code
     * interface A {
     *     default T m(A a) { return t1; }
     * }
     * interface B {
     *     default T m(A a) { return t2; }
     * }
     * interface C extends A {}
     * }</pre></blockquote>
     *
     * The following creates a proxy instance that implements {@code A}
     * and invokes the default method {@code A::m}.
     *
     * <blockquote><pre>{@code
     * Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] { A.class },
     *         (o, m, params) -> {
     *             assert m.getDeclaringClass() == A.class && m.isDefault();
     *             return InvocationHandler.invokeDefaultMethod(o, m, params);
     *         });
     * }</pre></blockquote>
     *
     * If a proxy instance implements both {@code A} and {@code B}, both
     * of which provides the default implementation of method {@code m},
     * the invocation handler can dispatch the method invocation to
     * {@code A::m} or {@code B::m} via the {@code invokeDefaultMethod} method.
     * For example, the following code delegates the method invocation
     * to {@code B::m}.
     *
     * <blockquote><pre>{@code
     * Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] { A.class, B.class },
     *         (o, m, params) -> {
     *             // delegate to invoking B::m
     *             Method selectedMethod = B.class.getMethod(m.getName(), m.getParameterTypes());
     *             return InvocationHandler.invokeDefaultMethod(o, selectedMethod, params);
     *         });
     * }</pre></blockquote>
     *
     * If a proxy instance implements {@code C} that inherits the default
     * method {@code m} from its superinterface {@code A}, then
     * the interface method invocation on {@code "m"} is dispatched to
     * the invocation handler's {@link #invoke(MethodHandles.Lookup, Object, Method, Object[]) invoke}
     * method with the {@code Method} object argument representing the
     * default method {@code A::m}.
     *
     * <blockquote><pre>{@code
     * Object c = Proxy.newProxyInstance(loader, new Class<?>[] { C.class },
     *        (o, m, params) -> {
     *             assert m.isDefault();
     *             return InvocationHandler.invokeDefaultMethod(o, m, params);
     *        });
     * }</pre></blockquote>
     *
     * The invocation of method {@code "m"} on {@code c} will behave as if
     * {@code C.super::m} is called and that is resolved to invoking
     * {@code A::m}.
     * <p>
     * If {@code C} is modified to override {@code m} as below:
     *
     * <blockquote><pre>{@code
     * interface C extends A {
     *     default T m(A a) { return t3; }
     * }
     * }</pre></blockquote>
     *
     * {@code C.super::m} will be resolved to {@code C::m} instead.
     * The invocation of method {@code "m"} on {@code c} will behave
     * differently and result in invoking {@code C::m} instead of {@code A::m}.
     * <p>
     * If an invocation handler dispatches the method invocation by calling
     * the {@code invokeDefaultMethod} method with the {@code Method} object
     * representing {@code A::m}:
     *
     * <blockquote><pre>{@code
     * Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] { C.class },
     *         (o, m, params) -> {
     *             // IllegalArgumentException thrown as {@code A::m} is not a method
     *             // inherited from its proxy interface C
     *             return InvocationHandler.invokeDefaultMethod(o, A.class.getMethod("m"), params);
     *         });
     * }</pre></blockquote>
     *
     * The invocation on {@code "m"} with this proxy instance will result in
     * an {@code IllegalArgumentException} because {@code C} overrides the implementation
     * of the same method and {@code A::m} is not accessible by a proxy instance.
     *
     * @param proxyLookup the {@code MethodHandles.Lookup} instance passed on from
     *                    the call to
     *                    {@linkplain InvocationHandlerWithLookup#invoke(MethodHandles.Lookup, Object, Method, Object[])}
     *                    method.
     * @param proxy   the {@code Proxy} instance on which the default method to be invoked
     * @param method  the {@code Method} instance corresponding to a default method
     *                declared in a proxy interface of the proxy class or inherited
     *                from its superinterface directly or indirectly
     * @param args    the parameters used for the method invocation; can be {@code null}
     *                if the number of formal parameters required by the method is zero.
     * @return the value returned from the method invocation
     *
     * @throws IllegalArgumentException if any of the following conditions is {@code true}:
     *         <ul>
     *         <li>{@code proxy} is not {@linkplain Proxy#isProxyClass(Class)
     *             a proxy instance}; or</li>
     *         <li>the given {@code method} is not a default method declared
     *             in a proxy interface of the proxy class and not inherited from
     *             any of its superinterfaces; or</li>
     *         <li>the given {@code method} is overridden directly or indirectly by
     *             the proxy interfaces and the method reference to the named
     *             method never resolves to the given {@code method}; or</li>
     *         <li>the length of the given {@code args} array does not match the
     *             number of parameters of the method to be invoked; or</li>
     *         <li>any of the {@code args} elements fails the unboxing
     *             conversion if the corresponding method parameter type is
     *             a primitive type; or if, after possible unboxing, any of the
     *             {@code args} elements cannot be assigned to the corresponding
     *             method parameter type.</li>
     *         </ul>
     * @throws IllegalAccessException if given {@code proxyLookup} is not
     *         a full privileged {@code Lookup} or its lookup class is not
     *         equal to the proxy class
     * @throws InvocationTargetException if the invoked default method throws
     *         any exception, it is wrapped by {@code InvocationTargetException}
     *         and rethrown
     * @throws NullPointerException if {@code proxy}, {@code method} or
     *         {@code proxyLookup} is {@code null}
     *
     * @jvms 5.4.3. Method Resolution
     */
    static Object invokeDefaultMethod(MethodHandles.Lookup proxyLookup,
                                      Object proxy, Method method, Object... args)
    throws IllegalAccessException, InvocationTargetException {
        Objects.requireNonNull(proxyLookup);
        Objects.requireNonNull(proxy);
        Objects.requireNonNull(method);

        // verify that the object is actually a proxy instance...
        Class<?> proxyClass = proxy.getClass();
        if (!Proxy.isProxyClass(proxyClass)) {
            throw new IllegalArgumentException("'proxy' is not a proxy instance");
        }
        // ...that the method is a default method...
        if (!method.isDefault()) {
            throw new IllegalArgumentException("\"" + method + "\" is not a default method");
        }
        // ...that the lookup is a full privilege lookup with proxy lookup class
        if (proxyClass != proxyLookup.lookupClass() || !proxyLookup.hasFullPrivilegeAccess()) {
            throw new IllegalAccessException("'proxyLookup' is not a full privilege proxy class lookup");
        }
        // lookup the cached method handle
        ConcurrentHashMap<Method, MethodHandle> methods = Proxy.defaultMethodsCache(proxyClass);
        MethodHandle superMH = methods.get(method);

        if (superMH == null) {
            MethodType type = methodType(method.getReturnType(), method.getParameterTypes());
            Class<?> proxyInterface = Proxy.findProxyInterfaceOrElseThrow(proxyClass, method);
            MethodHandle dmh;
            try {
                dmh = proxyLookup
                    .findSpecial(proxyInterface, method.getName(), type, proxyClass)
                    .withVarargs(false);
            } catch (NoSuchMethodException e) {
                // should not reach here
                throw new InternalError(e);
            }
            // this check can be turned into assertion as it is guaranteed to succeed by the virtue of
            // looking up a default (instance) method declared or inherited by proxyInterface
            // while proxyClass implements (is a subtype of) proxyInterface ...
            assert ((BooleanSupplier) () -> {
                try {
                    // make sure that the method type matches
                    dmh.asType(type.insertParameterTypes(0, proxyClass));
                    return true;
                } catch (WrongMethodTypeException e) {
                    return false;
                }
            }).getAsBoolean() : "Wrong method type";
            // change return type to Object
            MethodHandle mh = dmh.asType(dmh.type().changeReturnType(Object.class));
            // wrap any exception thrown with InvocationTargetException
            mh = MethodHandles.catchException(mh, Throwable.class, Proxy.wrapWithInvocationTargetExceptionMH());
            // spread array of arguments among parameters (skipping 1st parameter - target)
            mh = mh.asSpreader(1, Object[].class, type.parameterCount());
            // change target type to Object
            mh = mh.asType(MethodType.methodType(Object.class, Object.class, Object[].class));

            // push MH into cache
            MethodHandle cached = methods.putIfAbsent(method, mh);
            if (cached != null) {
                superMH = cached;
            } else {
                superMH = mh;
            }
        }

        // invoke the super method
        try {
            // the args array can be null if the number of formal parameters required by
            // the method is zero (consistent with Method::invoke)
            Object[] params = args != null ? args : Proxy.EMPTY_ARGS;
            return superMH.invokeExact(proxy, params);
        } catch (ClassCastException | NullPointerException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (InvocationTargetException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            // should not reach here
            throw new InternalError(e);
        }
    }
}
