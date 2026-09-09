package br.com.ptf.api.domain;

import java.math.BigDecimal;

/**
 * Tipo da transacao, com o efeito que cada tipo causa no saldo.
 *
 * O comportamento fica no proprio enum em vez de um switch espalhado pelo
 * service. Consequencia pratica: adicionar um tipo novo (TRANSFER, REVERSAL)
 * nao compila enquanto o efeito dele nao for implementado. O compilador vira
 * a rede de seguranca, em vez de um switch que silenciosamente nao trata o
 * caso novo.
 */
public enum TransactionType {

    CREDIT {
        @Override
        public void applyTo(Account account, BigDecimal amount) {
            account.credit(amount);
        }
    },

    DEBIT {
        @Override
        public void applyTo(Account account, BigDecimal amount) {
            account.debit(amount);
        }
    };

    public abstract void applyTo(Account account, BigDecimal amount);
}
