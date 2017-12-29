The deploying user needs the following role:
```
    "Effect": "Allow",
    "Action": [
        "iam:GetRole",
        "iam:PassRole"
    ],
    "Resource": "*"
```
    
And the roleARN needs to have the following:
Policies: AmazonEC2ContainerRegistryFullAccess, AmazonECS_FullAccess, AmazonEC2ContainerServiceforEC2Role, 
Trust Relationship: 
```{
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
}```

Otherwise aws ecs describe-services --cluster ad-systems --services fargate-demo
leads to error ECS was unable to assume the role

