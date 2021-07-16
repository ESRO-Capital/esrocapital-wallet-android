package io.horizontalsystems.bankwallet.core.factories

import io.horizontalsystems.bankwallet.core.IAppNumberFormatter
import io.horizontalsystems.bankwallet.entities.*
import io.horizontalsystems.bankwallet.entities.transactionrecords.TransactionRecord
import io.horizontalsystems.bankwallet.entities.transactionrecords.bitcoin.BitcoinIncomingTransactionRecord
import io.horizontalsystems.bankwallet.entities.transactionrecords.bitcoin.BitcoinOutgoingTransactionRecord
import io.horizontalsystems.bankwallet.entities.transactionrecords.evm.*
import io.horizontalsystems.bankwallet.modules.transactionInfo.TransactionInfoAddressMapper
import io.horizontalsystems.bankwallet.modules.transactions.TransactionViewItem
import io.horizontalsystems.bankwallet.modules.transactions.TransactionViewItem.*
import java.lang.IllegalArgumentException
import java.util.*

class TransactionViewItemFactory(
    private val transactionInfoAddressMapper: TransactionInfoAddressMapper,
    private val numberFormatter: IAppNumberFormatter
) {

    fun item(
        wallet: Wallet,
        record: TransactionRecord,
        lastBlockInfo: LastBlockInfo?,
        mainAmountCurrencyValue: CurrencyValue? = null
    ): TransactionViewItem {
        return TransactionViewItem(
            wallet,
            record,
            getTransactionType(record, lastBlockInfo),
            Date(record.timestamp * 1000),
            record.status(lastBlockInfo?.height),
            mainAmountCurrencyValue?.let { getCurrencyString(it) }
        )
    }

    private fun getTransactionType(
        record: TransactionRecord,
        lastBlockInfo: LastBlockInfo?
    ): TransactionType {
        return when (record) {
            is EvmIncomingTransactionRecord ->
                TransactionType.Incoming(
                    getNameOrAddress(record.from),
                    getCoinString(record.value),
                    null,
                    null
                )

            is EvmOutgoingTransactionRecord ->
                TransactionType.Outgoing(
                    getNameOrAddress(record.to),
                    getCoinString(record.value),
                    null,
                    null,
                    sentToSelf = record.sentToSelf
                )

            is SwapTransactionRecord ->
                TransactionType.Swap(
                    getNameOrAddress(record.exchangeAddress),
                    getCoinString(record.valueIn),
                    record.valueOut?.let { getCoinString(it) },
                    foreignRecipient = record.foreignRecipient
                )

            is ApproveTransactionRecord ->
                TransactionType.Approve(
                    getNameOrAddress(record.spender),
                    getCoinString(record.value),
                    record.value.isMaxValue
                )

            is ContractCallTransactionRecord ->
                TransactionType.ContractCall(
                    getNameOrAddress(record.contractAddress),
                    record.method
                )

            is ContractCreationTransactionRecord -> TransactionType.ContractCreation

            is BitcoinIncomingTransactionRecord -> {
                val lockState = record.lockState(lastBlockInfo?.timestamp)

                TransactionType.Incoming(
                    record.from,
                    getCoinString(record.value),
                    lockState,
                    record.conflictingHash
                )
            }

            is BitcoinOutgoingTransactionRecord -> {
                val lockState = record.lockState(lastBlockInfo?.timestamp)

                TransactionType.Outgoing(
                    record.to,
                    getCoinString(record.value),
                    lockState,
                    record.conflictingHash,
                    record.sentToSelf
                )
            }

            else -> throw IllegalArgumentException("Record must be associated with TransactionType")
        }
    }

    private fun getCoinString(coinValue: CoinValue): String {
        val significantDecimal = numberFormatter.getSignificantDecimalCoin(coinValue.value)
        return numberFormatter.formatCoin(
            coinValue.value,
            coinValue.coin.code,
            0,
            significantDecimal
        )
    }

    private fun getCurrencyString(currencyValue: CurrencyValue): String {
        return numberFormatter.formatFiat(currencyValue.value, currencyValue.currency.symbol, 0, 2)
    }

    private fun getNameOrAddress(address: String): String {
        return transactionInfoAddressMapper.title(address)
            ?: "${address.take(5)}...${address.takeLast(5)}"
    }

}
