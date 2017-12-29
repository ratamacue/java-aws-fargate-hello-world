package com.cj.adsystems.deployment;


import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.AuthorizationData;
import com.amazonaws.services.ecr.model.CreateRepositoryRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult;
import com.amazonaws.services.ecr.model.RepositoryAlreadyExistsException;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.ecs.model.AssignPublicIp;
import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.Compatibility;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateClusterRequest;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.CreateServiceResult;
import com.amazonaws.services.ecs.model.NetworkConfiguration;
import com.amazonaws.services.ecs.model.NetworkMode;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
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

	
	//public static final File SLASH_TARGET_SLASH_CLASSES = new File(Deploy.class.getClassLoader().getResource("thisistheroot").getPath());
	//public static final File DOCKER_WORKING_DIR = SLASH_TARGET_SLASH_CLASSES.getParentFile().getParentFile().getParentFile();
	
	private static final Logger logger = Logger.getLogger(Deploy.class.getName());

	public static void main(String[] args) throws Exception {

		/*
		 * Configuration Options Follow.
		 * These should be environment variables or args to main.
		 */
		Regions AWS_REGION = Regions.US_EAST_1;
		String CLUSTER_NAME = "ad-systems";
		String APPLICATION_NAME = "fargate-demo";
		String ENVIRONMENT_NAME = "lab";
		File DOCKER_WORKING_DIR = new File(".");
		String AWS_REGISTRY_ID="727586729164";
		String DOCKER_TAG = "deploy";
		Integer MEMORY = 512;
		Integer CPU = 256;
		Set<String> VPC_SUBNETS=ImmutableSet.of("subnet-6f752a45", "subnet-361d1240", "subnet-14154d4c", "subnet-b64d718b", "subnet-d7c941db", "subnet-d5bdddb0");
		Set<String> VPC_SECURITY_GROUPS = ImmutableSet.of("sg-a84f08d3");

		/*
		 * End Configuration Options
		 */


		AmazonECR ecr = AmazonECRClientBuilder.standard().withRegion(AWS_REGION).build();
		AmazonECS ecs = AmazonECSClientBuilder.standard().withRegion(AWS_REGION).build();
		AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).build();
		//https://stackoverflow.com/questions/40099527/pulling-image-from-amazon-ecr-using-docker-java
		createEcrRepository(ecr, APPLICATION_NAME);
		DockerClient dockerClient = setupDocker(ecr);
	    String ecrImageName = dockerBuild(dockerClient, DOCKER_WORKING_DIR, AWS_REGISTRY_ID, AWS_REGION, APPLICATION_NAME, DOCKER_TAG);
		dockerPush(ecrImageName, dockerClient); 
		
		
		//Here's where we begin translating the Fargate guide http://docs.aws.amazon.com/AmazonECS/latest/developerguide/ECS_AWSCLI_Fargate.html
		Cluster cluster = createCluster(CLUSTER_NAME, ecs);
		//NetworkConfiguration networkConfiguration = createNetworkConfiguration(ec2);
		NetworkConfiguration networkConfiguration = new NetworkConfiguration().withAwsvpcConfiguration(
				new AwsVpcConfiguration()
					.withSecurityGroups(VPC_SECURITY_GROUPS)
					.withSubnets(VPC_SUBNETS)
					.withAssignPublicIp(AssignPublicIp.ENABLED)
				);
		run(ecs, cluster, networkConfiguration, AWS_REGISTRY_ID, ecrImageName, APPLICATION_NAME, MEMORY, CPU);
		
		
		
	    logger.info("Complete.");
	}


	private static void run(AmazonECS ecs, Cluster cluster, NetworkConfiguration networkConfiguration, String accountId, String ecrImageName, String applicationName, Integer memory, Integer cpu) {
		//String roleARN = String.format("arn:aws:iam::%s:role/aws-service-role/ecs.amazonaws.com/AWSServiceRoleForECS", accountId);
		String roleARN = String.format("arn:aws:iam::%s:role/AmazonEC2ContainerServiceforEC2", accountId);


		/*
		 * created the role AmazonEC2ContainerServiceforEC2 confusingly containing the policy AmazonEC2ContainerServiceforEC2Role
		 */
		
		//http://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_passrole.html
		
		
		/*
		 * http://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_passrole.html
		The deploying user needs the following role:
		    "Effect": "Allow",
            "Action": [
	            "iam:GetRole",
	            "iam:PassRole"
	        ],
            "Resource": "*"

            Otherwise aws ecs describe-services --cluster ad-systems --services fargate-demo
            leads to error ECS was unable to assume the role
		 */



		ContainerDefinition containerDefinition = new ContainerDefinition()
				.withImage(ecrImageName)
				.withName(applicationName);
				//.withMemory(memory);
		RegisterTaskDefinitionRequest taskRequest = new RegisterTaskDefinitionRequest()
				.withContainerDefinitions(ImmutableSet.of(containerDefinition))
				.withFamily(applicationName)
				.withCpu(cpu.toString())
				.withRequiresCompatibilities(Compatibility.FARGATE)
				.withNetworkMode(NetworkMode.Awsvpc)
				.withMemory(memory.toString())
				.withExecutionRoleArn(roleARN)
				.withTaskRoleArn(roleARN);
		TaskDefinition task = ecs.registerTaskDefinition(taskRequest).getTaskDefinition();


		CreateServiceRequest createServiceRequest = new CreateServiceRequest()
				.withCluster(cluster.getClusterName())
				.withServiceName(applicationName)
				.withTaskDefinition(task.getTaskDefinitionArn())
				.withDesiredCount(2)
				.withLaunchType("FARGATE")
				.withNetworkConfiguration(networkConfiguration);
		try {
			CreateServiceResult createServiceResult = ecs.createService(createServiceRequest);
		}catch(Exception e){
			//Assume that the service already exists.  Attempt to update it.
			ecs.updateService(new UpdateServiceRequest()
					.withCluster(cluster.getClusterName())
					.withService(applicationName)
					.withTaskDefinition(task.getTaskDefinitionArn())
					.withDesiredCount(2)
					.withNetworkConfiguration(networkConfiguration));
		}


	}


	private static Cluster createCluster(String clusterName, AmazonECS ecs) {
		CreateClusterRequest request = new CreateClusterRequest().withClusterName(clusterName);
		return ecs.createCluster(request).getCluster();
		
	}


	private static void dockerPush(String ecrImageName, DockerClient dockerClient) throws Exception {
	    dockerClient.pushImageCmd(Identifier.fromCompoundString(ecrImageName)).withAuthConfig(dockerClient.authConfig()).exec(new PushImageResultCallback()).awaitSuccess();
	}


	private static String dockerBuild(DockerClient dockerClient, File workingFolder, String awsAccountNumber, Regions region, String applicationName, String dockerTag) {
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
	    
	    dockerClient.buildImageCmd(workingFolder).withTags(ImmutableSet.of(ecrImageName)).exec(callback).awaitImageId();
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


	private static void createEcrRepository(AmazonECR ecr, String applicationName) {
		CreateRepositoryRequest repoRequest = new CreateRepositoryRequest();
		repoRequest.setRepositoryName(applicationName);
		try {
			ecr.createRepository(repoRequest);
		}catch(RepositoryAlreadyExistsException e) {
			logger.info(String.format("Registry %s Already Exists.", applicationName));
		}
	}
	
}
