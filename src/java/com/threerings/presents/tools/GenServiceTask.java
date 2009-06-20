//
// $Id$
//
// Narya library - tools for developing networked games
// Copyright (C) 2002-2009 Three Rings Design, Inc., All Rights Reserved
// http://www.threerings.net/code/narya/
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.threerings.presents.tools;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.HashSet;

import java.io.File;
import java.io.StringWriter;

import org.apache.velocity.VelocityContext;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import com.samskivert.util.ComparableArrayList;
import com.samskivert.util.StringUtil;

import com.threerings.util.ActionScript;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.data.InvocationMarshaller;
import com.threerings.presents.dobj.InvocationResponseEvent;
import com.threerings.presents.server.InvocationDispatcher;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationProvider;

/**
 * An Ant task for generating invocation service marshalling and
 * unmarshalling classes.
 * TODO: when generating the imports for exported action script files, there are just enough
 * conversions of primitive types (e.g. float -> Number), array types (e.g. int[] -> TypedArray)
 * and three rings utility types (e.g. float -> Float) to make the existing serivces work. It
 * should be possible to create a complete list of these conversions so that future services
 * can be generated without problems.
 */
public class GenServiceTask extends InvocationTask
{
    /** Used to keep track of custom InvocationListener derivations. */
    public class ServiceListener implements Comparable<ServiceListener>
    {
        public Class<?> listener;

        public ComparableArrayList<ServiceMethod> methods =
            new ComparableArrayList<ServiceMethod>();

        /** Contains all imports required for the parameters of the methods in this listener. */
        public ImportSet imports = new ImportSet();

        public ServiceListener (Class<?> service, Class<?> listener)
        {
            this.listener = listener;
            Method[] methdecls = listener.getDeclaredMethods();
            for (Method m : methdecls) {
                // service interface methods must be public and abstract
                if (!Modifier.isPublic(m.getModifiers()) &&
                    !Modifier.isAbstract(m.getModifiers())) {
                    continue;
                }
                if (_verbose) {
                    System.out.println("Adding " + m + ", imports are " +
                        StringUtil.toString(imports));
                }
                methods.add(new ServiceMethod(m, imports));
                if (_verbose) {
                    System.out.println("Added " + m + ", imports are " +
                        StringUtil.toString(imports));
                }
            }
            methods.sort();
        }

        /**
         * Checks whether any of our methods have parameterized types.
         */
        public boolean hasParameterizedMethodArgs ()
        {
            return Iterables.any(methods, new Predicate<ServiceMethod>() {
                public boolean apply (ServiceMethod sm) {
                    return sm.hasParameterizedArgs();
                }
            });
        }

        public String getName ()
        {
            String name = GenUtil.simpleName(listener);
            name = StringUtil.replace(name, "Listener", "");
            int didx = name.indexOf(".");
            return name.substring(didx+1);
        }

        // from interface Comparable<ServiceListener>
        public int compareTo (ServiceListener other)
        {
            return getName().compareTo(other.getName());
        }

        @Override
        public boolean equals (Object other)
        {
            return getClass().equals(other.getClass()) &&
                listener.equals(((ServiceListener)other).listener);
        }

        @Override
        public int hashCode ()
        {
            return listener.getName().hashCode();
        }
    }

    /** Used to track services for which we should not generate a provider interface. */
    public class Providerless
    {
        public void setService (String className)
        {
            _providerless.add(className);
        }
    }

    /** Used to track services for which we should create listener adapters in actionscript. */
    public class Adapter
    {
        public void setService (String className)
        {
            _aslistenerAdapters.add(className);
        }
    }

    /**
     * Configures the path to our ActionScript source files.
     */
    public void setAsroot (File asroot)
    {
        _asroot = asroot;
    }

    public Providerless createProviderless ()
    {
        return new Providerless();
    }

    public Adapter createAdapter ()
    {
        return new Adapter();
    }

    // documentation inherited
    @Override
    protected void processService (File source, Class<?> service)
    {
        System.out.println("Processing " + service.getName() + "...");

        // verify that the service class name is as we expect it to be
        if (!service.getName().endsWith("Service")) {
            System.err.println("Cannot process '" + service.getName() + "':");
            System.err.println("Service classes must be named SomethingService.");
            return;
        }

        ActionScript asa = service.getAnnotation(ActionScript.class);
        boolean skipAS = (asa != null) && asa.omit();

        ServiceDescription desc = new ServiceDescription(service);
        generateMarshaller(source, desc, skipAS);
        generateDispatcher(source, desc);
        if (!_providerless.contains(service.getSimpleName())) {
            generateProvider(source, desc);
        }
    }

