/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j;

import org.apache.log4j.helpers.MessageFormatter;
import org.apache.log4j.spi.LoggerFactory;
import org.ops4j.pax.logging.PaxLogger;
import org.ops4j.pax.logging.PaxLoggingManager;
import org.ops4j.pax.logging.internal.Activator;
import org.ops4j.pax.logging.internal.FallbackLogFactory;

/**
  This is the central class in the log4j package. Most logging
  operations, except configuration, are done through this class.

  <p>pax-logging-api has to treat this class both as a factory and as logger itself
  - with all the configuration-related consequences.</p>

  @since log4j 1.2

  @author Ceki G&uuml;lc&uuml; */
public class Logger extends Category {

  /**
     The fully qualified name of the Logger class. See also the
     getFQCN method. */
  private static final String FQCN = Logger.class.getName();


  protected
  Logger(String name) {
    super(name);
  }

  protected
  Logger(String name, PaxLogger delegate) {
    super(name, delegate);
  }

  // public API of original org.apache.log4j.Logger follows.
  // no need to call isXXXEnabled, as the delegated logger (PaxLogger) does it anyway
  // non-public API is removed or changed to no-op if that's reasonable in
  // pax-logging case.

  /**
    Log a message object with the {@link Level#FINE FINE} level which
    is just an alias for the {@link Level#DEBUG DEBUG} level.

    <p>This method first checks if this category is <code>DEBUG</code>
    enabled by comparing the level of this category with the {@link
    Level#DEBUG DEBUG} level. If this category is
    <code>DEBUG</code> enabled, then it converts the message object
    (passed as parameter) to a string by invoking the appropriate
    {@link org.apache.log4j.or.ObjectRenderer}. It then proceeds to call all the
    registered appenders in this category and also higher in the
    hierarchy depending on the value of the additivity flag.

    <p><b>WARNING</b> Note that passing a {@link Throwable} to this
    method will print the name of the <code>Throwable</code> but no
    stack trace. To print a stack trace use the {@link #debug(Object,
    Throwable)} form instead.

    @param message the message object to log. */
  //public
  //void fine(Object message) {
  //  if(repository.isDisabled(Level.DEBUG_INT))
  //	return;
  //  if(Level.DEBUG.isGreaterOrEqual(this.getChainedLevel())) {
  //	forcedLog(FQCN, Level.DEBUG, message, null);
  //  }
  //}


  /**
   Log a message object with the <code>FINE</code> level including
   the stack trace of the {@link Throwable} <code>t</code> passed as
   parameter.

   <p>See {@link #fine(Object)} form for more detailed information.

   @param message the message object to log.
   @param t the exception to log, including its stack trace.  */
  //public
  //void fine(Object message, Throwable t) {
  //  if(repository.isDisabled(Level.DEBUG_INT))
  //	return;
  //  if(Level.DEBUG.isGreaterOrEqual(this.getChainedLevel()))
  //	forcedLog(FQCN, Level.FINE, message, t);
  //}

  /**
   * Retrieve a logger named according to the value of the
   * <code>name</code> parameter. If the named logger already exists,
   * then the existing instance will be returned. Otherwise, a new
   * instance is created.  
   *
   * <p>By default, loggers do not have a set level but inherit it
   * from their neareast ancestor with a set level. This is one of the
   * central features of log4j.
   *
   * <p>In pax-logging, loggers are obtained from current or fallback
   * {@link PaxLoggingManager}</p>
   *
   * @param name The name of the logger to retrieve.  
  */
  static
  public
  Logger getLogger(String name) {
    PaxLogger logger;
    if (m_paxLogging == null) {
      logger = FallbackLogFactory.createFallbackLog(null, name);
    } else {
      logger = m_paxLogging.getLogger(name, LOG4J_FQCN);
    }
    Logger log4jlogger = new Logger(name, logger);
    if (m_paxLogging == null) {
      synchronized (Activator.m_loggers) {
        Activator.m_loggers.put(name, log4jlogger);
      }
    }
    return log4jlogger;
  }

  /**
   * Shorthand for <code>getLogger(clazz.getName())</code>.
   *
   * @param clazz The name of <code>clazz</code> will be used as the
   * name of the logger to retrieve.  See {@link #getLogger(String)}
   * for more detailed information.
   */
  static
  public
  Logger getLogger(Class clazz) {
    return getLogger(clazz.getName());
  }


  /**
   * Return the root logger for the current logger repository.
   * <p>
   * The {@link #getName Logger.getName()} method for the root logger always returns
   * string value: "root". However, calling
   * <code>Logger.getLogger("root")</code> does not retrieve the root
   * logger but a logger just under root named "root".
   * <p>
   * In other words, calling this method is the only way to retrieve the 
   * root logger.
   */
  public
  static
  Logger getRootLogger() {
    return getLogger("");
  }

