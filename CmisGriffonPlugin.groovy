/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author Andres Almiray
 */
 class CmisGriffonPlugin {
    // the plugin version
    String version = '0.4'
    // the version or versions of Griffon the plugin is designed for
    String griffonVersion = '1.1.0 > *'
    // the other plugins this plugin depends on
    Map dependsOn = [:]
    // resources that are included in plugin packaging
    List pluginIncludes = []
    // the plugin license
    String license = 'Apache Software License 2.0'
    // Toolkit compatibility. No value means compatible with all
    // Valid values are: swing, javafx, swt, pivot, gtk
    List toolkits = []
    // Platform compatibility. No value means compatible with all
    // Valid values are:
    // linux, linux64, windows, windows64, macosx, macosx64, solaris
    List platforms = []
    // URL where documentation can be found
    String documentation = ''
    // URL where source can be found
    String source = 'https://github.com/griffon/griffon-cmis-plugin'

    List authors = [
        [
            name: 'Andres Almiray',
            email: 'aalmiray@yahoo.com'
        ]
    ]
    String title = 'Open CMIS support'
    String description = '''
The CMIS plugin enables the usage of the [CMIS][1] specification via [Apache Chemistry][2].

Usage
-----
Upon installation the plugin will generate the following artifacts in `$appdir/griffon-app/conf`:

 * CmisConfig.groovy - contains repository definitions.

A new dynamic method named `withCmis` will be injected into all controllers,
giving you access to a `org.apache.chemistry.opencmis.client.api.Session` object, with which you'll be able
to make calls to the repository. Remember to make all repositry calls off the EDT
otherwise your application may appear unresponsive when doing long computations
inside the EDT.

This method is aware of multiple repositories. If no sessionName is specified when calling
it then the default repository will be selected. Here are two example usages, the first
queries against the default repository while the second queries a repository whose name has
been configured as 'internal'

    package sample
    class SampleController {
        def queryAllRepositories = {
            withCmis { sessionName, session -> ... }
            withCmis('internal') { sessionName, session -> ... }
        }
    }

This method is also accessible to any component through the singleton `griffon.plugins.cmis.CmisConnector`.
You can inject these methods to non-artifacts via metaclasses. Simply grab hold of a particular metaclass and call
`CmisEnhancer.enhance(metaClassInstance, cmisProviderInstance)`.

Configuration
-------------
### Dynamic method injection

The `withCmis()` dynamic method will be added to controllers by default. You can
change this setting by adding a configuration flag in `griffon-app/conf/Config.groovy`

    griffon.cmis.injectInto = ['controller', 'service']

### Events

The following events will be triggered by this addon

 * CmisConnectStart[config, sessionName] - triggered before connecting to the repository
 * CmisConnectEnd[sessionName, session] - triggered after connecting to the repository
 * CmisDisconnectStart[config, sessionName, session] - triggered before disconnecting from the repository
 * CmisDisconnectEnd[config, sessionName] - triggered after disconnecting from the repository

### Multiple Stores

The config file `CmisConfig.groovy` defines a default session block. As the name
implies this is the session used by default, however you can configure named sessions
by adding a new config block. For example connecting to a session whose name is 'internal'
can be done in this way

    sessions {
        internal {
            (SessionParameter.ATOMPUB_URL)   : 'http://acme.com/atom/',
            (SessionParameter.BINDING_TYPE)  : BindingType.ATOMPUB.value(),
            (SessionParameter.REPOSITORY_ID) : 'ACME1'
        }
    }

This block can be used inside the `environments()` block in the same way as the
default session block is used.

### Example

Taken from [http://chemistry.apache.org/java/developing/guide.html][3] the following controller assumes the default
connection is setup to query an AtomPub repo ([http://repo.opencmis.org/inmemory/atom][4])

        class SampleController {
            void onReadyEnd(GriffonApplication app) {
                withCmis { sessionName, session ->
                    for(o in session.rootFolder.children) {
                        println "${o.name} := ${o.type.displayName}"
                    }
                }
            }
        }

The usage of the `onReadyEnd` event handler is just for demonstration purposes; the method `withCmis()` can be called
from anywhere in the controller.

Testing
-------
The `withCmis()` dynamic method will not be automatically injected during unit testing, because addons are simply not initialized
for this kind of tests. However you can use `CmisEnhancer.enhance(metaClassInstance, cmisProviderInstance)` where 
`cmisProviderInstance` is of type `griffon.plugins.cmis.CmisProvider`. The contract for this interface looks like this

    public interface CmisProvider {
        Object withCmis(Closure closure);
        Object withCmis(String sessionName, Closure closure);
        <T> T withCmis(CallableWithArgs<T> callable);
        <T> T withCmis(String sessionName, CallableWithArgs<T> callable);
    }

It's up to you define how these methods need to be implemented for your tests. For example, here's an implementation that never
fails regardless of the arguments it receives

    class MyCmisProvider implements CmisProvider {
        Object withCmis(String sessionName = 'default', Closure closure) { null }
        public <T> T withCmis(String sessionName = 'default', CallableWithArgs<T> callable) { null }
    }

This implementation may be used in the following way

    class MyServiceTests extends GriffonUnitTestCase {
        void testSmokeAndMirrors() {
            MyService service = new MyService()
            CmisEnhancer.enhance(service.metaClass, new MyCmisProvider())
            // exercise service methods
        }
    }

[1]: http://docs.oasis-open.org/cmis/CMIS/v1.0/cmis-spec-v1.0.html
[2]: http://chemistry.apache.org/java/opencmis.html
[3]: http://chemistry.apache.org/java/developing/guide.html
[4]: http://repo.opencmis.org/inmemory/atom
'''
}