    protected void generateMarshaller (File source, ServiceDescription sdesc, boolean skipAS)
    {
        if (_verbose) {
            System.out.println("Generating marshaller");
        }

        String sname = sdesc.sname;
        String name = StringUtil.replace(sname, "Service", "");
        String mname = StringUtil.replace(sname, "Service", "Marshaller");
        String mpackage = StringUtil.replace(sdesc.spackage, ".client", ".data");

        // ----------- Part I - java marshaller

        // start with all imports (service methods and listener methods)
        ImportSet imports = sdesc.constructAllImports();

        // import things marshaller will always need
        imports.add(sdesc.service);
        imports.add(Client.class);
        imports.add(InvocationMarshaller.class);

        // if any listeners are to be present, they need the response event
        if (sdesc.listeners.size() > 0) {
            imports.add(InvocationResponseEvent.class);
        }

        // import classes contained in arrays
        imports.translateClassArrays();

        // get rid of java.lang stuff and primitives
        imports.removeGlobals();

        // get rid of all arrays (they are automatic in java)
        imports.removeArrays();

        // for each listener type, also import the corresponding marshaller
        imports.duplicateAndMunge("*Listener",
            "Service", "Marshaller",
            "Listener", "Marshaller",
            ".client.", ".data.");

        // import the parent class of Foo$Bar
        imports.swapInnerClassesForParents();

        // remove imports in our own package
        imports.removeSamePackage(mpackage);

        VelocityContext ctx = new VelocityContext();
        ctx.put("name", name);
        ctx.put("package", mpackage);
        ctx.put("methods", sdesc.methods);
        ctx.put("listeners", sdesc.listeners);
        ctx.put("imports", imports.toList());

        // determine the path to our marshaller file
        String mpath = source.getPath();
        mpath = StringUtil.replace(mpath, "Service", "Marshaller");
        mpath = replacePath(mpath, "/client/", "/data/");

        try {
            StringWriter sw = new StringWriter();
            _velocity.mergeTemplate(MARSHALLER_TMPL, "UTF-8", ctx, sw);
            writeFile(mpath, sw.toString());

        } catch (Exception e) {
            System.err.println("Failed processing template");
            e.printStackTrace(System.err);
        }

        // if we're not configured with an ActionScript source root, don't generate the
        // ActionScript versions
        if (_asroot == null || skipAS) {
            return;
        }

        // ----------- Part II - as marshaller

        // start with the service method imports
        imports = sdesc.imports.clone();

        // add some things that marshallers just need
        imports.add(sdesc.service);
        imports.add(Client.class);
        imports.add(InvocationMarshaller.class);

        // replace inner classes with action script equivalents
        imports.translateInnerClasses();

        // replace primitive types with OOO types (required for unboxing)
        imports.replace("byte", "com.threerings.util.Byte");
        imports.replace("int", "com.threerings.util.Integer");
        imports.replace("boolean", "com.threerings.util.langBoolean");
        imports.replace("[B", "flash.utils.ByteArray");
        imports.replace("float", "com.threerings.util.Float");
        imports.replace("[I", "com.threerings.io.TypedArray");

        // ye olde special case - any method that uses a default listener
        // causes the need for the default listener marshaller
        imports.duplicateAndMunge("*.InvocationService_InvocationListener",
            "InvocationService_InvocationListener",
            "InvocationMarshaller_ListenerMarshaller",
            ".client.", ".data.");

        // any use of a listener requires the listener marshaller
        imports.pushOut("*.InvocationService_InvocationListener");
        imports.duplicateAndMunge("*Listener",
            "Service", "Marshaller",
            "Listener", "Marshaller",
            ".client.", ".data.");
        imports.popIn();

        // get rid of java.lang stuff and any remaining primitives
        imports.removeGlobals();

        if (imports.removeAll("[L*") > 0) {
            imports.add("com.threerings.io.TypedArray");
        }

        // get rid of remaining arrays
        imports.removeArrays();

        // remove imports in our own package
        imports.removeSamePackage(mpackage);

        ctx.put("imports", imports.toList());

        // now generate ActionScript versions of our marshaller
        try {
            // make sure our marshaller directory exists
            String mppath = mpackage.replace('.', File.separatorChar);
            new File(_asroot + File.separator + mppath).mkdirs();

            // generate an ActionScript version of our marshaller
            String ampath = _asroot + File.separator + mppath +
                File.separator + mname + ".as";
            StringWriter sw = new StringWriter();
            _velocity.mergeTemplate(AS_MARSHALLER_TMPL, "UTF-8", ctx, sw);
            writeFile(ampath, sw.toString());

            // ----------- Part III - as listener marshallers

            Class<?> imlm = InvocationMarshaller.ListenerMarshaller.class;

            // now generate ActionScript versions of our listener marshallers
            // because those have to be in separate files
            for (ServiceListener listener : sdesc.listeners) {
                // start imports with just those used by listener methods
                imports = listener.imports.clone();

                // always need the super class and the listener class
                imports.add(imlm);
                imports.add(listener.listener);

                // replace '$' with '_' for action script naming convention
                imports.translateInnerClasses();

                // convert primitive java types to ooo util types
                imports.replace("long", "com.threerings.util.Long");

                // convert object arrays to typed arrays
                if (imports.removeAll("[L*") > 0) {
                    imports.add("com.threerings.io.TypedArray");
                }

                // get rid of remaining primitives and java.lang types
                imports.removeGlobals();

                // remove imports in our own package
                imports.removeSamePackage(mpackage);

                ctx.put("imports", imports.toList());
                ctx.put("listener", listener);
                sw = new StringWriter();
                _velocity.mergeTemplate(
                    AS_LISTENER_MARSHALLER_TMPL, "UTF-8", ctx, sw);
                String aslpath = _asroot + File.separator + mppath +
                    File.separator + mname + "_" +
                    listener.getName() + "Marshaller.as";
                writeFile(aslpath, sw.toString());
            }

        } catch (Exception e) {
            System.err.println("Failed processing template");
            e.printStackTrace(System.err);
        }

        // ----------- Part IV - as service

        // then make some changes to the context and generate ActionScript
        // versions of the service interface itself

        // start with the service methods' imports
        imports = sdesc.imports.clone();

        // add some things required by action script
        imports.add(Client.class);
        imports.add(InvocationService.class);

        // allow primitive types in service methods
        imports.replace("[B", "flash.utils.ByteArray");
        imports.replace("[I", "com.threerings.io.TypedArray");

        // convert java primitive types to ooo util types
        imports.replace("java.lang.Integer", "com.threerings.util.Integer");

        if (imports.removeAll("[L*") > 0) {
            imports.add("com.threerings.io.TypedArray");
        }

        // get rid of primitives and java.lang classes
        imports.removeGlobals();

        // get rid of remaining arrays
        imports.removeArrays();

        // change imports of Foo$Bar to Foo_Bar
        imports.translateInnerClasses();

        // remove imports in our own package
        imports.removeSamePackage(sdesc.spackage);

        ctx.put("imports", imports.toList());
        ctx.put("package", sdesc.spackage);

        try {
            // make sure our service directory exists
            String sppath = sdesc.spackage.replace('.', File.separatorChar);
            new File(_asroot + File.separator + sppath).mkdirs();

            // generate an ActionScript version of our service
            String aspath = _asroot + File.separator + sppath +
                File.separator + sname + ".as";
            StringWriter sw = new StringWriter();
            _velocity.mergeTemplate(AS_SERVICE_TMPL, "UTF-8", ctx, sw);
            writeFile(aspath, sw.toString());

            // ----------- Part V - as service listeners

            Class<?> isil = InvocationService.InvocationListener.class;

            // also generate ActionScript versions of any inner listener
            // interfaces because those have to be in separate files
            for (ServiceListener listener : sdesc.listeners) {

                // start with just the imports needed by listener methods
                imports = listener.imports.clone();

                // add things needed by all listeners
                imports.add(isil);
                imports.add(listener.listener);

                // change Foo$Bar to Foo_Bar
                imports.translateInnerClasses();

                // use a typed array for any arrays of objects
                if (imports.removeAll("[L*") > 0) {
                    imports.add("com.threerings.io.TypedArray");
                }

                // convert java primitive types to ooo util types
                imports.replace("long", "com.threerings.util.Long");

                // get rid of remaining primitives and java.lang types
                imports.removeGlobals();

                // remove imports in our own package
                imports.removeSamePackage(sdesc.spackage);

                ctx.put("imports", imports.toList());
                ctx.put("listener", listener);

                sw = new StringWriter();
                _velocity.mergeTemplate(AS_LISTENER_SERVICE_TMPL, "UTF-8", ctx, sw);
                String aslpath = _asroot + File.separator + sppath +
                    File.separator + sname + "_" +
                    listener.getName() + "Listener.as";
                writeFile(aslpath, sw.toString());

                if (_aslistenerAdapters.contains(sname)) {
                    sw = new StringWriter();
                    _velocity.mergeTemplate(AS_LISTENER_ADAPTER_SERVICE_TMPL, "UTF-8", ctx, sw);
                    String aslapath = _asroot + File.separator + sppath +
                        File.separator + sname + "_" +
                        listener.getName() + "ListenerAdapter.as";
                    writeFile(aslapath, sw.toString());
                }
            }

        } catch (Exception e) {
            System.err.println("Failed processing template");
            e.printStackTrace(System.err);
        }
    }