  /**
     Like {@link #getLogger(String)} except that the type of logger
     instantiated depends on the type returned by the {@link
     LoggerFactory#makeNewLoggerInstance} method of the
     <code>factory</code> parameter.

     <p>This method is intended to be used by sub-classes.

     @param name The name of the logger to retrieve.

     @param factory <b>Ignored!</b>

     @since 0.8.5 */
  public
  static
  Logger getLogger(String name, LoggerFactory factory) {
    return getLogger(name);
  }

    /**
     * Log a message object with the {@link org.apache.log4j.Level#TRACE TRACE} level.
     *
     * @param message the message object to log.
     * @see #debug(Object) for an explanation of the logic applied.
     * @since 1.2.12
     */
    public void trace(Object message) {
        m_delegate.trace(message == null ? null : message.toString(), null);
    }

    /**
     * Log a message object with the <code>TRACE</code> level including the
     * stack trace of the {@link Throwable}<code>t</code> passed as parameter.
     *
     * <p>
     * See {@link #debug(Object)} form for more detailed information.
     * </p>
     *
     * @param message the message object to log.
     * @param t the exception to log, including its stack trace.
     * @since 1.2.12
     */
    public void trace(Object message, Throwable t) {
        m_delegate.trace(message == null ? null : message.toString(), t);
    }

  /**
   * Log a message with the <code>TRACE</code> level with message formatting
   * done according to the value of <code>messagePattern</code> and
   * <code>arg</code> parameters.
   * <p>
   * This form avoids superflous parameter construction. Whenever possible,
   * you should use this form instead of constructing the message parameter
   * using string concatenation.
   *
   * @param messagePattern The message pattern which will be parsed and formatted
   * @param arg The argument to replace the formatting element, i,e,
   * the '{}' pair within <code>messagePattern</code>.
   * @since 1.3
   */
  public void trace(Object messagePattern, Object arg) {
    if (m_delegate.isTraceEnabled()) {
      String msgStr = (String) messagePattern;
      msgStr = MessageFormatter.format(msgStr, arg);
      m_delegate.trace(msgStr, null);
    }
  }

  /**
   * Log a message with the <code>TRACE</code> level with message formatting
   * done according to the messagePattern and the arguments arg1 and arg2.
   * <p>
   * This form avoids superflous parameter construction. Whenever possible,
   * you should use this form instead of constructing the message parameter
   * using string concatenation.
   *
   * @param messagePattern The message pattern which will be parsed and formatted
   * @param arg1 The first argument to replace the first formatting element
   * @param arg2 The second argument to replace the second formatting element
   * @since 1.3
   */
  public void trace(String messagePattern, Object arg1, Object arg2) {
    if (m_delegate.isTraceEnabled()) {
      String msgStr = MessageFormatter.format(messagePattern, arg1, arg2);
      m_delegate.trace(msgStr, null);
    }
  }

  /**
   * Log a message with the <code>FATAL</code> level with message formatting
   * done according to the messagePattern and the arguments arg1 and arg2.
   * <p>
   * This form avoids superflous parameter construction. Whenever possible,
   * you should use this form instead of constructing the message parameter
   * using string concatenation.
   *
   * @param messagePattern The message pattern which will be parsed and formatted
   * @param arg1 The first argument to replace the first formatting element
   * @param arg2 The second argument to replace the second formatting element
   * @since 1.3
   */
  public void fatal(String messagePattern, Object arg1, Object arg2) {
    if (m_delegate.isFatalEnabled()) {
      String msgStr = MessageFormatter.format(messagePattern, arg1, arg2);
      m_delegate.fatal(msgStr, null);
    }
  }

  // Here are added overriden methods from the Category class (all methods that can be potentially used for logging).
  // It is needed, because Category class is included in the stack trace in which log4j backend is looking for the LocationInfo instead of Logger class.
  // These methods just call their super methods in the Category class

  public void debug(final Object message) {
    super.debug(message);
  }

  public void debug(final Object message, final Throwable t) {
    super.debug(message, t);
  }

  public void error(final Object message) {
    super.error(message);
  }

  public void error(final Object message, final Throwable t) {
    super.error(message, t);
  }

  public void fatal(final Object message) {
    super.fatal(message);
  }

  public void fatal(final Object message, final Throwable t) {
    super.fatal(message, t);
  }

  public void info(final Object message) {
    super.info(message);
  }

  public void info(final Object message, final Throwable t) {
    super.info(message, t);
  }

  public void warn(final Object message) {
    super.warn(message);
  }

  public void warn(final Object message, final Throwable t) {
    super.warn(message, t);
  }

}