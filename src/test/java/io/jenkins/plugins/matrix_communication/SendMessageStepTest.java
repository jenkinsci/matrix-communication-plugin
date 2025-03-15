package io.jenkins.plugins.matrix_communication;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cosium.matrix_communication_client.ClientEvent;
import com.cosium.matrix_communication_client.CreateRoomInput;
import com.cosium.matrix_communication_client.MatrixResources;
import com.cosium.matrix_communication_client.Message;
import com.cosium.matrix_communication_client.RoomResource;
import com.cosium.synapse_junit_extension.EnableSynapse;
import com.cosium.synapse_junit_extension.Synapse;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.Secret;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import jenkins.model.ParameterizedJobMixIn;
import org.assertj.core.groups.Tuple;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Réda Housni Alaoui
 */
@EnableSynapse
@WithJenkins
class SendMessageStepTest {

  private JenkinsRule j;

  @BeforeEach
  void beforeEach(JenkinsRule j) {
    this.j = j;
  }

  @Test
  @DisplayName("With UsernamePasswordCredentials")
  void test1(Synapse synapse) throws Exception {
    test(
        synapse,
        accessToken -> {
          try {
            SystemCredentialsProvider credentialsProvider = SystemCredentialsProvider.getInstance();
            IdCredentials credentials =
                new UsernamePasswordCredentialsImpl(
                    CredentialsScope.GLOBAL, UUID.randomUUID().toString(), null, null, accessToken);
            credentialsProvider.getCredentials().add(credentials);
            credentialsProvider.save();
            return credentials;
          } catch (Descriptor.FormException | IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  @DisplayName("With StringCredentials")
  void test2(Synapse synapse) throws Exception {
    test(
        synapse,
        accessToken -> {
          SystemCredentialsProvider credentialsProvider = SystemCredentialsProvider.getInstance();
          IdCredentials credentials =
              new StringCredentialsImpl(
                  CredentialsScope.GLOBAL,
                  UUID.randomUUID().toString(),
                  null,
                  Secret.fromString(accessToken));
          credentialsProvider.getCredentials().add(credentials);
          try {
            credentialsProvider.save();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          return credentials;
        });
  }

  private void test(Synapse synapse, Function<String, IdCredentials> credentialsFactory)
      throws Exception {
    MatrixResources matrixResources =
        MatrixResources.factory()
            .builder()
            .https(synapse.https())
            .hostname(synapse.hostname())
            .port(synapse.port())
            .usernamePassword(synapse.adminUsername(), synapse.adminPassword())
            .build();

    String accessTokenCredentialsId =
        credentialsFactory.apply(matrixResources.accessTokens().create()).getId();

    CreateRoomInput createRoomInput =
        CreateRoomInput.builder()
            .name(UUID.randomUUID().toString())
            .roomAliasName(UUID.randomUUID().toString())
            .topic(UUID.randomUUID().toString())
            .build();

    RoomResource room = matrixResources.rooms().create(createRoomInput);
    String roomId = room.id();

    WorkflowJob project = j.createProject(WorkflowJob.class);
    String script =
        "node {\n"
            + "matrixSendMessage https: "
            + synapse.https()
            + ", hostname: '"
            + synapse.hostname()
            + "', port: "
            + synapse.port()
            + ", accessTokenCredentialsId: '"
            + accessTokenCredentialsId
            + "', roomId: '"
            + roomId
            + "', body: 'Hello World !', formattedBody: '<b>Hello World !</b>'\n"
            + "}";
    project.setDefinition(new CpsFlowDefinition(script, true));

    triggerAndAssertSuccess(project);

    List<ClientEvent> roomEvents = room.fetchEventPage(null, null, null, null).chunk();
    assertThat(roomEvents)
        .filteredOn(clientEvent -> "m.room.message".equals(clientEvent.type()))
        .map(clientEvent -> clientEvent.content(Message.class))
        .extracting(Message::body, Message::formattedBody)
        .containsExactly(Tuple.tuple("Hello World !", "<b>Hello World !</b>"));
  }

  private void triggerAndAssertSuccess(Job job) throws Exception {
    final QueueTaskFuture future =
        new ParameterizedJobMixIn() {
          @Override
          protected Job asJob() {
            return job;
          }
        }.scheduleBuild2(0);
    j.assertBuildStatusSuccess(future);
  }
}
