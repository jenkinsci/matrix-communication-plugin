package io.jenkins.plugins.matrix_communication;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.PasswordCredentials;
import com.cosium.matrix_communication_client.MatrixResources;
import com.cosium.matrix_communication_client.Message;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
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
    protected Void run() {

      PasswordCredentials accessTokenCredentials =
          CredentialsMatchers.firstOrNull(
              CredentialsProvider.lookupCredentials(
                  PasswordCredentials.class, Jenkins.get(), null, Collections.emptyList()),
              CredentialsMatchers.withId(accessTokenCredentialsId));

      String accessToken =
          Optional.ofNullable(accessTokenCredentials)
              .map(PasswordCredentials::getPassword)
              .map(Secret::getPlainText)
              .orElse(null);

      MatrixResources.factory()
          .builder()
          .https(https)
          .hostname(hostname)
          .port(port)
          .accessToken(accessToken)
          .build()
          .rooms()
          .byId(roomId)
          .sendMessage(
              Message.builder()
                  .body(body)
                  .format(format)
                  .formattedBody(formattedBody)
                  .type(type)
                  .build());

      return null;
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
      return Set.of(Run.class, TaskListener.class);
    }

    @Override
    public String getFunctionName() {
      return "matrixSendMessage";
    }
  }
}
