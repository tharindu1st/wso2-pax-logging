/*
 * Copyright 2005-2009 Niclas Hedhman.
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
package org.ops4j.pax.logging.log4j2.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.async.AsyncLoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory;
import org.apache.logging.log4j.core.pattern.DatePatternConverter;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.knopflerfish.service.log.LogService;
import org.ops4j.pax.logging.EventAdminPoster;
import org.ops4j.pax.logging.PaxContext;
import org.ops4j.pax.logging.PaxLogger;
import org.ops4j.pax.logging.PaxLoggingConstants;
import org.ops4j.pax.logging.PaxLoggingService;
import org.ops4j.pax.logging.log4j2.internal.bridges.PaxOsgiAppender;
import org.ops4j.pax.logging.spi.support.BackendSupport;
import org.ops4j.pax.logging.spi.support.ConfigurationNotifier;
import org.ops4j.pax.logging.spi.support.LogEntryImpl;
import org.ops4j.pax.logging.spi.support.LogReaderServiceImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogEntry;

public class PaxLoggingServiceImpl
        implements PaxLoggingService, LogService, ManagedService, ServiceFactory {

    private static final String LOGGER_CONTEXT_NAME = "pax-logging";

                    // see org.apache.logging.log4j.core.config.properties.PropertiesConfigurationBuilder.createLogger()
                    private static final String LOG4J2_ROOT_LOGGER_LEVEL_PROPERTY = "log4j2.rootLogger.level";
                    private static final String LOG4J2_LOGGER_PROPERTY_PREFIX = "log4j2.logger.";

    static {
//        PluginManager.addPackage("org.apache.logging.log4j.core");
        // We don't have to add "org.apache.logging.log4j.core", because this package will be handled
        // using default cache file "/META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat"
        // taken unchanged from "org.apache.logging.log4j:log4j-core".
        PluginManager.addPackage(PaxOsgiAppender.class.getPackage().getName());
    }

    private final BundleContext m_bundleContext;

    // LogReaderService registration as defined by org.osgi.service.log package
    private final LogReaderServiceImpl m_logReader;

    // pax-logging-log4j2 specific PaxContext for all MDC access
    private final PaxContext m_paxContext;

    // optional bridging into Event Admin service
    private final EventAdminPoster m_eventAdmin;

    // optional notification mechanism for configuration events
    private final ConfigurationNotifier m_configNotifier;

    // Log level (actually a threashold) for this entire service.
    private int m_logLevel = org.osgi.service.log.LogService.LOG_DEBUG;

    // the main org.apache.logging.log4j.core.LoggerContext
    private LoggerContext m_log4jContext;

    // there's no need to run configureDefaults() more than once. That was happening in constructor
    // and millisecond later during registration of ManagedService, upon receiving empty org.ops4j.pax.logging
    // configuration
    private AtomicBoolean emptyConfiguration = new AtomicBoolean(false);

    private volatile boolean closed;

    private final String fqcn = getClass().getName();

    private boolean m_async;

    public PaxLoggingServiceImpl(BundleContext bundleContext, LogReaderServiceImpl logReader, EventAdminPoster eventAdmin, ConfigurationNotifier configNotifier) {
        if (bundleContext == null)
            throw new IllegalArgumentException("bundleContext cannot be null");
        m_bundleContext = bundleContext;
        if (logReader == null)
            throw new IllegalArgumentException("logReader cannot be null");
        m_logReader = logReader;
        if (eventAdmin == null)
            throw new IllegalArgumentException("eventAdmin cannot be null");
        m_eventAdmin = eventAdmin;
        m_configNotifier = configNotifier;

        m_paxContext = new PaxContext();

        configureDefaults();
    }

    // org.ops4j.pax.logging.PaxLoggingService

    /**
     * Shut down the Pax Logging service.  This will reset the logging configuration entirely, so it should only be
     * used just before disposing of the service instance.
     */
    public synchronized void shutdown() {
        m_log4jContext.stop();
        closed = true;
    }

    // org.knopflerfish.service.log.LogService

    @Override
    public PaxLogger getLogger(Bundle bundle, String category, String fqcn) {
        Logger log4j2Logger;
        if (category == null) {
            log4j2Logger = m_log4jContext.getRootLogger();
        } else {
            log4j2Logger = m_log4jContext.getLogger(category);
        }
        return new PaxLoggerImpl(bundle, log4j2Logger, fqcn, this);
    }

    // org.osgi.service.log.LogService
    // these methods are actually never called directly (except in tests), because the actual published
    // methods come from service factory produced object

    @Override
    public int getLogLevel() {
        return m_logLevel;
    }

    @Override
    public void log(int level, String message) {
        logImpl(null, level, message, null, fqcn);
    }

    @Override
    public void log(int level, String message, Throwable exception) {
        logImpl(null, level, message, exception, fqcn);
    }

    @Override
    public void log(ServiceReference sr, int level, String message) {
        logImpl(sr == null ? null : sr.getBundle(), level, message, null, fqcn);
    }

    @Override
    public void log(ServiceReference sr, int level, String message, Throwable exception) {
        logImpl(sr == null ? null : sr.getBundle(), level, message, exception, fqcn);
    }

    @Override
    public PaxContext getPaxContext() {
        return m_paxContext;
    }

    // org.osgi.service.cm.ManagedService

    @Override
    public synchronized void updated(Dictionary<String, ?> configuration) throws ConfigurationException {
        if (closed) {
            return;
        }
        if (configuration == null) {
            configureDefaults();
            return;
        }

        Object configfile = configuration.get(PaxLoggingConstants.PID_CFG_LOG4J2_CONFIG_FILE);

        // async property choses org.apache.logging.log4j.core.LoggerContext, so it has to be
        // extracted early
        Object asyncProperty = configuration.get(PaxLoggingConstants.PID_CFG_LOG4J2_ASYNC);
        boolean async = asyncProperty != null && Boolean.parseBoolean(asyncProperty.toString());
        if (async) {
            try {
                getClass().getClassLoader().loadClass("com.lmax.disruptor.EventFactory");
            } catch (Exception e) {
                StatusLogger.getLogger().warn("Asynchronous loggers defined, but the disruptor library is not available.  Reverting to synchronous loggers.", e);
                async = false;
            }
        }

        if (configfile instanceof String) {
            // configure using external (XML or properties) file
            configureLog4J2(async, (String) configfile, null);
        } else {
            // configure using inline (in org.ops4j.pax.logging PID) configuration
            configureLog4J2(async, null, configuration);
        }

        // pick up pax-specific configuration of LogReader
        configurePax(configuration);
    }

    /**
     * Actual logging work is done here
     * @param bundle
     * @param level
     * @param message
     * @param exception
     * @param fqcn
     */
    private void logImpl(Bundle bundle, int level, String message, Throwable exception, String fqcn) {
        String category = BackendSupport.category(bundle);

        PaxLogger logger = getLogger(bundle, category, fqcn);
        if (level < LOG_ERROR) {
            logger.fatal(message, exception);
        } else {
            switch (level) {
                case LOG_ERROR:
                    logger.error(message, exception);
                    break;
                case LOG_WARNING:
                    logger.warn(message, exception);
                    break;
                case LOG_INFO:
                    logger.inform(message, exception);
                    break;
                case LOG_DEBUG:
                    logger.debug(message, exception);
                    break;
                default:
                    logger.trace(message, exception);
            }
        }
    }

    void handleEvents(Bundle bundle, ServiceReference sr, int level, String message, Throwable exception) {
        LogEntry entry = new LogEntryImpl(bundle, sr, level, message, exception);
        m_logReader.fireEvent(entry);

        // This should only be null for TestCases.
        if (m_eventAdmin != null) {
            m_eventAdmin.postEvent(bundle, level, entry, message, exception, sr, getPaxContext().getContext());
        }
    }

    /**
     * Default configuration, when Configuration Admin is not (yet) available.
     */
    private void configureDefaults() {
        String levelName = BackendSupport.defaultLogLevel(m_bundleContext);
        java.util.logging.Level julLevel = BackendSupport.toJULLevel(levelName);

        m_logLevel = BackendSupport.convertLogServiceLevel(levelName);

        final java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        rootLogger.setLevel(julLevel);

        configureLog4J2(false, null, null);
    }

    /**
     * Configure using external XML/properties file name or properties. When neither are specified, default
     * configuration is used.
     * @param async
     * @param configFileName
     * @param configuration
     */
    private void configureLog4J2(boolean async, String configFileName, Dictionary<String, ?> configuration) {
        Throwable problem = null;

        File file = null;
        if (configFileName != null) {
            file = new File(configFileName);
        }
        if (file != null && !file.isFile()) {
            StatusLogger.getLogger().warn("Configuration file '" + file + "' is not available. Default configuration will be used.");
            file = null;
        }

        if (file == null && configuration == null && !emptyConfiguration.compareAndSet(false, true)) {
            // no need to reconfigure default configuration
            return;
        }

        // check if there are empty properties to shortcut this path
        Properties props = null;
        if (configuration != null) {
            // properties passed directly
            props = new Properties();
            for (Enumeration<String> keys = configuration.keys(); keys.hasMoreElements(); ) {
                String key = keys.nextElement();
                props.setProperty(key, configuration.get(key).toString());
            }
            props = PropertiesUtil.extractSubset(props, "log4j2");

            if (props.size() == 0 && emptyConfiguration.get()) {
                // no need to even stop current context
                return;
            }
        }

        if (m_log4jContext != null) {
            // Log4J1: org.apache.log4j.LogManager.resetConfiguration()
            // Logback: ch.qos.logback.classic.LoggerContext.reset()
            // Log4J2: org.apache.logging.log4j.core.AbstractLifeCycle.stop()
            m_log4jContext.stop();
        }

        if (m_log4jContext == null || async != m_async) {
            m_log4jContext = async ? new AsyncLoggerContext(LOGGER_CONTEXT_NAME) : new LoggerContext(LOGGER_CONTEXT_NAME);
            m_async = async;
        }

        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            if (props != null) {
                if (props.size() == 0) {
                    configureDefaults();
                    return;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                props.store(baos, null);
                ConfigurationSource src = new ConfigurationSource(new ByteArrayInputStream(baos.toByteArray()));
                Configuration config = new PropertiesConfigurationFactory().getConfiguration(m_log4jContext, src);

                m_log4jContext.start(config);

                StatusLogger.getLogger().info("Log4J2 configured using configuration from " + PaxLoggingConstants.LOGGING_CONFIGURATION_PID + " PID.");
            } else if (file != null) {
                // configuration using externally specified file. This is the way to make Karaf's
                // etc/org.ops4j.pax.logging.cfg much simpler without this cumbersome properties
                // file format.
                // file may have been specified as system/context property "org.ops4j.pax.logging.property.file"
                // or as single, "org.ops4j.pax.logging.log4j2.config.file" property in etc/org.ops4j.pax.logging.cfg
                emptyConfiguration.set(false);

                ConfigurationFactory factory = ConfigurationFactory.getInstance();
                // ".json", ".jsn": org.apache.logging.log4j.core.config.json.JsonConfigurationFactory
                // ".properties": org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory
                // ".xml", "*": org.apache.logging.log4j.core.config.xml.XmlConfigurationFactory
                // ".yml", ".yaml": org.apache.logging.log4j.core.config.yaml.YamlConfigurationFactory
                Configuration config = factory.getConfiguration(m_log4jContext, LOGGER_CONTEXT_NAME, file.toURI());

                m_log4jContext.start(config);

                StatusLogger.getLogger().info("Log4J2 configured using file '" + file + "'.");
            } else {
                // default configuration - Log4J2 specific.
                // even if LoggerContext by default has DefaultConfiguration set, it's necessary to pass
                // new DefaultConfiguration during start. Otherwise
                // org.apache.logging.log4j.core.LoggerContext.reconfigure() will be called with empty
                // org.apache.logging.log4j.core.config.properties.PropertiesConfiguration
                m_log4jContext.start(new DefaultConfiguration());
                m_log4jContext.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.INFO);

                StatusLogger.getLogger().info("Log4J2 configured using default configuration.");
            }
        } catch (Throwable e) {
            StatusLogger.getLogger().error("Log4J2 configuration problem: " + e.getMessage(), e);
            problem = e;
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }

        m_log4jContext.updateLoggers();

        // TODO: should iterate existing loggers instead of the configuration
