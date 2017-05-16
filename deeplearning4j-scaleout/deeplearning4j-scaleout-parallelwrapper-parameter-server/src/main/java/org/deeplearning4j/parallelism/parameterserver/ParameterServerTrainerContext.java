package org.deeplearning4j.parallelism.parameterserver;

import io.aeron.driver.MediaDriver;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.parallelism.MagicQueue;
import org.deeplearning4j.parallelism.ParallelWrapper;
import org.deeplearning4j.parallelism.factory.TrainerContext;
import org.deeplearning4j.parallelism.trainer.Trainer;
import org.nd4j.parameterserver.client.ParameterServerClient;
import org.nd4j.parameterserver.node.ParameterServerNode;

/**
 * Used for creating and running {@link ParallelWrapper}
 * with {@link ParameterServerTrainer} workers.
 *
 * @author Adam Gibson
 */
public class ParameterServerTrainerContext implements TrainerContext {

    private ParameterServerNode parameterServerNode;
    private MediaDriver mediaDriver;
    private MediaDriver.Context mediaDriverContext;
    private int statusServerPort = 33000;
    private int numUpdatesPerEpoch = 1;
    private String[] parameterServerArgs;
    private int numWorkers = 1;

    /**
     * Initialize the context
     *
     * @param model
     * @param args the arguments to initialize with (maybe null)
     */
    @Override
    public void init(Model model, Object... args) {
        mediaDriverContext = new MediaDriver.Context();
        mediaDriver = MediaDriver.launchEmbedded(mediaDriverContext);
        parameterServerNode = new ParameterServerNode(mediaDriver, statusServerPort, numWorkers);
        if (parameterServerArgs == null)
            parameterServerArgs = new String[] {"-m", "true", "-s", "1," + String.valueOf(model.numParams()), "-p",
                            "40323", "-h", "localhost", "-id", "11", "-md", mediaDriver.aeronDirectoryName(), "-sh",
                            "localhost", "-sp", String.valueOf(statusServerPort), "-u",
                            String.valueOf(numUpdatesPerEpoch)};

    }

    /**
     * Create a {@link Trainer}
     * based on the given parameters
     *
     * @param threadId   the thread id to use for this worker
     * @param model      the model to start the trainer with
     * @param rootDevice the root device id
     * @param useMDS     whether to use the {@link MagicQueue}
     *                   or not
     * @param wrapper    the wrapper instance to use with this trainer (this refernece is needed
     *                   for coordination with the {@link ParallelWrapper} 's {@link IterationListener}
     * @return the created training instance
     */
    @Override
    public Trainer create(int threadId, Model model, int rootDevice, boolean useMDS, ParallelWrapper wrapper,
                    WorkspaceMode mode, int averagingFrequency) {
        return ParameterServerTrainer.builder().originalModel(model).parameterServerClient(ParameterServerClient
                        .builder().aeron(parameterServerNode.getAeron())
                        .ndarrayRetrieveUrl(
                                        parameterServerNode.getSubscriber()[threadId].getResponder().connectionUrl())
                        .ndarraySendUrl(parameterServerNode.getSubscriber()[threadId].getSubscriber().connectionUrl())
                        .subscriberHost("localhost").masterStatusHost("localhost").masterStatusPort(statusServerPort)
                        .subscriberPort(40625 + threadId).subscriberStream(12 + threadId).build())
                        .replicatedModel(model).threadId(threadId).parallelWrapper(wrapper).useMDS(useMDS).build();
    }
}