    protected void generateDispatcher (File source, ServiceDescription sdesc)
    {
        if (_verbose) {
            System.out.println("Generating dispatcher");
        }

        String name = StringUtil.replace(sdesc.sname, "Service", "");
        String dpackage = StringUtil.replace(
            sdesc.spackage, ".client", ".server");

        // start with the imports required by service methods
        ImportSet imports = sdesc.imports.clone();

        // If any listeners are to be used in dispatches, we need to import the service
        if (sdesc.listeners.size() > 0) {
            imports.add(sdesc.service);
        }

        // swap Client for ClientObject
        imports.add(ClientObject.class);
        imports.remove(Client.class);

        // add some classes required for all dispatchers
        imports.add(InvocationDispatcher.class);
        imports.add(InvocationException.class);

        // import classes contained in arrays
        imports.translateClassArrays();

        // get rid of primitives and java.lang types
        imports.removeGlobals();

        // get rid of arrays
        imports.removeArrays();

        // import the Marshaller corresponding to the service
        imports.addMunged(sdesc.service,
            "Service", "Marshaller",
            ".client.", ".data.");

        // import Foo instead of Foo$Bar
        imports.swapInnerClassesForParents();

        // remove imports in our own package
        imports.removeSamePackage(dpackage);

        VelocityContext ctx = new VelocityContext();
        ctx.put("name", name);
        ctx.put("package", dpackage);
        ctx.put("methods", sdesc.methods);
        ctx.put("imports", imports.toList());

        try {
            StringWriter sw = new StringWriter();
            _velocity.mergeTemplate(DISPATCHER_TMPL, "UTF-8", ctx, sw);

            // determine the path to our marshaller file
            String mpath = source.getPath();
            mpath = StringUtil.replace(mpath, "Service", "Dispatcher");
            mpath = replacePath(mpath, "/client/", "/server/");

            writeFile(mpath, sw.toString());

        } catch (Exception e) {
            System.err.println("Failed processing template");
            e.printStackTrace(System.err);
        }
    }

