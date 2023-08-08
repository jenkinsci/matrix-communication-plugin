package io.jenkins.plugins.matrix_communication;

import ch.qos.logback.classic.BasicConfigurator;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Context;
import java.util.Optional;

/**
 * @author Réda Housni Alaoui
 */
public class LogbackConfigurator extends BasicConfigurator {

  @Override
  public ExecutionStatus configure(Context context) {
    super.configure(context);
    Level rootLevel =
        Optional.ofNullable(System.getenv("MATRIX_COMMUNICATION_PLUGIN_TEST_LOG_ROOT_LEVEL"))
            .map(Level::valueOf)
            .orElse(Level.OFF);
    LoggerContext loggerContext = (LoggerContext) context;
    loggerContext.getLogger("ROOT").setLevel(rootLevel);
    return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
  }
}
