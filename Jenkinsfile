@Library('gitlab.cj.com/ad-systems/jenkins-utils@master') _

node('build203') {
  ansiColor('xterm') {
    stage('Maven Build') {
      checkout scm
      gitCommit = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
      currentBuild.displayName = gitCommit
      imageTag = "727586729164.dkr.ecr.us-west-1.amazonaws.com/fargate-demo:${gitCommit}"
      mvn("install")
    }

    stage('Docker Build') {
      sh "docker build -q -t ${imageTag} ."
    }

    stage('Docker Deploy') {
      def dockerLogin = aws("ecr get-login", returnStdout: true)
      sh dockerLogin
      try {
        aws("ecr create-repository --repository-name fargate-demo", returnStdout: true)
      } catch (err) {
        echo "Create repository failed. Assuming the repository exists. Continuing..."
      }
      sh "docker push ${imageTag}"
    }

    stage('Fargate Deploy') {
      println("BRANCH_NAME is "+env.BRANCH_NAME)

      if(env.BRANCH_NAME.equals("master")){
          environment = "production"
          replicas = 2
          secretEnv = "production"
      } else if (env.BRANCH_NAME.startsWith("_")) {
          environment = env.BRANCH_NAME.replaceFirst("_", "lab-").toLowerCase()
          replicas = 1
          secretEnv = "staging"
      }

        sh(
          "ENVIRONMENT=$environment " +
          "GIT_COMMIT=$gitCommit " +
          "REPLICAS=$replicas " +
          "SECRET_ENV=$secretEnv " +
          "SVC_TYPE=LoadBalancer " +
          "IMAGE_TAG=$imageTag"+
          "./templater.sh fargate-task.yaml > target/fargate-task.yaml"
        )
        aws("ecs create-cluster --cluster-name fargate-cluster-$environment", returnStdout: true)
        aws("ecs register-task-definition --cli-input-json target/fargate-task.json", returnStdout: true)
        aws("ecs create-service --cluster fargate-cluster --service-name fargate-service --task-definition sample-fargate:1 --desired-count 2 --launch-type \"FARGATE\" --network-configuration \"awsvpcConfiguration={subnets=[subnet-abcd1234],securityGroups=[sg-abcd1234]}\"", returnStdout: true)
              
    }
  }
}
