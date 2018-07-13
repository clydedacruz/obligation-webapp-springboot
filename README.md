# obligation-webapp-springboot
Web app that uses a Spring Boot API for the [Obligation CorDapp](https://github.com/corda/obligation-cordapp), that talks to a Corda node over Corda RPC.

## Pre-requisites
Follow the [Instructions for setting up](https://github.com/corda/obligation-cordapp#instructions-for-setting-up) which sets up a Notary and 3 Corda Nodes with the Obligation CorDapp installed on each node.


## Starting the web app

Make sure you have cloned the Obligation Cordapp example and started the nodes (as described in pre-requisites)
```
git clone git@github.com:clydedacruz/obligation-webapp-springboot.git

./gradlew runPartyAServer 
```

The UI for Party A will be accessible at 

`<IP>:8080/obligation`

The API for Party A will be accessible at

`<IP>:8080/obligation/api/`

We can similarly start up the web apps for *Party B* and *Party C* using the gradle tasks `runPartyBServer` (port 8081) and `runPartyCServer` (port 8082) respectively, which are defined in `build.gradle`


## API Endpoints
### Get names of peers in the network
`curl http://127.0.0.1:8080/obligation/api/peers`

### Get name of current node
`curl http://127.0.0.1:8080/obligation/api/me`

### Issue cash to yourself
`curl http://127.0.0.1:8080/obligation/api/self-issue-cash?amount=2000&currency=USD`

### Get cash balances
`curl http://127.0.0.1:8080/obligation/api/cash-balances`

### Issue obligation
`curl http://127.0.0.1:8080/obligation/api/issue-obligation?amount=100&currency=USD&party=PartyB`

### Get list obligations
`curl http://127.0.0.1:8080/obligation/api/obligations`
### Transfer obligation
`curl http://127.0.0.1:8080/obligation/api/transfer-obligation?id=4ecee9db-2d37-48f3-b10a-a1dd3cf272da&party=PartyB`

### Settle obligation
`curl http://127.0.0.1:8080/obligation/api/settle-obligation?id=918f8ca0-4785-4abb-8e17-5f2f8cdf0a7e&amount=100&currency=USD`


## Note
The obligation-cordapp-0.1.jar is included in the `jars` directory to make the Corda flows and contracts accessible to api controller code.

It can be built by simply running `./gradlew jar` in the root directory of the `obligation-cordapp`