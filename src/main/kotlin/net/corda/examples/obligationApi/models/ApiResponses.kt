package net.corda.examples.obligationApi.models

import net.corda.core.identity.Party
import net.corda.examples.obligation.Obligation

fun Party.toSimpleName(): String {
    return "${name.organisation} (${name.locality}, ${name.country})"
}

data class ObligationSimpleObj(
        val amount: String,
        val lender: String,
        val borrower: String,
        val paid: String,
        val linearId: String)

fun Obligation.toSimpleObj(): ObligationSimpleObj {
    return ObligationSimpleObj(amount.toString(),lender.nameOrNull()!!.organisation ,borrower.nameOrNull()!!.organisation ,paid.toString(),linearId.id.toString())
}