    protected void generateProvider (File source, ServiceDescription sdesc)
    {
        if (_verbose) {
            System.out.println("Generating provider");
        }

        String name = StringUtil.replace(sdesc.sname, "Service", "");
        String mpackage = StringUtil.replace(sdesc.spackage, ".client", ".server");

        // start with imports required by service methods
        ImportSet imports = sdesc.imports.clone();

        // swap Client for ClientObject
        imports.add(ClientObject.class);
        imports.remove(Client.class);

        // import superclass and service
        imports.add(InvocationProvider.class);
        imports.add(sdesc.service);

        // any method that takes a listener may throw this
        if (sdesc.hasAnyListenerArgs()) {
            imports.add(InvocationException.class);
        }

        // import classes contained in arrays
        imports.translateClassArrays();

        // get rid of primitives and java.lang types
        imports.removeGlobals();

        // get rid of arrays
        imports.removeArrays();

        // import Foo instead of Foo$Bar
        imports.swapInnerClassesForParents();

        // remove imports in our own package
        imports.removeSamePackage(mpackage);

        VelocityContext ctx = new VelocityContext();
        ctx.put("name", name);
        ctx.put("package", mpackage);
        ctx.put("methods", sdesc.methods);
        ctx.put("listeners", sdesc.listeners);
        ctx.put("imports", imports.toList());

        try {
            StringWriter sw = new StringWriter();
            _velocity.mergeTemplate(PROVIDER_TMPL, "UTF-8", ctx, sw);

            // determine the path to our provider file
            String mpath = source.getPath();
            mpath = StringUtil.replace(mpath, "Service", "Provider");
            mpath = replacePath(mpath, "/client/", "/server/");

            writeFile(mpath, sw.toString());

        } catch (Exception e) {
            System.err.println("Failed processing template");
            e.printStackTrace(System.err);
        }
    }

