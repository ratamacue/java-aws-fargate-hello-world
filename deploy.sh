#!/bin/sh

imageTag="727586729164.dkr.ecr.us-east-1.amazonaws.com/fargate-demo:test"

docker build -q -t ${imageTag} .
$(aws --no-include-email ecr get-login)
docker push ${imageTag}


export ENVIRONMENT=$environment
export GIT_COMMIT=$gitCommit
export REPLICAS=$replicas
export SECRET_ENV=$secretEnv
export SVC_TYPE=LoadBalancer
export IMAGE_TAG=$imageTag
export TASK_NAME="fargate-demo"
./templater.sh fargate-task.json > target/fargate-task.json


aws ecr --region us-east-1 create-repository --repository-name fargate-demo #will fail after first attempt
aws ecs --region us-east-1 create-cluster --cluster-name fargate-cluster-test
aws ecs --region us-east-1 register-task-definition --execution-role-arn "Hello" --cli-input-json "$(< target/fargate-task.json)"

#do we need the latest version? https://github.com/aws/amazon-ecs-cli#latest-version
aws ecs --region us-east-1 create-service --cluster fargate-cluster-test --service-name fargate-service-test --task-definition $TASK_NAME --desired-count 2 --launch-type "FARGATE" --network-configuration "awsvpcConfiguration={subnets=[subnet-361d1240],securityGroups=[sg-a84f08d3]}"


#"awsvpcConfiguration={subnets=[subnet-abcd1234],securityGroups=[sg-abcd1234]}"


