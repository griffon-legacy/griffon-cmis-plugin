
Open CMIS support
-----------------

Plugin page: [http://artifacts.griffon-framework.org/plugin/cmis](http://artifacts.griffon-framework.org/plugin/cmis)


The CMIS plugin enables the usage of the [CMIS][1] specification via [Apache Chemistry][2].

Usage
-----
Upon installation the plugin will generate the following artifacts in
`$appdir/griffon-app/conf`:

 * CmisConfig.groovy - contains repository definitions.

A new dynamic method named `withCmis` will be injected into all controllers,
giving you access to a `org.apache.chemistry.opencmis.client.api.Session`
object, with which you'll be able to make calls to the repository. Remember to
make all repository calls off the UI thread otherwise your application may appear
unresponsive when doing long computations inside the UI thread.

This method is aware of multiple repositories. If no sessionName is specified
when calling it then the default repository will be selected. Here are two
example usages, the first queries against the default repository while the
second queries a repository whose name has been configured as 'internal'

    package sample
    class SampleController {
        def queryAllRepositories = {
            withCmis { sessionName, session -> ... }
            withCmis('internal') { sessionName, session -> ... }
        }
    }

The following list enumerates all the variants of the injected method

 * `<R> R withCmis(Closure<R> stmts)`
 * `<R> R withCmis(CallableWithArgs<R> stmts)`
 * `<R> R withCmis(String sessionName, Closure<R> stmts)`
 * `<R> R withCmis(String sessionName, CallableWithArgs<R> stmts)`

These methods are also accessible to any component through the singleton
`griffon.plugins.cmis.CmisEnhancer`. You can inject these methods to
non-artifacts via metaclasses. Simply grab hold of a particular metaclass and
call `CmisEnhancer.enhance(metaClassInstance)`.

Configuration
-------------

### CmisAware AST Transformation

The preferred way to mark a class for method injection is by annotating it with
`@griffon.plugins.cmis.CmisAware`. This transformation injects the
`griffon.plugins.cmis.CmisContributionHandler` interface and default behavior
that fulfills the contract.

### Dynamic Method Injection

Dynamic methods will be added to controllers by default. You can
change this setting by adding a configuration flag in `griffon-app/conf/Config.groovy`

    griffon.cmis.injectInto = ['controller', 'service']

Dynamic method injection will be skipped for classes implementing
`griffon.plugins.cmis.CmisContributionHandler`.

### Events

The following events will be triggered by this addon

 * CmisConnectStart[config, sessionName] - triggered before connecting to the repository
 * CmisConnectEnd[sessionName, session] - triggered after connecting to the repository
 * CmisDisconnectStart[config, sessionName, session] - triggered before disconnecting from the repository
 * CmisDisconnectEnd[config, sessionName] - triggered after disconnecting from the repository

### Multiple Stores

The config file `CmisConfig.groovy` defines a default session block. As the
name implies this is the session used by default, however you can configure
named sessions by adding a new config block. For example connecting to a
session whose name is 'internal' can be done in this way

    sessions {
        internal {
            (SessionParameter.ATOMPUB_URL)   : 'http://acme.com/atom/',
            (SessionParameter.BINDING_TYPE)  : BindingType.ATOMPUB.value(),
            (SessionParameter.REPOSITORY_ID) : 'ACME1'
        }
    }

This block can be used inside the `environments()` block in the same way as the
default session block is used.

### Configuration Storage

The plugin will load and store the contents of `CmisConfig.groovy` inside the
application's configuration, under the `pluginConfig` namespace. You may retrieve
and/or update values using

    app.config.pluginConfig.cmis

### Connect at Startup

The plugin will attempt a connection to the default database at startup. If this
behavior is not desired then specify the following configuration flag in
`Config.groovy`

    griffon.cmis.connect.onstartup = false

### Example

Taken from [http://chemistry.apache.org/java/developing/guide.html][3] the
following controller assumes the default connection is setup to query an
AtomPub repo ([http://repo.opencmis.org/inmemory/atom][4])

        package sample
        class SampleController {
            void onReadyEnd(GriffonApplication app) {
                withCmis { sessionName, session ->
                    for(o in session.rootFolder.children) {
                        println "${o.name} := ${o.type.displayName}"
                    }
                }
            }
        }

The usage of the `onReadyEnd` event handler is just for demonstration purposes;
the method `withCmis()` can be called from anywhere in the controller.

The plugin exposes a Java friendly API to make the exact same calls from Java,
or any other JVM language for that matter. Here's for example the previous code
rewritten in Java. Note the usage of @CmisWare on a Java class

        package sample;
        import griffon.core.GriffonApplication;
        import griffon.util.CallableWithArgs;
        import org.apache.chemistry.opencmis.client.api.CmisObject;
        import org.apache.chemistry.opencmis.client.api.Session;
        import org.codehaus.griffon.runtime.core.AbstractGriffonController;
        @griffon.plugins.cmis.CmisAware
        public class SampleController extends AbstractGriffonController {
            private SampleModel model;
        
            public void setModel(SampleModel model) {
                this.model = model;
            }
        
            public void onReadyEnd(GriffonApplication app) {
                withCmis(new CallableWithArgs<Void>() {
                    public Void call(Object[] args) {
                        Session session = (Session) args[1];
                        for (CmisObject o : session.getRootFolder().getChildren()) {
                            System.out.println(
                                o.getName() +
                                " := " +
                                o.getType().getDisplayName()
                            );
                        }
        
                        return null;
                    }
                });
            }
        }

Testing
-------

Dynamic methods will not be automatically injected during unit testing, because
addons are simply not initialized for this kind of tests. However you can use
`CmisEnhancer.enhance(metaClassInstance, cmisProviderInstance)` where
`cmisProviderInstance` is of type `griffon.plugins.cmis.CmisProvider`.
The contract for this interface looks like this

    public interface CmisProvider {
        <R> R withCmis(Closure<R> closure);
        <R> R withCmis(CallableWithArgs<R> callable);
        <R> R withCmis(String sessionName, Closure<R> closure);
        <R> R withCmis(String sessionName, CallableWithArgs<R> callable);
    }

It's up to you define how these methods need to be implemented for your tests.
For example, here's an implementation that never fails regardless of the
arguments it receives

    class MyCmisProvider implements CmisProvider {
        public <R> R withCmis(Closure<R> closure) { null }
        public <R> R withCmis(CallableWithArgs<R> callable) { null }
        public <R> R withCmis(String sessionName, Closure<R> closure) { null }
        public <R> R withCmis(String sessionName, CallableWithArgs<R> callable) { null }
    }

This implementation may be used in the following way

    class MyServiceTests extends GriffonUnitTestCase {
        void testSmokeAndMirrors() {
            MyService service = new MyService()
            CmisEnhancer.enhance(service.metaClass, new MyCmisProvider())
            // exercise service methods
        }
    }

On the other hand, if the service is annotated with `@CmisAware` then usage
of `CmisEnhancer` should be avoided at all costs. Simply set
`cmisProviderInstance` on the service instance directly, like so, first the
service definition

    @griffon.plugins.cmis.CmisAware
    class MyService {
        def serviceMethod() { ... }
    }

Next is the test

    class MyServiceTests extends GriffonUnitTestCase {
        void testSmokeAndMirrors() {
            MyService service = new MyService()
            service.cmisProvider = new MyCmisProvider()
            // exercise service methods
        }
    }

Tool Support
------------

### DSL Descriptors

This plugin provides DSL descriptors for Intellij IDEA and Eclipse (provided
you have the Groovy Eclipse plugin installed). These descriptors are found
inside the `griffon-cmis-compile-x.y.z.jar`, with locations

 * dsdl/cmis.dsld
 * gdsl/cmis.gdsl

### Lombok Support

Rewriting Java AST in a similar fashion to Groovy AST transformations is
possible thanks to the [lombok][5] plugin.

#### JavaC

Support for this compiler is provided out-of-the-box by the command line tools.
There's no additional configuration required.

#### Eclipse

Follow the steps found in the [Lombok][5] plugin for setting up Eclipse up to
number 5.

 6. Go to the path where the `lombok.jar` was copied. This path is either found
    inside the Eclipse installation directory or in your local settings. Copy
    the following file from the project's working directory

         $ cp $USER_HOME/.griffon/<version>/projects/<project>/plugins/cmis-<version>/dist/griffon-cmis-compile-<version>.jar .

 6. Edit the launch script for Eclipse and tweak the boothclasspath entry so
    that includes the file you just copied

        -Xbootclasspath/a:lombok.jar:lombok-pg-<version>.jar:        griffon-lombok-compile-<version>.jar:griffon-cmis-compile-<version>.jar

 7. Launch Eclipse once more. Eclipse should be able to provide content assist
    for Java classes annotated with `@CmisAware`.

#### NetBeans

Follow the instructions found in [Annotation Processors Support in the NetBeans
IDE, Part I: Using Project Lombok][6]. You may need to specify
`lombok.core.AnnotationProcessor` in the list of Annotation Processors.

NetBeans should be able to provide code suggestions on Java classes annotated
with `@CmisAware`.

#### Intellij IDEA

Follow the steps found in the [Lombok][5] plugin for setting up Intellij IDEA
up to number 5.

 6. Copy `griffon-cmis-compile-<version>.jar` to the `lib` directory

         $ pwd
           $USER_HOME/Library/Application Support/IntelliJIdea11/lombok-plugin
         $ cp $USER_HOME/.griffon/<version>/projects/<project>/plugins/cmis-<version>/dist/griffon-cmis-compile-<version>.jar lib

 7. Launch IntelliJ IDEA once more. Code completion should work now for Java
    classes annotated with `@CmisAware`.

[1]: http://docs.oasis-open.org/cmis/CMIS/v1.0/cmis-spec-v1.0.html
[2]: http://chemistry.apache.org/java/opencmis.html
[3]: http://chemistry.apache.org/java/developing/guide.html
[4]: http://repo.opencmis.org/inmemory/atom
[5]: /plugin/lombok
[6]: http://netbeans.org/kb/docs/java/annotations-lombok.html

### Building

This project requires all of its dependencies be available from maven compatible repositories.
Some of these dependencies have not been pushed to the Maven Central Repository, however you
can obtain them from [lombok-dev-deps][lombok-dev-deps].

Follow the instructions found there to install the required dependencies into your local Maven
repository before attempting to build this plugin.

[lombok-dev-deps]: https://github.com/aalmiray/lombok-dev-deps