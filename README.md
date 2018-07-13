# obligation-webapp-springboot
Web app that uses a Spring Boot API for the [Obligation CorDapp](https://github.com/corda/obligation-cordapp), that talks to a Corda node over Corda RPC.

## Pre-requisites
Follow the [Instructions for setting up](https://github.com/corda/obligation-cordapp#instructions-for-setting-up) which sets up a Notary and 3 Corda Nodes with the Obligation CorDapp installed on each node.


## Starting the web app
```
git clone git@github.com:clydedacruz/obligation-webapp-springboot.git

./gradlew runPartyAServer 
```

### Note
The obligation-cordapp-0.1.jar is included in the `jars` directory to make the Corda flows and contracts accessible to api controller code.

It can be built by simply running `./gradlew jar` in the root directory of the `obligation-cordapp`