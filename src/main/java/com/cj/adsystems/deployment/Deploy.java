package com.cj.adsystems.deployment;


import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jetty.util.log.Log;

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
import com.amazonaws.services.ecs.model.AssignPublicIp;
import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.Compatibility;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateClusterRequest;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkConfiguration;
import com.amazonaws.services.ecs.model.NetworkMode;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.ActionTypeEnum;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateListenerRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupResult;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancerTypeEnum;
import com.amazonaws.services.elasticloadbalancingv2.model.ProtocolEnum;
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetTypeEnum;
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
		Set<String> VPC_SUBNETS=set("subnet-6f752a45", "subnet-361d1240", "subnet-14154d4c", "subnet-b64d718b", "subnet-d7c941db", "subnet-d5bdddb0");
		Set<String> VPC_SECURITY_GROUPS = set("sg-8aa662fe");//set("sg-a84f08d3");
		Integer PORT = 8080;
		
		//See the readme for information about these roles
		//String roleARN = String.format("arn:aws:iam::%s:role/aws-service-role/ecs.amazonaws.com/AWSServiceRoleForECS", accountId);
		//String roleARN = String.format("arn:aws:iam::%s:role/AmazonEC2ContainerServiceforEC2", accountId);
		String roleARN = String.format("arn:aws:iam::%s:role/AdSystemsFargateManagerRole", AWS_REGISTRY_ID);
		

		/*
		 * End Configuration Options
		 */


		AmazonECR ecr = AmazonECRClientBuilder.standard().withRegion(AWS_REGION).build();
		AmazonECS ecs = AmazonECSClientBuilder.standard().withRegion(AWS_REGION).build();
		AmazonElasticLoadBalancing elb = AmazonElasticLoadBalancingClientBuilder.standard().withRegion(AWS_REGION).build();

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
					.withAssignPublicIp(AssignPublicIp.ENABLED) //Crashloops without proper internet NAT set up?
				);
		
		
		String targetGroupARN = createLoadBalancer(elb, APPLICATION_NAME, VPC_SUBNETS, VPC_SECURITY_GROUPS, PORT);
		
		/**
		 For Application Load Balancers and Network Load Balancers, this object must contain the load balancer target group ARN, the container name (as it appears in a container definition), 
		 and the container port to access from the load balancer. When a task from this service is placed on a container instance, the container instance and port combination is
		  registered as a target in the target group specified here.
		 */
		LoadBalancer balancer = new LoadBalancer()
				//.withLoadBalancerName(APPLICATION_NAME)
				.withContainerName(APPLICATION_NAME)
				.withTargetGroupArn(targetGroupARN)
				.withContainerPort(PORT);
		
		
		
		
		run(ecs, cluster, networkConfiguration, balancer, AWS_REGISTRY_ID, roleARN, ecrImageName, APPLICATION_NAME, PORT, MEMORY, CPU);
		
		
		
	    logger.info("Complete.");
	}


	private static String createLoadBalancer(AmazonElasticLoadBalancing elb, String name, Set<String> subnets, Set<String> securityGroups, Integer port) throws InterruptedException {
		CreateLoadBalancerRequest lbRequest = new CreateLoadBalancerRequest()
				.withName(name)
				.withSecurityGroups(securityGroups) //CRASHLOOP?
				.withType(LoadBalancerTypeEnum.Application)
				.withSubnets(subnets);
		
		CreateLoadBalancerResult result = elb.createLoadBalancer(lbRequest);
		
		
		
		if(result.getLoadBalancers().size() !=1) throw new RuntimeException("Expected exactly one load balancer to be created here.");
		

		String loadBalancerArn = result.getLoadBalancers().get(0).getLoadBalancerArn();
		String vpcId = result.getLoadBalancers().get(0).getVpcId();
		
		CreateTargetGroupResult targetGroupResult = elb.createTargetGroup(new CreateTargetGroupRequest()
				.withName(name)
				.withProtocol(ProtocolEnum.HTTP)
				.withPort(port)
				.withVpcId(vpcId)
				.withTargetType(TargetTypeEnum.Ip)
				);
		
		if(targetGroupResult.getTargetGroups().size() != 1) throw new RuntimeException("Expected exactly one targetgroup to be created here.");
		
		String targetGroupArn = targetGroupResult.getTargetGroups().get(0).getTargetGroupArn();
		
		elb.createListener(new CreateListenerRequest().withLoadBalancerArn(loadBalancerArn).withPort(port).withProtocol(ProtocolEnum.HTTP).withDefaultActions(new Action().withTargetGroupArn(targetGroupArn).withType(ActionTypeEnum.Forward)));
		
		
		//elb.registerTargets(new RegisterTargetsRequest().withTargetGroupArn(targetGroupArn).withTargets(new TargetDescription().withAvailabilityZone("all").withId("WTF")));
		
		
		
		return targetGroupArn;
		
		
		
//		DescribeLoadBalancersRequest request = new DescribeLoadBalancersRequest().withLoadBalancerNames(applicationName);
//		DescribeLoadBalancersResult response = elb.describeLoadBalancers(request);
//		
//		List<LoadBalancerDescription> lbs = response.getLoadBalancerDescriptions();
//		if(lbs.size()!=1) throw new RuntimeException(String.format("There should be exactly one load balancer with the name %s, but there were %s", applicationName, lbs.size()));
//		LoadBalancerDescription lbd = lbs.get(0);
		
		//return new LoadBalancer().setContainerPort(lbd.);;
		

	}


	private static void run(AmazonECS ecs, Cluster cluster, NetworkConfiguration networkConfiguration, LoadBalancer balancers, String accountId, String roleArn, String ecrImageName, String applicationName, Integer port, Integer memory, Integer cpu) {
		

		ContainerDefinition containerDefinition = new ContainerDefinition()
				.withImage(ecrImageName)
				.withName(applicationName)
				.withPortMappings(set(new PortMapping().withContainerPort(port)));
		RegisterTaskDefinitionRequest taskRequest = new RegisterTaskDefinitionRequest()
				.withContainerDefinitions(set(containerDefinition))
				.withFamily(applicationName)
				.withCpu(cpu.toString())
				.withRequiresCompatibilities(Compatibility.FARGATE)
				.withNetworkMode(NetworkMode.Awsvpc)
				.withMemory(memory.toString())
				.withExecutionRoleArn(roleArn)
				.withTaskRoleArn(roleArn);
		TaskDefinition task = ecs.registerTaskDefinition(taskRequest).getTaskDefinition();

		try {
			ecs.createService(new CreateServiceRequest()
					.withCluster(cluster.getClusterName())
					.withServiceName(applicationName)
					.withTaskDefinition(task.getTaskDefinitionArn())
					.withDesiredCount(2)
					.withLaunchType("FARGATE")
					.withNetworkConfiguration(networkConfiguration)
					.withLoadBalancers(balancers)); //I'm not sure how to 
		}catch(Exception e){
			//Assume that the service already exists.  Attempt to update it.
			logger.log(Level.INFO, "Failed to create service, attempting to update instead.  If this works, you can ignore this error.", e);
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
	    
	    dockerClient.buildImageCmd(workingFolder).withTags(set(ecrImageName)).exec(callback).awaitImageId();
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
	
	@SafeVarargs
	private static <T> Set<T> set(T... elements){
		return Stream.of(elements).collect(Collectors.toSet());
	}
	
}
