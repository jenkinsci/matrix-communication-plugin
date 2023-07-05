package io.jenkins.plugins.matrix_communication;

import static java.util.Objects.requireNonNull;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.PasswordCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cosium.matrix_communication_client.MatrixResources;
import com.cosium.matrix_communication_client.Message;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.util.Secret;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.security.auth.login.CredentialNotFoundException;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @author Réda Housni Alaoui
 */
public class SendMessageStep extends Step {

  private boolean https = true;
  private String hostname;
  private Integer port;
  private String accessTokenCredentialsId;
  private String roomId;
  private String body;
  private String format = "org.matrix.custom.html";
  private String formattedBody;
  private String type = "m.text";

  @DataBoundConstructor
  public SendMessageStep() {
    // Only needed to mark the constructor with @DataBoundConstructor
  }

  public boolean isHttps() {
    return https;
  }

  @DataBoundSetter
  public void setHttps(boolean https) {
    this.https = https;
  }

  public String getHostname() {
    return hostname;
  }

  @DataBoundSetter
  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public Integer getPort() {
    return port;
  }

  @DataBoundSetter
  public void setPort(Integer port) {
    this.port = port;
  }

  public String getAccessTokenCredentialsId() {
    return accessTokenCredentialsId;
  }

  @DataBoundSetter
  public void setAccessTokenCredentialsId(String accessTokenCredentialsId) {
    this.accessTokenCredentialsId = accessTokenCredentialsId;
  }

  public String getRoomId() {
    return roomId;
  }

  @DataBoundSetter
  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  public String getBody() {
    return body;
  }

  @DataBoundSetter
  public void setBody(String body) {
    this.body = body;
  }

  public String getFormat() {
    return format;
  }

  @DataBoundSetter
  public void setFormat(String format) {
    this.format = format;
  }

  public String getFormattedBody() {
    return formattedBody;
  }

  @DataBoundSetter
  public void setFormattedBody(String formattedBody) {
    this.formattedBody = formattedBody;
  }

  public String getType() {
    return type;
  }

  @DataBoundSetter
  public void setType(String type) {
    this.type = type;
  }

  @Override
  public StepExecution start(StepContext context) {
    Execution execution = new Execution(context);
    execution.https = https;
    execution.hostname = hostname;
    execution.port = port;
    execution.accessTokenCredentialsId = accessTokenCredentialsId;
    execution.roomId = roomId;
    execution.body = body;
    execution.format = format;
    execution.formattedBody = formattedBody;
    execution.type = type;
    return execution;
  }

  private static class Execution extends SynchronousNonBlockingStepExecution<Void> {

    private static final Logger LOGGER = Logger.getLogger(Execution.class.getName());

    private boolean https;
    private String hostname;
    private Integer port;
    private String accessTokenCredentialsId;
    private String roomId;
    private String body;
    private String format;
    private String formattedBody;
    private String type;

    protected Execution(@Nonnull StepContext context) {
      super(context);
    }

    @Override
    protected Void run() throws CredentialNotFoundException, IOException, InterruptedException {
      String accessToken = findAccessTokenSecret().map(Secret::getPlainText).orElse(null);

      MatrixResources matrixResources =
          MatrixResources.factory()
              .builder()
              .https(https)
              .hostname(hostname)
              .port(port)
              .accessToken(accessToken)
              .build();

      TaskListener taskListener = getContext().get(TaskListener.class);
      requireNonNull(taskListener, String.format("Could not retrieve %s", TaskListener.class));

      String withAccessToken;
      if (accessToken != null) {
        withAccessToken = "with access token";
      } else {
        withAccessToken = "without access token";
      }

      taskListener
          .getLogger()
          .printf(
              "Sending Matrix message {type: '%s', format: '%s', body: '%s', formattedBody: '%s'} to {https: %s, hostname: '%s', port: %s} %s%n",
              type, format, body, formattedBody, https, hostname, port, withAccessToken);

      matrixResources
          .rooms()
          .byId(roomId)
          .sendMessage(
              Message.builder()
                  .type(type)
                  .format(format)
                  .body(body)
                  .formattedBody(formattedBody)
                  .build());

      taskListener.getLogger().println("Matrix message sent");

      return null;
    }

    private Optional<Secret> findAccessTokenSecret() throws CredentialNotFoundException {
      if (accessTokenCredentialsId == null) {
        LOGGER.log(Level.FINE, "accessTokenCredentialsId is null");
        return Optional.empty();
      }

      LOGGER.log(
          Level.FINE,
          () -> String.format("Looking for credentials with id '%s'", accessTokenCredentialsId));

      StandardCredentials accessTokenCredentials =
          CredentialsMatchers.firstOrNull(
              CredentialsProvider.lookupCredentials(
                  StandardCredentials.class, Jenkins.get(), null, Collections.emptyList()),
              CredentialsMatchers.withId(accessTokenCredentialsId));

      if (accessTokenCredentials == null) {
        throw new CredentialNotFoundException(
            String.format("No credentials found for id '%s'", accessTokenCredentialsId));
      }

      if (accessTokenCredentials instanceof StringCredentials) {
        LOGGER.log(Level.FINE, () -> String.format("Found '%s'", accessTokenCredentials));
        return Optional.of(accessTokenCredentials)
            .map(StringCredentials.class::cast)
            .map(StringCredentials::getSecret);
      }

      if (accessTokenCredentials instanceof PasswordCredentials) {
        LOGGER.log(Level.FINE, () -> String.format("Found '%s'", accessTokenCredentials));
        return Optional.of(accessTokenCredentials)
            .map(PasswordCredentials.class::cast)
            .map(PasswordCredentials::getPassword);
      }

      throw new CredentialNotFoundException(
          String.format("Cannot handle %s", accessTokenCredentials));
    }
  }

  @Extension
  public static final class DescriptorImpl extends StepDescriptor {

    @Override
    public String getDisplayName() {
      return "Send message to a Matrix room";
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return Set.of(TaskListener.class);
    }

    @Override
    public String getFunctionName() {
      return "matrixSendMessage";
    }
  }
}