    /** Rolls up everything needed for the generate* methods. */
    protected class ServiceDescription
    {
        public Class<?> service;
        public String sname;
        public String spackage;
        public ImportSet imports = new ImportSet();
        public ComparableArrayList<ServiceMethod> methods =
            new ComparableArrayList<ServiceMethod>();
        public ComparableArrayList<ServiceListener> listeners =
            new ComparableArrayList<ServiceListener>();

        public ServiceDescription (Class<?> serviceClass)
        {
            service = serviceClass;
            sname = service.getSimpleName();
            spackage = service.getPackage().getName();

            // look through and locate our service methods, also locating any
            // custom InvocationListener derivations along the way
            Method[] methdecls = service.getDeclaredMethods();
            for (Method m : methdecls) {
                // service interface methods must be public and abstract
                if (!Modifier.isPublic(m.getModifiers()) &&
                    !Modifier.isAbstract(m.getModifiers())) {
                    continue;
                }
                // check this method for custom listener declarations
                Class<?>[] args = m.getParameterTypes();
                for (Class<?> arg : args) {
                    if (_ilistener.isAssignableFrom(arg) &&
                        GenUtil.simpleName(arg).startsWith(sname + ".")) {
                        checkedAdd(listeners, new ServiceListener(service, arg));
                    }
                }
                if (_verbose) {
                    System.out.println("Adding " + m + ", imports are " +
                        StringUtil.toString(imports));
                }
                methods.add(new ServiceMethod(m, imports));
                if (_verbose) {
                    System.out.println("Added " + m + ", imports are " +
                        StringUtil.toString(imports));
                }
            }
            listeners.sort();
            methods.sort();
        }

        /**
         * Checks if any of the service method arguments are listener types.
         */
        public boolean hasAnyListenerArgs ()
        {
            return Iterables.any(methods, new Predicate<ServiceMethod>() {
                public boolean apply (ServiceMethod sm) {
                    return !sm.listenerArgs.isEmpty();
                }
            });
        }

        /**
         * Constructs a union of the imports of the service methods and all listener methods.
         */
        public ImportSet constructAllImports ()
        {
            ImportSet allimports = imports.clone();
            for (ServiceListener listener : listeners) {
                allimports.addAll(listener.imports);
            }
            return allimports;
        }
    }

    /** The path to our ActionScript source files. */
    protected File _asroot;

    /** Services for which we should not generate provider interfaces. */
    protected HashSet<String> _providerless = Sets.newHashSet();

    /** Services for which we should generate actionscript listener adapters. */
    protected HashSet<String> _aslistenerAdapters = Sets.newHashSet();

    /** Specifies the path to the marshaller template. */
    protected static final String MARSHALLER_TMPL =
        "com/threerings/presents/tools/marshaller.tmpl";

    /** Specifies the path to the dispatcher template. */
    protected static final String DISPATCHER_TMPL =
        "com/threerings/presents/tools/dispatcher.tmpl";

    /** Specifies the path to the provider template. */
    protected static final String PROVIDER_TMPL =
        "com/threerings/presents/tools/provider.tmpl";

    /** Specifies the path to the ActionScript service template. */
    protected static final String AS_SERVICE_TMPL =
        "com/threerings/presents/tools/service_as.tmpl";

    /** Specifies the path to the ActionScript listener service template. */
    protected static final String AS_LISTENER_SERVICE_TMPL =
        "com/threerings/presents/tools/service_listener_as.tmpl";

    /** Specifies the path to the ActionScript listener adapter service template. */
    protected static final String AS_LISTENER_ADAPTER_SERVICE_TMPL =
        "com/threerings/presents/tools/service_listener_adapter_as.tmpl";

    /** Specifies the path to the ActionScript marshaller template. */
    protected static final String AS_MARSHALLER_TMPL =
        "com/threerings/presents/tools/marshaller_as.tmpl";

    /** Specifies the path to the ActionScript listener marshaller template. */
    protected static final String AS_LISTENER_MARSHALLER_TMPL =
        "com/threerings/presents/tools/marshaller_listener_as.tmpl";
}
