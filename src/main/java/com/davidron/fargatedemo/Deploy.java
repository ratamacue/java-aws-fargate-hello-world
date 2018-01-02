package com.davidron.fargatedemo;


import java.util.logging.Logger;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import com.cj.adsystems.deployment.fargate.Deployer;

public class Deploy {

	private static final Logger logger = Logger.getLogger(Deploy.class.getName());

	public static void main(String[] args) throws Exception {

		Options options = new Options();
				
		new Deployer().deploy(new DeploymentImpl( new DefaultParser().parse(options, args)));
	}
}
