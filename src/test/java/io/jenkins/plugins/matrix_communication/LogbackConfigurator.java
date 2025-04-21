package io.jenkins.plugins.matrix_communication;

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

import java.util.Optional;

/**
 * Configures Logback for the Matrix Communication plugin by setting the root log level
 * based on the environment variable {@code MATRIX_COMMUNICATION_PLUGIN_TEST_LOG_ROOT_LEVEL}.
 *
 * If the environment variable is not defined or contains an invalid value, logging is turned off.
 */
public class LogbackConfigurator extends BasicConfigurator {

    private static final String LOG_LEVEL_ENV_VAR = "MATRIX_COMMUNICATION_PLUGIN_TEST_LOG_ROOT_LEVEL";

    /**
     * Applies custom Logback configuration by setting the root logger's level.
     *
     * @param loggerContext the context to configure
     * @return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY to prevent further configurators
     */
    @Override
    public ExecutionStatus configure(LoggerContext loggerContext) {
        super.configure(loggerContext);

        Level rootLevel = resolveLogLevel(System.getenv(LOG_LEVEL_ENV_VAR));
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(rootLevel);

        return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
    }

    /**
     * Resolves a log level string to a Logback {@link Level}. Defaults to {@code Level.OFF}
     * if the string is null, empty, or invalid.
     *
     * @param levelStr the string representation of the log level
     * @return the corresponding {@link Level} or {@code Level.OFF} if invalid
     */
    private Level resolveLogLevel(String levelStr) {
        try {
            return Optional.ofNullable(levelStr)
                           .map(String::trim)
                           .filter(s -> !s.isEmpty())
                           .map(Level::valueOf)
                           .orElse(Level.OFF);
        } catch (IllegalArgumentException e) {
            // Invalid level string provided
            return Level.OFF;
        }
    }
}
