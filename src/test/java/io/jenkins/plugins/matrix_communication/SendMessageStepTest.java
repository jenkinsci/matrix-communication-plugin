package io.jenkins.plugins.matrix_communication;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cosium.matrix_communication_client.ClientEvent;
import com.cosium.matrix_communication_client.CreateRoomInput;
import com.cosium.matrix_communication_client.MatrixResources;
import com.cosium.matrix_communication_client.Message;
import com.cosium.matrix_communication_client.RoomResource;
import com.cosium.synapse_junit_extension.EnableSynapse;
import com.cosium.synapse_junit_extension.Synapse;
import hudson.model.Job;
import hudson.model.queue.QueueTaskFuture;
import java.util.List;
import java.util.UUID;
import jenkins.model.ParameterizedJobMixIn;
import org.assertj.core.groups.Tuple;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
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
  void test(Synapse synapse) throws Exception {
    MatrixResources matrixResources =
        MatrixResources.factory()
            .builder()
            .https(synapse.https())
            .hostname(synapse.hostname())
            .port(synapse.port())
            .usernamePassword(synapse.adminUsername(), synapse.adminPassword())
            .build();

    String accessTokenCredentialsId = UUID.randomUUID().toString();

    SystemCredentialsProvider credentialsProvider = SystemCredentialsProvider.getInstance();
    Credentials credentials =
        new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL,
            accessTokenCredentialsId,
            null,
            null,
            matrixResources.accessTokens().create());
    credentialsProvider.getCredentials().add(credentials);
    credentialsProvider.save();

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