//        setLevelToJavaLogging();

        // do it outside of the lock (if there will be lock)
        if (problem == null) {
            m_configNotifier.configurationDone();
        } else {
            m_configNotifier.configurationError(problem);
        }
    }

    /**
     * Configure Java Util Logging according to the provided configuration.
     * Convert the log4j configuration to JUL config.
     *
     * It's necessary to do that, because with pax logging, JUL loggers are not replaced.
     * So we need to configure JUL loggers in order that log messages goes correctly to log Handlers.
     *
     * @param configuration Properties coming from the configuration.
     */
    private void setLevelToJavaLogging(final Dictionary<String, ?> configuration) {
        for (Enumeration enum_ = java.util.logging.LogManager.getLogManager().getLoggerNames(); enum_.hasMoreElements(); ) {
            String name = (String) enum_.nextElement();
            java.util.logging.Logger.getLogger(name).setLevel(null);
        }

        // TODO: should iterate existing loggers instead of the configuration
        for (Enumeration<String> keys = configuration.keys(); keys.hasMoreElements(); ) {
            String name = keys.nextElement();
            if (name.equals(LOG4J2_ROOT_LOGGER_LEVEL_PROPERTY)) {
                String value = (String) configuration.get(LOG4J2_ROOT_LOGGER_LEVEL_PROPERTY);
                java.util.logging.Level julLevel = BackendSupport.toJULLevel(value);
                java.util.logging.Logger.getGlobal().setLevel(julLevel);
                java.util.logging.Logger.getLogger("").setLevel(julLevel);
                // "global" comes from java.util.logging.Logger.GLOBAL_LOGGER_NAME, but that constant wasn't added until Java 1.6
                java.util.logging.Logger.getLogger("global").setLevel(julLevel);
            }

            if (name.startsWith(LOG4J2_LOGGER_PROPERTY_PREFIX)
                    && name.endsWith(".name")) {
                String value = (String) configuration.get(name.replaceFirst("\\.name$", ".level"));
                java.util.logging.Level julLevel = BackendSupport.toJULLevel(value);
                String packageName = (String) configuration.get(name);
                java.util.logging.Logger.getLogger(packageName).setLevel(julLevel);
            }
        }
    }

    private void configurePax(Dictionary<String, ?> config) {
        Object size = config.get(PaxLoggingConstants.PID_CFG_LOG_READER_SIZE);
        if (size == null) {
            size = config.get(PaxLoggingConstants.PID_CFG_LOG_READER_SIZE_LEGACY);
        }
        if (null != size) {
            try {
                m_logReader.setMaxEntries(Integer.parseInt((String) size));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // org.osgi.framework.ServiceFactory

    /**
     * <p>Use local class to delegate calls to underlying instance while keeping bundle reference.</p>
     * <p>We don't need anything special from bundle-scoped service ({@link ServiceFactory}) except the
     * reference to client bundle.</p>
     */
    @Override
    public Object getService(final Bundle bundle, ServiceRegistration registration) {
        class ManagedPaxLoggingService
                implements PaxLoggingService, LogService, ManagedService {

            private final String FQCN = ManagedPaxLoggingService.class.getName();

            @Override
            public void log(int level, String message) {
                PaxLoggingServiceImpl.this.logImpl(bundle, level, message, null, FQCN);
            }

            @Override
            public void log(int level, String message, Throwable exception) {
                PaxLoggingServiceImpl.this.logImpl(bundle, level, message, exception, FQCN);
            }

            @Override
            public void log(ServiceReference sr, int level, String message) {
                Bundle b = bundle == null && sr != null ? sr.getBundle() : bundle;
                PaxLoggingServiceImpl.this.logImpl(b, level, message, null, FQCN);
            }

            @Override
            public void log(ServiceReference sr, int level, String message, Throwable exception) {
                Bundle b = bundle == null && sr != null ? sr.getBundle() : bundle;
                PaxLoggingServiceImpl.this.logImpl(b, level, message, exception, FQCN);
            }

            @Override
            public int getLogLevel() {
                return PaxLoggingServiceImpl.this.getLogLevel();
            }

            @Override
            public PaxLogger getLogger(Bundle myBundle, String category, String fqcn) {
                return PaxLoggingServiceImpl.this.getLogger(myBundle, category, fqcn);
            }

            @Override
            public void updated(Dictionary<String, ?> configuration)
                    throws ConfigurationException {
                PaxLoggingServiceImpl.this.updated(configuration);
            }

            @Override
            public PaxContext getPaxContext() {
                return PaxLoggingServiceImpl.this.getPaxContext();
            }
        }

        return new ManagedPaxLoggingService();
    }

    @Override
    public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
        // nothing to do...
    }

}
