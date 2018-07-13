package net.corda.examples.obligationApi.controllers

import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

import net.corda.examples.obligation.Obligation
import net.corda.examples.obligation.flows.IssueObligation
import net.corda.examples.obligation.flows.SettleObligation
import net.corda.examples.obligation.flows.TransferObligation
import net.corda.examples.obligationApi.models.ObligationSimpleObj
import net.corda.examples.obligationApi.models.toSimpleName
import net.corda.examples.obligationApi.models.toSimpleObj
import net.corda.examples.obligationApi.rpcClient.NodeRPCConnection

import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import org.springframework.web.bind.annotation.*

import java.util.*

@RestController
@RequestMapping("/api")
class ObligationApiController(private val rpc: NodeRPCConnection) {

    private val rpcOps = rpc.proxy
    val myIdentity = rpcOps.nodeInfo().legalIdentities.first()

    @GetMapping(value = "/me", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun me() = mapOf("me" to myIdentity.toSimpleName())

    @GetMapping(value = "/peers", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun peers() = mapOf("peers" to rpcOps.networkMapSnapshot()
            .filter { nodeInfo -> nodeInfo.legalIdentities.first() != myIdentity }
            .map { it.legalIdentities.first().name.organisation })

    @GetMapping(value = "/owed-per-currency", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun owedPerCurrency() = rpcOps.vaultQuery(Obligation::class.java).states
            .filter { (state) -> state.data.lender != myIdentity }
            .map { (state) -> state.data.amount }
            .groupBy({ amount -> amount.token }, { (quantity) -> quantity })
            .mapValues { it.value.sum() }

    @GetMapping(value = "/obligations", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun obligations(): List<ObligationSimpleObj> {
        val statesAndRefs = rpcOps.vaultQuery(Obligation::class.java).states
        return statesAndRefs
                .map { stateAndRef -> stateAndRef.state.data }
                .map { state ->
                    // We map the anonymous lender and borrower to well-known identities if possible.
                    val possiblyWellKnownLender = rpcOps.wellKnownPartyFromAnonymous(state.lender) ?: state.lender
                    val possiblyWellKnownBorrower = rpcOps.wellKnownPartyFromAnonymous(state.borrower) ?: state.borrower

                    Obligation(state.amount,
                            possiblyWellKnownLender,
                            possiblyWellKnownBorrower,
                            state.paid,
                            state.linearId).toSimpleObj()
                }
    }

    @GetMapping(value = "/cash", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun cash() = rpcOps.vaultQuery(Cash.State::class.java).states

    @GetMapping(value = "/cash-balances", produces = arrayOf(MediaType.APPLICATION_JSON_VALUE))
    fun getCashBalances() = rpcOps.getCashBalances().mapValues { it.value.toString() }

    @GetMapping(value = "/self-issue-cash", produces = arrayOf(MediaType.TEXT_PLAIN_VALUE))
    fun selfIssueCash(@RequestParam(value = "amount") amount: Int,
                      @RequestParam(value = "currency") currency: String): ResponseEntity<Any?> {

        // 1. Prepare issue request.
        val issueAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))
        val notary = rpcOps.notaryIdentities().firstOrNull() ?: throw IllegalStateException("Could not find a notary.")
        val issueRef = OpaqueBytes.of(0)
        val issueRequest = CashIssueFlow.IssueRequest(issueAmount, issueRef, notary)

        // 2. Start flow and wait for response.
        val (status, message) = try {
            val flowHandle = rpcOps.startFlowDynamic(CashIssueFlow::class.java, issueRequest)
            val result = flowHandle.use { it.returnValue.getOrThrow() }
            HttpStatus.CREATED to "Transaction id ${result.stx.id} committed to ledger.\n${result.stx.tx.outputs.single().data}"
        } catch (e: Exception) {
            HttpStatus.BAD_REQUEST to e.message
        }

        // 3. Return the response.
        return ResponseEntity.status(status).body(message)

    }

    @GetMapping(value = "/issue-obligation", produces = arrayOf(MediaType.TEXT_PLAIN_VALUE))
    fun issueObligation(@RequestParam(value = "amount") amount: Int,
                        @RequestParam(value = "currency") currency: String,
                        @RequestParam(value = "party") party: String): ResponseEntity<String> {
        // 1. Get party objects for the counterparty.
        val lenderIdentity = rpcOps.partiesFromName(party, exactMatch = false).singleOrNull()
                ?: throw IllegalStateException("Couldn't lookup node identity for $party.")

        // 2. Create an amount object.
        val issueAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))

        // 3. Start the IssueObligation flow. We block and wait for the flow to return.
        val (status, message) = try {
            val flowHandle = rpcOps.startFlowDynamic(
                    IssueObligation.Initiator::class.java,
                    issueAmount,
                    lenderIdentity,
                    true
            )

            val result = flowHandle.use { it.returnValue.getOrThrow() }
            HttpStatus.CREATED to "Transaction id ${result.id} committed to ledger.\n${result.tx.outputs.single().data}"
        } catch (e: Exception) {
            HttpStatus.BAD_REQUEST to e.message
        }

        // 4. Return the result.
        return ResponseEntity.status(status).body(message)
    }

    @GetMapping(value = "/transfer-obligation", produces = arrayOf(MediaType.TEXT_PLAIN_VALUE))
    fun transferObligation(@RequestParam(value = "id") id: String,
                           @RequestParam(value = "party") party: String): ResponseEntity<String> {
        val linearId = UniqueIdentifier.fromString(id)
        val newLender = rpcOps.partiesFromName(party, exactMatch = false).singleOrNull()
                ?: throw IllegalStateException("Couldn't lookup node identity for $party.")

        val (status, message) = try {
            val flowHandle = rpcOps.startFlowDynamic(
                    TransferObligation.Initiator::class.java,
                    linearId,
                    newLender,
                    true
            )

            flowHandle.use { flowHandle.returnValue.getOrThrow() }
            HttpStatus.CREATED to "Obligation $id transferred to $party."
        } catch (e: Exception) {
            HttpStatus.BAD_REQUEST to e.message
        }

        return ResponseEntity.status(status).body(message)

    }

    @GetMapping(value = "/settle-obligation", produces = arrayOf(MediaType.TEXT_PLAIN_VALUE))
    fun settleObligation(@RequestParam("id") id: String,
                         @RequestParam("amount") amount: Int,
                         @RequestParam("currency") currency: String): ResponseEntity<String> {
        val linearId = UniqueIdentifier.fromString(id)
        val settleAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))

        val (status, message) = try {
            val flowHandle = rpcOps.startFlowDynamic(
                    SettleObligation.Initiator::class.java,
                    linearId,
                    settleAmount,
                    true
            )

            flowHandle.use { flowHandle.returnValue.getOrThrow() }
            HttpStatus.CREATED to "$amount $currency paid off on obligation id $id."
        } catch (e: Exception) {
            HttpStatus.BAD_REQUEST to e.message
        }

        return ResponseEntity.status(status).body(message)
    }
}