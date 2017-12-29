# Welcome
This is a very simple java project that will deploy a "Hello World" Java Webapp into AWS Fargate using only the AWS Java APIs and the java-docker library wrapper for Docker.  This is convenient for defining your deployment in code.

# Roles

The roleARN is the role that ECS will assume to be able to start EC2 instances for you.  It needs to have the following:
Policy: AmazonEC2ContainerServiceforEC2Role
Trust Relationship: 
```
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "",
      "Effect": "Allow",
      "Principal": {
        "Service": "ecs-tasks.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

The deploying user needs the following role so that the deploying user can pass the above role on to ECS (feel free to restrict the resource to just the roleArn rather than * for more security, otherwise the deploying user has root):
```
"Effect": "Allow",
"Action": [
    "iam:GetRole",
    "iam:PassRole"
],
"Resource": "*"
```


Without the above two things, you'll get errors when you run `aws ecs describe-services --cluster ad-systems --services fargate-demo` leads to error `ECS was unable to assume the role`.

