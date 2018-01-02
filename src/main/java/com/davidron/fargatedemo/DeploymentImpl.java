package com.davidron.fargatedemo;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;

import com.amazonaws.regions.Regions;
import com.cj.adsystems.deployment.fargate.Deployment;
import com.cj.adsystems.deployment.fargate.Deployment;

public class DeploymentImpl implements Deployment{
	
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
	private CommandLine opts;
	
	public DeploymentImpl(CommandLine opts) {
		this.opts = opts;
	}
	
	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getRegion()
	 */
	@Override
	public Regions getRegion() {
		return AWS_REGION;
	}

	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getClusterName()
	 */
	@Override
	public String getClusterName() {
		return CLUSTER_NAME;
	}

	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getApplicationName()
	 */
	@Override
	public String getApplicationName() {
		return APPLICATION_NAME;
	}

	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getAwsRegistryId()
	 */
	@Override
	public String getAwsRegistryId() {
		return AWS_REGISTRY_ID;
	}

	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getDockerTag()
	 */
	@Override
	public String getDockerTag() {
		return DOCKER_TAG;
	}

	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getMemory()
	 */
	@Override
	public Integer getMemory() {
		return MEMORY;
	}

	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getCPU()
	 */
	@Override
	public Integer getCPU() {
		return CPU;
	}

	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getVpcSubnets()
	 */
	@Override
	public Set<String> getVpcSubnets() {
		return VPC_SUBNETS;
	}

	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getDockerPort()
	 */
	@Override
	public Integer getDockerPort() {
		return DOCKERPORT;
	}

	/* (non-Javadoc)
	 * @see com.davidron.fargatedemo.Deployment2#getRoleArn()
	 */
	@Override
	public String getRoleArn() {
		return roleARN;
	}
	

}
