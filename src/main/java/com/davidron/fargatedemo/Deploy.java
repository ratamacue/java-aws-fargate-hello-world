package com.davidron.fargatedemo;


import java.util.logging.Logger;

import org.apache.commons.cli.MissingOptionException;

import com.cj.adsystems.deployment.fargate.Deployer;

public class Deploy {

	private static final Logger logger = Logger.getLogger(Deploy.class.getName());

	public static void main(String[] args) throws Exception {
		try {
			new Deployer().deploy(new DeploymentImpl( args ));
		}catch(MissingOptionException missingOption) {
			//do nothing, no reason to print a stack trace because help was offered.
		}
	}
}
