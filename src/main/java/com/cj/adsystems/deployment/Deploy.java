package com.cj.adsystems.deployment;


import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.AuthorizationData;
import com.amazonaws.services.ecr.model.CreateRepositoryRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult;
import com.amazonaws.services.ecr.model.RepositoryAlreadyExistsException;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateClusterRequest;
import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.util.Base64;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.api.model.AuthResponse;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Identifier;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.google.common.collect.ImmutableSet;

public class Deploy {
	/* 
	 * Configuration Options Follow.
	 * These should be environment variables or args to main.
	 */
	public static final Regions AWS_REGION = Regions.US_EAST_1;
	public static final String APPLICATION_NAME = "fargate-demo";
	public static final String ENVIRONMENT_NAME = "lab";
	public static final File DOCKER_WORKING_DIR = new File(".");
	public static final String AWS_REGISTRY_ID="727586729164";
	public static final String DOCKER_TAG = "deploy";
	/* 
	 * End Configuration Options
	 */
	
	//public static final File SLASH_TARGET_SLASH_CLASSES = new File(Deploy.class.getClassLoader().getResource("thisistheroot").getPath());
	//public static final File DOCKER_WORKING_DIR = SLASH_TARGET_SLASH_CLASSES.getParentFile().getParentFile().getParentFile();
	
	private static final Logger logger = Logger.getLogger(Deploy.class.getName());

	public static void main(String[] args) throws Exception {
		AmazonECR ecr = AmazonECRClientBuilder.standard().withRegion(AWS_REGION).build();
		AmazonECS ecs = AmazonECSClientBuilder.standard().withRegion(AWS_REGION).build();
		//https://stackoverflow.com/questions/40099527/pulling-image-from-amazon-ecr-using-docker-java
		createEcrRepository(ecr);		
		DockerClient dockerClient = setupDocker(ecr);
	    String ecrImageName = dockerBuild(dockerClient, AWS_REGISTRY_ID, AWS_REGION, APPLICATION_NAME, DOCKER_TAG);
		dockerPush(ecrImageName, dockerClient); 
		
		
		//Here's where we begin translating the Fargate guide http://docs.aws.amazon.com/AmazonECS/latest/developerguide/ECS_AWSCLI_Fargate.html
		Cluster cluster = createCluster("ad-systems", ecs);
		createTask(ecs);
		
		
		
	    logger.info("Complete.");
	}


	private static void createTask(AmazonECS ecs) {
//		ContainerDefinition containerDefinition = new ContainerDefinition().wi;
//		RegisterTaskDefinitionRequest request = new RegisterTaskDefinitionRequest().withContainerDefinitions(containerDefinitions)
//		ecs.registerTaskDefinition(registerTaskDefinitionRequest);
		
	}


	private static Cluster createCluster(String clusterName, AmazonECS ecs) {
		CreateClusterRequest request = new CreateClusterRequest().withClusterName(clusterName);
		return ecs.createCluster(request).getCluster();
		
	}


	private static void dockerPush(String ecrImageName, DockerClient dockerClient) throws Exception {
	    dockerClient.pushImageCmd(Identifier.fromCompoundString(ecrImageName)).withAuthConfig(dockerClient.authConfig()).exec(new PushImageResultCallback()).awaitSuccess();
	}


	private static String dockerBuild(DockerClient dockerClient, String awsAccountNumber, Regions region, String applicationName, String dockerTag) {
		//final String DOCKER_IMAGE_TAG = "727586729164.dkr.ecr."+AWS_REGION.getName()+".amazonaws.com/fargate-demo:"+GIT_COMMIT;
		String ecrImageName = String.format("%s.dkr.ecr.%s.amazonaws.com/%s:%s", awsAccountNumber, region.getName(), applicationName, dockerTag);
		BuildImageResultCallback callback = new BuildImageResultCallback() {
	        @Override
	        public void onNext(BuildResponseItem item) {
	           if(item.isErrorIndicated()) {
	        	   	logger.log(Level.SEVERE, item.getErrorDetail().toString(), new RuntimeException("Trouble Building The Docker Image"));
	           }
	           super.onNext(item);
	        }
	    };
	    
	    dockerClient.buildImageCmd(DOCKER_WORKING_DIR).withTags(ImmutableSet.of(ecrImageName)).exec(callback).awaitImageId();
	    return ecrImageName;
	}


	private static DockerClient setupDocker(AmazonECR ecr) throws UnsupportedEncodingException {
		GetAuthorizationTokenRequest tokenRequest = new GetAuthorizationTokenRequest();
	    GetAuthorizationTokenResult getAuthTokenResult = ecr.getAuthorizationToken(tokenRequest);
	    AuthorizationData authData = getAuthTokenResult.getAuthorizationData().get(0);
	    String userPassword = new String(Base64.decode(authData.getAuthorizationToken()), "UTF-8");
	    String user = userPassword.substring(0, userPassword.indexOf(":"));
	    String password = userPassword.substring(userPassword.indexOf(":")+1);


	    DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
		    .withDockerHost("unix:///var/run/docker.sock")
		    .withDockerTlsVerify(false)
		    .withRegistryUsername(user)
		    .withRegistryPassword(password)
		    .withRegistryUrl(authData.getProxyEndpoint())
		    .build();
	    
	    DockerCmdExecFactory dockerCmdExecFactory = new JerseyDockerCmdExecFactory();
	    DockerClient dockerClient = DockerClientBuilder.getInstance(config)
	        .withDockerCmdExecFactory(dockerCmdExecFactory)
	    .build();

	    // Response
	    AuthResponse response = dockerClient.authCmd().exec();
	    logger.info("Auth Status is: "+response.getStatus());
		return dockerClient;
	}


	private static void createEcrRepository(AmazonECR ecr) {
		CreateRepositoryRequest repoRequest = new CreateRepositoryRequest();
		repoRequest.setRepositoryName(APPLICATION_NAME);
		try {
			ecr.createRepository(repoRequest);
		}catch(RepositoryAlreadyExistsException e) {
			logger.info(String.format("Registry %s Already Exists.", APPLICATION_NAME));
		}
	}
	
}
