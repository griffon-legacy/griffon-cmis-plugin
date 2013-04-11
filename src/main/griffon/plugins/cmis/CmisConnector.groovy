/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package griffon.plugins.cmis

import org.apache.chemistry.opencmis.client.api.Session
import org.apache.chemistry.opencmis.client.api.SessionFactory
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl

import griffon.core.GriffonApplication
import griffon.util.Environment
import griffon.util.Metadata
import griffon.util.CallableWithArgs
import griffon.util.ConfigUtils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Andres Almiray
 */
@Singleton
final class CmisConnector {
    private static final String DEFAULT = 'default'
    private static final Logger LOG = LoggerFactory.getLogger(CmisConnector)
    private final SessionFactory sessionFactory = SessionFactoryImpl.newInstance()

    ConfigObject createConfig(GriffonApplication app) {
        if (!app.config.pluginConfig.cmis) {
            app.config.pluginConfig.cmis = ConfigUtils.loadConfigWithI18n('CmisConfig')
        }
        app.config.pluginConfig.cmis
    }

    private ConfigObject narrowConfig(ConfigObject config, String sessionName) {
        if (config.containsKey('session') && sessionName == DEFAULT) {
            return config.session
        } else if (config.containsKey('sessions')) {
            return config.sessions[sessionName]
        }
        return config
    }

    Session connect(GriffonApplication app, ConfigObject config, String sessionName = DEFAULT) {
        if (SessionHolder.instance.isSessionConnected(sessionName)) {
            return SessionHolder.instance.getSession(sessionName)
        }

        config = narrowConfig(config, sessionName)
        app.event('CmisConnectStart', [config, sessionName])
        Session s = createSession(config)
        SessionHolder.instance.setSession(sessionName, s)
        app.event('CmisConnectEnd', [sessionName, s])
        s
    }

    void disconnect(GriffonApplication app, ConfigObject config, String sessionName = DEFAULT) {
        if (SessionHolder.instance.isSessionConnected(sessionName)) {
            config = narrowConfig(config, sessionName)
            Session s = SessionHolder.instance.getSession(sessionName)
            app.event('CmisDisconnectStart', [config, sessionName, s])
            app.event('CmisDisconnectEnd', [config, sessionName])
            SessionHolder.instance.disconnectSession(sessionName)
        }
    }

    Session createSession(ConfigObject config, String sessionName = DEFAULT) {
        sessionFactory.createSession(config)
    }
}
