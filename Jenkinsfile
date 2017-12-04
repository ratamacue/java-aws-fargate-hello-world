@Library('gitlab.cj.com/ad-systems/jenkins-utils@master') _

node('build203') {
  ansiColor('xterm') {
    stage('Maven Build') {
      checkout scm
      gitCommit = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
      currentBuild.displayName = gitCommit
      imageTag = "727586729164.dkr.ecr.us-west-1.amazonaws.com/kubernetes-demo:${gitCommit}"
      mvn("install")
    }

    stage('Docker Build') {
      sh "docker build -q -t ${imageTag} ."
    }

    stage('Docker Deploy') {
      def dockerLogin = aws("ecr get-login", returnStdout: true)
      sh dockerLogin
      try {
        aws("ecr create-repository --repository-name kubernetes-demo", returnStdout: true)
      } catch (err) {
        echo "Create repository failed. Assuming the repository exists. Continuing..."
      }
      sh "docker push ${imageTag}"
    }



    stage('Kubernetes Deploy') {
      deployBranch = false
      println("BRANCH_NAME is "+env.BRANCH_NAME)

      if(env.BRANCH_NAME.equals("master")){
          environment = "production"
          replicas = 2
          secretEnv = "production"
          deployBranch = true
      } else if (env.BRANCH_NAME.startsWith("_")) {
          environment = env.BRANCH_NAME.replaceFirst("_", "lab-").toLowerCase()
          deployBranch = true
          replicas = 1
          secretEnv = "staging"
      }


      if (deployBranch) {
        sh(
          "ENVIRONMENT=$environment " +
          "GIT_COMMIT=$gitCommit " +
          "REPLICAS=$replicas " +
          "SECRET_ENV=$secretEnv " +
          "SVC_TYPE=LoadBalancer " +
          "./templater.sh deployment.yaml > target/deployment.yaml"
        )
        kubeApply("./target/deployment.yaml")
      }

    }







  }
}
