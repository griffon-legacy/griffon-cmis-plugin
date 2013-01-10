/*
 * Copyright 2012-2013 the original author or authors.
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

package griffon.plugins.cmis;

import griffon.util.CallableWithArgs;
import groovy.lang.Closure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.chemistry.opencmis.client.api.Session;

/**
 * @author Andres Almiray
 */
public abstract class AbstractCmisProvider implements CmisProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCmisProvider.class);
    private static final String DEFAULT = "default";

    public <R> R withCmis(Closure<R> closure) {
        return withCmis(DEFAULT, closure);
    }

    public <R> R withCmis(String sessionName, Closure<R> closure) {
        if (closure != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing statement on session '"+ sessionName + "'");
            }
            return closure.call(sessionName, getSession(sessionName));
        }
        return null;
    }

    public <R> R withCmis(CallableWithArgs<R> callable) {
        return withCmis(DEFAULT, callable);
    }

    public <R> R withCmis(String sessionName, CallableWithArgs<R> callable) {
        if (callable != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing statement on session '"+ sessionName + "'");
            }
            callable.setArgs(new Object[]{ sessionName, getSession(sessionName)} );
            return callable.call();
        }
        return null;
    }

    protected abstract Session getSession(String sessionName);
}