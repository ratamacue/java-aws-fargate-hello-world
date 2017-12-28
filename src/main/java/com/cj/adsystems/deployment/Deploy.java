package com.cj.adsystems.deployment;


import java.io.File;
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
import com.amazonaws.services.s3.model.Region;
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
	public static final Regions AWS_REGION = Regions.US_EAST_1;
	public static final String APPLICATION_NAME = "fargate-demo";
	public static final String ENVIRONMENT_NAME = "lab";
	public static final String GIT_COMMIT = "f3ae7dd03f5e";
	public static final String DOCKER_IMAGE_TAG = "727586729164.dkr.ecr."+AWS_REGION.getName()+".amazonaws.com/fargate-demo:"+GIT_COMMIT;
	public static final File SLASH_TARGET_SLASH_CLASSES = new File(Deploy.class.getClassLoader().getResource("thisistheroot").getPath());
	public static final File DOCKER_WORKING_DIR = SLASH_TARGET_SLASH_CLASSES.getParentFile().getParentFile().getParentFile();
	///public static final String AWS_REGISTRY_ID="727586729164";
	/*
	 * Expected environment variables:
	 *  
	 */
	
	
	
	private static final Logger logger = Logger.getLogger(Deploy.class.getName());

	public static void main(String[] args) throws Exception {
		AmazonECR ecr = AmazonECRClientBuilder.standard().withRegion(AWS_REGION).build();
		
		//https://stackoverflow.com/questions/40099527/pulling-image-from-amazon-ecr-using-docker-java
		
		createEcrRepository(ecr);		
		dockerPush(ecr); 
	    
	    logger.info("Complete.");
			
	}


	private static void dockerPush(AmazonECR ecr) throws Exception {
		GetAuthorizationTokenRequest tokenRequest = new GetAuthorizationTokenRequest();
		//tokenRequest.setRegistryIds(ImmutableList.of("")); //Default registry is assumed?
	    GetAuthorizationTokenResult getAuthTokenResult = ecr.getAuthorizationToken(tokenRequest);
	    AuthorizationData authData = getAuthTokenResult.getAuthorizationData().get(0);
	    String userPassword = new String(Base64.decode(authData.getAuthorizationToken()), "UTF-8");
	    //logger.info(userPassword);
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
	    
	    BuildImageResultCallback callback = new BuildImageResultCallback() {
	        @Override
	        public void onNext(BuildResponseItem item) {
	           if(item.isErrorIndicated()) {
	        	   	logger.log(Level.SEVERE, item.getErrorDetail().toString(), new RuntimeException("Trouble Building The Docker Image"));
	           }
	           super.onNext(item);
	        }
	    };
	    
	    logger.info("Docker Working Dir is set to "+DOCKER_WORKING_DIR);
	    String imageId = dockerClient.buildImageCmd(DOCKER_WORKING_DIR).withTags(ImmutableSet.of(DOCKER_IMAGE_TAG)).exec(callback).awaitImageId();
	    dockerClient.pushImageCmd(Identifier.fromCompoundString(DOCKER_IMAGE_TAG)).withAuthConfig(dockerClient.authConfig()).exec(new PushImageResultCallback()).awaitSuccess();
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
