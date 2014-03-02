package org.boon.core.reflection;

import org.boon.Lists;
import org.boon.Sets;
import org.boon.collections.ConcurrentHashSet;
import org.boon.collections.MultiMap;
import org.boon.core.Typ;
import org.boon.core.reflection.fields.FieldAccess;
import org.boon.core.reflection.impl.ConstructorAccessImpl;
import org.boon.core.reflection.impl.MethodAccessImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.boon.Exceptions.die;


/**
 * Created by Richard on 2/17/14.
 */
public class ClassMeta <T> implements Annotated{

    final Class<T> cls;

    final Map<String, MethodAccess> methodMap;

    final Set<ConstructorAccess<T>> constructorAccessSet;

    final MultiMap<String, MethodAccess> methodsMulti;
    final List <MethodAccess> methods;

    final Map<String, FieldAccess> fieldMap;
    final Map<String, FieldAccess> propertyMap;


    final List<FieldAccess> fields;
    final List<FieldAccess> properties;


    final ConstructorAccess<T> noArgConstructor;

    final static MethodAccess MANY_METHODS = new MethodAccessImpl(){
        @Override
        public Object invoke( Object object, Object... args ) {
            return die(Object.class, "Unable to invoke method as there are more than one with that same name", object, args);
        }

        @Override
        public boolean respondsTo(Class<?>[] parametersToMatch) {
            return false;
        }

        @Override
        public Iterable<AnnotationData> annotationData() {
            return die(Iterable.class, "Unable to use method as there are more than one with that same name");
        }

        @Override
        public boolean hasAnnotation( String annotationName ) {
            return die(Boolean.class, "Unable to invoke method as there are more than one with that same name");
        }

        @Override
        public AnnotationData annotation(String annotationName) {
            return die(AnnotationData.class, "Unable to invoke method as there are more than one with that same name");
        }

        @Override
        public Class<?>[] parameterTypes() {
            return die(Class[].class, "Unable to invoke method as there are more than one with that same name");
        }

        @Override
        public Type[] getGenericParameterTypes() {
            return die(Type[].class, "Unable to invoke method as there are more than one with that same name");
        }
    };
    private final Map<String, AnnotationData> annotationMap;
    private final List<AnnotationData> annotations;



    public ClassMeta( Class<T> cls ) {
        this.cls = cls;

        if (!cls.isInterface()) {
            fieldMap = Reflection.getAllAccessorFields( this.cls );
            fields = Lists.list(fieldMap.values());
        } else {
            fieldMap = Collections.EMPTY_MAP;
            fields = Collections.EMPTY_LIST;
        }
        propertyMap = Reflection.getPropertyFieldAccessors( this.cls );
        properties = Lists.list(propertyMap.values());


        Constructor<?>[] constructors = cls.getDeclaredConstructors();


        ConstructorAccess noArg = null;

        Set set = new HashSet();

        for (Constructor constructor : constructors ) {
            if (constructor.getParameterTypes().length == 0 ) {
                noArg = new ConstructorAccessImpl<>(constructor);
            }
            set.add(new ConstructorAccessImpl(constructor));
        }


        this.noArgConstructor = noArg;

        this.constructorAccessSet = (Set<ConstructorAccess<T>> ) Sets.safeSet(set);

        List<Class<?>> classes = getBaseClassesSuperFirst();



        methodMap = new ConcurrentHashMap<>(  );
        methodsMulti = new MultiMap<>(  );



        for (Class clasz : classes) {
            Method[] methods_ = clasz.getDeclaredMethods();

            for (Method m : methods_) {
                if ( methodMap.containsKey( m.getName() )) {
                    /** Checking for duplicates */
                    MethodAccessImpl invoker = ( MethodAccessImpl ) methodMap.get( m.getName() );
                    if (invoker == MANY_METHODS) {
                        //do nothing
                    }
                    else if (invoker.method.getParameterTypes().length != m.getParameterTypes().length) {
                        methodMap.put( m.getName(), MANY_METHODS );
                    } else {
                        boolean match = true;
                        for (int index =0; index < m.getParameterTypes().length; index++) {
                            if (m.getParameterTypes()[index] != invoker.method.getParameterTypes()[index]) {
                                match = false;
                            }
                        }
                        /* A match means a subclass overrode a base class. */
                        if ( match ) {
                            methodMap.put( m.getName(), new MethodAccessImpl( m ) );
                        } else {
                            /* Don't allow overloads. */
                            methodMap.put( m.getName(), MANY_METHODS );
                        }
                    }

                } else {
                    methodMap.put( m.getName(), new MethodAccessImpl( m ));
                }
                methodsMulti.put( m.getName(), new MethodAccessImpl( m ) );
            }
        }

        methods = Lists.list( methodsMulti.values() );



        annotationMap = Annotations.getAnnotationDataForClassAsMap( cls );
        annotations = Annotations.getAnnotationDataForClass(cls);

    }

    public static ClassMeta classMeta( Class<?> aClass ) {
        ClassMeta meta = Reflection.context()._classMetaMap.get( aClass );
        if (meta == null) {
            meta = new ClassMeta( aClass );
            Reflection.context()._classMetaMap.put( aClass, meta );
        }
        return meta;
    }


    public static ClassMeta classMetaEither(Object obj) {
        if (obj instanceof Class) {
            return classMeta((Class<?>) obj);
        } else {
            return classMeta(obj.getClass());
        }
    }

    public MethodAccess method(String name) {
        return methodMap.get( name );
    }


    public Iterable<MethodAccess> methods(String name) {
        return methodsMulti.getAll( name );
    }

