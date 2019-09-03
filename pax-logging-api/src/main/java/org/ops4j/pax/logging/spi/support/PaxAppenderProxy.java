/*
 * Copyright 2006 Niclas Hedhman.
 *
 * Licensed  under the  Apache License,  Version 2.0  (the "License");
 * you may not use  this file  except in  compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.logging.spi.support;

import org.ops4j.pax.logging.PaxLoggingConstants;
import org.ops4j.pax.logging.spi.PaxAppender;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A {@link ServiceTracker} used by bridges specific to given backend.
 */
public class PaxAppenderProxy extends ServiceTracker<PaxAppender, PaxAppender> implements PaxAppender {

    private volatile int count = -1;
    private PaxAppender[] appenders = null;

    public PaxAppenderProxy(BundleContext bundleContext, String name) {
        super(bundleContext, createFilter(bundleContext, name), null);
    }

    /**
     * Filter in the form of {@code (&(objectClass=org.ops4j.pax.logging.spi.PaxAppender)(org.ops4j.pax.logging.appender.name=NAME))},
     * where {@code NAME} comes from {@code osgi:} prefixed
     * references from logging configuration.
     * @param bundleContext
     * @param name
     * @return
     */
    public static Filter createFilter(BundleContext bundleContext, String name) {
        try {
            return bundleContext.createFilter(
                    "(&(" + Constants.OBJECTCLASS + "=" + PaxAppender.class.getName() + ")" +
                            "(" + PaxLoggingConstants.SERVICE_PROPERTY_APPENDER_NAME_PROPERTY + "=" + name + "))");
        } catch (InvalidSyntaxException e) {
            throw new IllegalStateException("unable to create appender tracker", e);
        }
    }

    @Override
    public void doAppend(PaxLoggingEvent event) {
        if (count != getTrackingCount()) {
            count = getTrackingCount();
            appenders = getServices(new PaxAppender[0]);
        }
        if (appenders != null && appenders.length > 0) {
            // Bug in Karaf, as it expects the source to be available
            event.getLocationInformation();
            for (PaxAppender appender : appenders) {
                appender.doAppend(event);
            }
        }
    }

}
