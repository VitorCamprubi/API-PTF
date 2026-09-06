package br.com.ptf.api.dto;

import br.com.ptf.api.domain.Account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String document,
        String holderName,
        BigDecimal balance,
        OffsetDateTime createdAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getDocument(),
                account.getHolderName(),
                account.getBalance(),
                account.getCreatedAt()
        );
    }
}