    private List<Class<?>> getBaseClassesSuperFirst() {

        if (!cls.isInterface()) {
            List<Class<?>> classes = new ArrayList( 10 );
            Class<?> currentClass = cls;
            while (currentClass != Object.class) {
                classes.add( currentClass );
                currentClass = currentClass.getSuperclass();
            }
            java.util.Collections.reverse( classes );

            return classes;
        } else {
           return Lists.list(cls.getInterfaces());
        }

    }



    public Map<String, FieldAccess> fieldMap() {
        return fieldMap;
    }

    public Map<String, FieldAccess> propertyMap() {
        return propertyMap;
    }

    public Iterator<FieldAccess> fields() {
        return fields.iterator();
    }


    public Iterable<MethodAccess> methods() {
        return new Iterable<MethodAccess>() {
            @Override
            public Iterator<MethodAccess> iterator() {
                return methods.iterator();
            }
        };
    }

    public Iterator<FieldAccess> properties() {
        return properties.iterator();
    }



    public Iterable<ConstructorAccess<T>> constructors() {
        return new Iterable<ConstructorAccess<T>>() {
            @Override
            public Iterator<ConstructorAccess<T>> iterator() {
                return constructorAccessSet.iterator();
            }
        };
    }

    public  ConstructorAccess<T> noArgConstructor() {
        return this.noArgConstructor;
    }

    public <T> ConstructorAccess<T> declaredConstructor(Class<? extends Object> singleArg) {
        for (ConstructorAccess constructorAccess : constructorAccessSet) {
            if (constructorAccess.parameterTypes().length==1) {
                if (constructorAccess.parameterTypes()[0].isAssignableFrom(singleArg)) {
                    return constructorAccess;
                }
            }
        }
        return null;
    }



    public List<ConstructorAccess> oneArgumentConstructors() {
        List <ConstructorAccess> constructors = new ArrayList<>();
        for (ConstructorAccess constructorAccess : constructorAccessSet) {
            if (constructorAccess.parameterTypes().length==1) {

                constructors.add(constructorAccess);
            }
        }

        return constructors;

    }

    public Iterable<AnnotationData> annotationData() {
        return new Iterable<AnnotationData>() {
            @Override
            public Iterator<AnnotationData> iterator() {
                return annotations.iterator();
            }
        };
    }

    public boolean hasAnnotation(String annotationName) {
        return annotationMap.containsKey(annotationName);
    }

    public AnnotationData annotation(String annotationName) {
        return annotationMap.get(annotationName);
    }


    public boolean respondsTo(String methodName) {
        return methodMap.containsKey(methodName);
    }


    public boolean respondsTo(String methodName, Class<?>... types) {

        Iterable<MethodAccess> methods = this.methodsMulti.getAll(methodName);
        for (MethodAccess methodAccess : methods) {
           if (methodAccess.isStatic()) continue;
           if (methodAccess.respondsTo(types) ) {
              return true;
           };
        }
        return false;

    }


    public boolean respondsTo(String methodName, Object... args) {

        Iterable<MethodAccess> methods = this.methodsMulti.getAll(methodName);
        for (MethodAccess methodAccess : methods) {
            if (methodAccess.isStatic()) continue;
            if (methodAccess.respondsTo(args) ) {
                return true;
            };
        }
        return false;

    }



    public boolean respondsTo(String methodName, List list) {

        Object[] args = list.toArray(new Object[list.size()]);
        return respondsTo(methodName, args);
    }


    public boolean handles(Class<?> interfaceMethods) {
        Method[] declaredMethods = interfaceMethods.getDeclaredMethods();
        for (Method method : declaredMethods) {
            if (!respondsTo(method.getName(), method.getParameterTypes())) {
                return false;
            }
        }
        return true;
    }


    public Object invoke(T instance, String methodName,  Object... args) {
        return methodMap.get(methodName).invoke(instance, args);
    }


    public MethodAccess invokeMethodAccess(String methodName) {
        return methodMap.get(methodName);
    }

    public Object invokeStatic(String methodName,  Object... args) {
        return methodMap.get(methodName).invokeStatic(args);
    }


    public Object invoke(T instance, String methodName,  List<?> args) {

        Object[] array = args.toArray(new Object[args.size()]);
        return methodMap.get(methodName).invoke(instance, array);
    }


    public  boolean invokePredicate(Object instance, Object arg) {
        MethodAccess methodAccess = null;

        if (methods.size()==1) {
            methodAccess = methods.get(0);
        } else  {
            methodAccess = methodMap.get("test");
        }

        return (boolean) methodAccess.invoke(instance, arg);
    }

    public Object invokeReducer(Object instance, Object sum, Object value) {

        MethodAccess methodAccess;

        if (methods.size()==1) {
            methodAccess = methods.get(0);
        } else  {
            methodAccess = methodMap.get("test");
        }

        Class<?> arg1 = methodAccess.parameterTypes()[0];
        if (Typ.isPrimitiveNumber(arg1) && sum == null) {
            return methodAccess.invoke(instance, 0, value);
        } else {
            return methodAccess.invoke(instance, sum, value);
        }
    }

    public Object invokeFunction(Object instance, Object arg) {

        MethodAccess methodAccess = invokeFunctionMethodAccess();
        return methodAccess.invoke(instance, arg);
    }


    public MethodAccess invokeFunctionMethodAccess() {

        if (methods.size()==1) {
            return  methods.get(0).methodAccess();
        } else  {
            return  methodMap.get("apply").methodAccess();
        }
    }

    public String name() {
        return this.cls.getSimpleName();
    }

}
