package com.cj.adsystems.deployment;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.regions.Regions;

public class Deployment {
	
	private Regions AWS_REGION = Regions.US_EAST_1;
	private String CLUSTER_NAME = "ad-systems";
	private String APPLICATION_NAME = "fargate-demo";
	private String ENVIRONMENT_NAME = "lab";
	private String AWS_REGISTRY_ID="727586729164";
	private String DOCKER_TAG = "deploy";
	private Integer MEMORY = 512;
	private Integer CPU = 256;
	private Set<String> VPC_SUBNETS=Stream.of("subnet-6f752a45", "subnet-361d1240", "subnet-14154d4c", "subnet-b64d718b", "subnet-d7c941db", "subnet-d5bdddb0").collect(Collectors.toSet());
	private Integer DOCKERPORT = 8080;
	private String roleARN = String.format("arn:aws:iam::%s:role/AdSystemsFargateManagerRole", AWS_REGISTRY_ID);
	
	public Regions getRegion() {
		return AWS_REGION;
	}

	public String getClusterName() {
		return CLUSTER_NAME;
	}

	public String getApplicationName() {
		return APPLICATION_NAME;
	}

	public String getAwsRegistryId() {
		return AWS_REGISTRY_ID;
	}

	public String getDockerTag() {
		return DOCKER_TAG;
	}

	public Integer getMemory() {
		return MEMORY;
	}

	public Integer getCPU() {
		return CPU;
	}

	public Set<String> getVpcSubnets() {
		return VPC_SUBNETS;
	}

	public Integer getDockerPort() {
		return DOCKERPORT;
	}

	public String getRoleArn() {
		return roleARN;
	}
	

}
