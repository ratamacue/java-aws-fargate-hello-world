package com.cj.adsystems.deployment;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
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

public class Deployer {
	
	private static final Logger logger = Logger.getLogger(Deployer.class.getName());
	
	public void deploy() throws UnsupportedEncodingException, Exception, InterruptedException {
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
		Integer DOCKERPORT = 8080;
		String roleARN = String.format("arn:aws:iam::%s:role/AdSystemsFargateManagerRole", AWS_REGISTRY_ID);
		

		/*
		 * End Configuration Options
		 */


		AmazonECR ecr = AmazonECRClientBuilder.standard().withRegion(AWS_REGION).build();
		AmazonECS ecs = AmazonECSClientBuilder.standard().withRegion(AWS_REGION).build();
		AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).build();
		AmazonElasticLoadBalancing elb = AmazonElasticLoadBalancingClientBuilder.standard().withRegion(AWS_REGION).build();

		//https://stackoverflow.com/questions/40099527/pulling-image-from-amazon-ecr-using-docker-java
		createEcrRepository(ecr, APPLICATION_NAME);
		DockerClient dockerClient = setupDocker(ecr);
	    String ecrImageName = dockerBuild(dockerClient, DOCKER_WORKING_DIR, AWS_REGISTRY_ID, AWS_REGION, APPLICATION_NAME, DOCKER_TAG);
		dockerPush(ecrImageName, dockerClient); 
		
		
		//Here's where we begin translating the Fargate guide http://docs.aws.amazon.com/AmazonECS/latest/developerguide/ECS_AWSCLI_Fargate.html
		Cluster cluster = createCluster(CLUSTER_NAME, ecs);
		
		String securityGroup = createFirewallSecurityGroup(ec2, APPLICATION_NAME);
		
		
		//NetworkConfiguration networkConfiguration = createNetworkConfiguration(ec2);
		NetworkConfiguration networkConfiguration = new NetworkConfiguration().withAwsvpcConfiguration(
				new AwsVpcConfiguration()
					.withSecurityGroups(securityGroup)
					.withSubnets(VPC_SUBNETS)
					.withAssignPublicIp(AssignPublicIp.ENABLED) //Crashloops without proper internet NAT set up?
				);
		
		
		String targetGroupARN = createLoadBalancer(elb, APPLICATION_NAME, VPC_SUBNETS, securityGroup, DOCKERPORT);

		LoadBalancer balancer = new LoadBalancer()
				.withContainerName(APPLICATION_NAME)
				.withTargetGroupArn(targetGroupARN)
				.withContainerPort(DOCKERPORT);
		
		TaskDefinition task = registerTask(ecs, roleARN, ecrImageName, APPLICATION_NAME, DOCKERPORT, MEMORY, CPU);

		run(ecs, cluster, networkConfiguration, balancer, APPLICATION_NAME, task);
		
	    logger.info("Complete.");
	}


	private static String createFirewallSecurityGroup(AmazonEC2 ec2, String securityGroupName) {
		String groupId;
		try {
			groupId = ec2.createSecurityGroup(new CreateSecurityGroupRequest().withGroupName(securityGroupName).withDescription(securityGroupName)).getGroupId();
			logger.log(Level.WARNING,"Created a new security group "+securityGroupName, new RuntimeException("Just A Stack Trace"));

		}catch(Exception e) {
			List<SecurityGroup> groups = ec2.describeSecurityGroups(new DescribeSecurityGroupsRequest().withGroupNames(securityGroupName)).getSecurityGroups();
			if(groups.size()!=1) throw new RuntimeException("Error finding security group "+securityGroupName);
			groupId=groups.get(0).getGroupId();
		}
		
		
		try{
			ec2.authorizeSecurityGroupEgress(new AuthorizeSecurityGroupEgressRequest().withIpPermissions(new IpPermission().withIpProtocol("-1")).withGroupId(groupId));		
		}catch (Exception e) {
			logger.info("Unable to set up egress, may already have been done.");
		}
		
		try {
			ec2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest().withGroupId(groupId).withIpProtocol("-1").withCidrIp("0.0.0.0/0"));
		}catch (Exception e) {
			logger.info("Unable to set up ingress, may already have been done.");
		}
		
		return groupId;
	}


	private static String createLoadBalancer(AmazonElasticLoadBalancing elb, String name, Set<String> subnets, String securityGroups, Integer port) throws InterruptedException {
		CreateLoadBalancerRequest lbRequest = new CreateLoadBalancerRequest()
				.withName(name)
				.withSecurityGroups(securityGroups) //CRASHLOOP?
				.withType(LoadBalancerTypeEnum.Application)
				.withSubnets(subnets);
		
		CreateLoadBalancerResult result = elb.createLoadBalancer(lbRequest);
		
		if(result.getLoadBalancers().size() !=1) throw new RuntimeException("Expected exactly one load balancer to be created here.");
		

		com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer balancer = result.getLoadBalancers().get(0);
		String loadBalancerArn = balancer.getLoadBalancerArn();
		String domain = balancer.getDNSName();
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
		
		logger.info(String.format("\n********\nCongratulations, you have a domain name.  %s \n********", domain));
		
		return targetGroupArn;
	}


	private static void run(AmazonECS ecs, Cluster cluster, NetworkConfiguration networkConfiguration, LoadBalancer balancers, String applicationName, TaskDefinition task) {
		

		try {
			ecs.createService(new CreateServiceRequest()
					.withCluster(cluster.getClusterName())
					.withServiceName(applicationName)
					.withTaskDefinition(task.getTaskDefinitionArn())
					.withDesiredCount(2)
					.withLaunchType("FARGATE")
					.withNetworkConfiguration(networkConfiguration)
					.withLoadBalancers(balancers));
		}catch(Exception createException){
			//Assume that the service already exists.  Attempt to update it.
			logger.info("Failed to create service, attempting to update instead.  If this works, you can ignore this error.");
			try {
				ecs.updateService(new UpdateServiceRequest()
						.withCluster(cluster.getClusterName())
						.withService(applicationName)
						.withTaskDefinition(task.getTaskDefinitionArn())
						.withDesiredCount(2)
						.withNetworkConfiguration(networkConfiguration));
			}catch(Exception updateException) {
				logger.log(Level.WARNING, "trouble creating service", createException);
				logger.log(Level.SEVERE, "trouble updating service", updateException);
			}
		}

	}


	private static TaskDefinition registerTask(AmazonECS ecs, String roleArn, String ecrImageName,
			String applicationName, Integer port, Integer memory, Integer cpu) {
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
		return task;
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
