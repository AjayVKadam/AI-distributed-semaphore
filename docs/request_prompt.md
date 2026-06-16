# Project Goal
Build a java spring-boot project  with following goals
- Application will have multiple instances running as K8S pods.
- Provide a distributed lock mechanism  using MongoDB GA version
- Provide logic to ensure only one instance is able to grab the lock for 15 mins, use atomic operation provided by MongoDB.
- If  an instance is not able to gab a lock, it should wait upto 3 mins , retrying avery 15 seconds. 
- Extend the distributed lock mechanism to implement a Sepmahore
- Provide K8S deployment files to deploy the application in Kubernetes in 4  Data Centers
- Log entry & exit for each methods.
- Generate javadocs for class and methods
- Generate appropriate README.md 
