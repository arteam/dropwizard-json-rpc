package com.github.arteam.simplejsonrpc.server;

import com.github.arteam.simplejsonrpc.core.annotation.*;
import com.github.arteam.simplejsonrpc.core.domain.ErrorResponse;
import com.github.arteam.simplejsonrpc.server.metadata.*;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Optional;

import static java.util.Optional.empty;

/**
 *
 * utility data class
 *
 * @param <A>
 * @param <T>
 */
class Tuple2<A extends Annotation, T> {
    public final A annotation;
    public final T owner;

    private Tuple2(A annotation, T owner) {
        this.annotation = annotation;
        this.owner = owner;
    }

    static <A extends Annotation, T> Tuple2<A,T> of( A annotation, T owner ) {
        return new Tuple2<A,T>( annotation, owner);
    }
    static <A extends Annotation, T> Optional<Tuple2<A,T>> optionalOf( A annotation, T owner ) {
        return Optional.of(of(annotation,owner));
    }
}


public class AnnotationsServiceMetadataFactory implements ServiceMetadataFactory {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcServer.class);

    /**
     * Gets class metadata for JSON-RPC processing.
     * It scans the class and builds JSON-RPC meta-information about methods and it's parameters
     *
     * @param serviceClass actual service class
     * @return service class JSON-RPC meta-information
     */
    @Override
    public @NotNull ServiceMetadata createServiceMetadata(@NotNull Class<?> serviceClass) {

        ImmutableMap.Builder<String, MethodMetadata> methodsMetadata = ImmutableMap.builder();

        Class<?> searchType = serviceClass;
        // Search through the class hierarchy
        while (searchType != null) {
            for (Method method : searchType.getDeclaredMethods()) {
                String methodName = method.getName();
                // Checks the annotation
                Optional<Tuple2<JsonRpcMethod,Method>> jsonRpcMethod = getMethodAnnotationByTpe( method, JsonRpcMethod.class);
                if (!jsonRpcMethod.isPresent()) {
                    continue;
                }

                // Check modifiers, only public non-static methods are permitted
                int modifiers = method.getModifiers();
                if (!Modifier.isPublic(modifiers)) {
                    log.warn("Method '" + methodName + "' is not public");
                    continue;
                }
                if (Modifier.isStatic(modifiers)) {
                    log.warn("Method '" + methodName + "' is static");
                    continue;
                }

                final String jsonRpcMethodValue = jsonRpcMethod.get().annotation.value();
                String rpcMethodName = !jsonRpcMethodValue.isEmpty() ? jsonRpcMethodValue : methodName;
                //final ImmutableMap<String, ParameterMetadata> methodParams = getMethodParameters(method);
                final java.util.Map<String, ParameterMetadata> methodParams = getMethodParameters(jsonRpcMethod.get().owner);
                if (methodParams == null) {
                    log.warn("Method '" + methodName + "' has misconfigured parameters");
                    continue;
                }

                method.setAccessible(true);
                methodsMetadata.put(rpcMethodName, new MethodMetadata(rpcMethodName, method, methodParams));
            }
            searchType = searchType.getSuperclass();
        }

        return getClassAnnotationByTpe(serviceClass, JsonRpcService.class)
                .map( t2 -> ServiceMetadata.asService( methodsMetadata.build() )
                ).orElseGet( () -> ServiceMetadata.asClass()  );
    }

    /**
     *
     * @param throwableClass
     * @return
     */
    @Override
    public ErrorDataResolver buildErrorDataResolver(Class<? extends Throwable> throwableClass) {
        Class<?> c = throwableClass;
        Field dataField = null;
        Method dataMethod = null;
        while (c != null) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(JsonRpcErrorData.class)) {
                    if (dataField != null) {
                        throw new IllegalArgumentException(
                                "Ambiguous configuration: there is more than one " +
                                        "@JsonRpcErrorData annotated property in " +
                                        c.getName());
                    }
                    field.setAccessible(true);
                    dataField = field;
                }
            }
            for (Method method : c.getDeclaredMethods()) {
                if (method.isAnnotationPresent(JsonRpcErrorData.class)) {
                    if (method.getReturnType() == void.class) {
                        log.warn("Method '{}' annotated with 'JsonRpcErrorData' cannot have void return type", method.getName());
                        continue;
                    }
                    if (method.getParameterCount() > 0) {
                        log.warn("Method '{}' annotated with 'JsonRpcErrorData' must be with zero arguments", method.getName());
                        continue;
                    }
                    if (dataField != null || dataMethod != null) {
                        throw new IllegalArgumentException(
                                "Ambiguous configuration: there is more than one " +
                                        "@JsonRpcErrorData annotated property in " +
                                        c.getName());
                    }
                    method.setAccessible(true);
                    dataMethod = method;
                }
            }
            c = c.getSuperclass();
        }
        if (dataField != null) {
            Field finalDataField = dataField;
            return t -> Optional.ofNullable(finalDataField.get(t));
        } else if (dataMethod != null) {
            Method finalDataMethod = dataMethod;
            return t -> Optional.ofNullable(finalDataMethod.invoke(t));
        } else {
            return t -> Optional.empty();
        }
    }

    /**
     *
     * @param rootCause
     * @return
     */
    @Override
    public Optional<ErrorMetadata> createErrorMetadata(@NotNull Throwable rootCause ) {
        final Annotation[] annotations = rootCause.getClass().getAnnotations();
        Optional<JsonRpcError> jsonRpcErrorAnnotation = getAnnotation(annotations, JsonRpcError.class);
        if (jsonRpcErrorAnnotation.isPresent()) {
            final String msg = jsonRpcErrorAnnotation.get().message();
            final ErrorMetadata result =
                    new ErrorMetadata(
                            jsonRpcErrorAnnotation.get().code(),
                            msg.isEmpty() ? rootCause.getMessage() : msg );
            return Optional.of(result);
        }
        return empty();
    }

    /**
     * Finds an entity annotation with appropriate type.
     *
     * @param annotations entity annotations
     * @param clazz       annotation class type
     * @param <T>         actual compile-time annotation type
     * @return appropriate annotation or {@code null} if it wasn't found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private <T extends Annotation> Optional<T> getAnnotation(@Nullable Annotation[] annotations,
                                                                   @NotNull Class<T> clazz) {
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(clazz)) {
                    return Optional.of((T) annotation);
                }
            }
        }
        return empty();
    }

    /**
     * get method's annotations by type, evaluating also if annotation
     * is eventually present in the same method from inherited interfaces
     *
     * @param m method to evaluate
     * @return Optional containing a tuple with annotation and declaring method if found
     *
     */
    @NotNull
    private <A extends Annotation> Optional<Tuple2<A, Method>> getMethodAnnotationByTpe(Method m, Class<A> annotationClass ) {

        final Annotation[] directs = m.getDeclaredAnnotations();
        if( directs!=null  && directs.length > 0 ) {
            for( Annotation direct : directs ) {
                if( direct.annotationType().equals(annotationClass))
                    return Tuple2.optionalOf((A)direct, m);
            }
        }

        final Class<?> interfaces[] = m.getDeclaringClass().getInterfaces();
        if( interfaces != null && interfaces.length > 0 ) {
            for (Class<?> ifc : interfaces) {
                try {
                    final Method mm = ifc.getMethod(m.getName(), m.getParameterTypes());
                    final Annotation[] indirects = mm.getDeclaredAnnotations();
                    for( Annotation indirect : indirects ) {
                        if( indirect.annotationType().equals(annotationClass))
                            return Tuple2.optionalOf( (A)indirect, mm);
                    }
                } catch (NoSuchMethodException e) {
                    // skip
                }
            }
        }
        return empty();
    }


    /**
     * get class's annotations by type, evaluating also if annotation
     * is eventually present in the inherited interfaces
     *
     * @param clazz class to evaluate
     * @return Optional containing a tuple with annotation and declaring class/interface if found
     */
    @NotNull
    private <A extends Annotation> Optional<Tuple2<A,Class<?>>> getClassAnnotationByTpe( Class<?> clazz, Class<A> annotationClass ) {

        final Annotation[] directs = clazz.getDeclaredAnnotations();
        if( directs!=null  && directs.length > 0 ) {
            for( Annotation direct : directs ) {
                if( direct.annotationType().equals(annotationClass))
                    return Tuple2.optionalOf( (A)direct, clazz );
            }
        }

        final Class<?> interfaces[] = clazz.getInterfaces();
        if( interfaces != null && interfaces.length > 0 ) {
            for (Class<?> ifc : interfaces) {
                final Annotation[] indirects = ifc.getDeclaredAnnotations();
                for( Annotation indirect : indirects ) {
                    if( indirect.annotationType().equals(annotationClass))
                        return Tuple2.optionalOf((A)indirect,ifc);
                }
            }
        }
        return empty();
    }

    /**
     * Gets JSON-RPC meta-information about method parameters.
     *
     * @param method actual method
     * @return map of parameters metadata by their names
     */
    @Nullable
    private java.util.Map<String, ParameterMetadata> getMethodParameters(@NotNull Method method) {
        Annotation[][] allParametersAnnotations = method.getParameterAnnotations();
        int methodParamsSize = allParametersAnnotations.length;
        Class<?>[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();

        ImmutableMap.Builder<String, ParameterMetadata> parametersMetadata = ImmutableMap.builder();
        for (int i = 0; i < methodParamsSize; i++) {
            Annotation[] parameterAnnotations = allParametersAnnotations[i];
            Optional<JsonRpcParam> jsonRpcParam = getAnnotation(parameterAnnotations, JsonRpcParam.class);
            if (!jsonRpcParam.isPresent()) {
                log.warn("Annotation @JsonRpcParam is not set for the {} parameter of a method '{}'", i, method.getName());
                return null;
            }

            String paramName = jsonRpcParam.get().value();
            boolean optional = getAnnotation(parameterAnnotations, JsonRpcOptional.class).isPresent();
            parametersMetadata.put(paramName, new ParameterMetadata(paramName, parameterTypes[i],
                    genericParameterTypes[i], i, optional));
        }

        try {
            return parametersMetadata.build();
        } catch (IllegalArgumentException e) {
            log.error( "There two parameters with the same name in method '" +
                    method.getName() +
                    "' of the class '" +
                    method.getDeclaringClass() +
                    "'", e);
            return null;
        }
    }

}
