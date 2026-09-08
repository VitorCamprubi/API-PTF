package br.com.ptf.api.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projecao de leitura do saldo.
 *
 * Consultar saldo nao precisa da conta inteira. Esta interface faz o Hibernate
 * montar um SELECT apenas com id, balance e updated_at, em vez de carregar todas
 * as colunas e ainda colocar uma entidade gerenciada no contexto de persistencia.
 *
 * Os nomes dos metodos seguem o padrao getXxx e casam com as propriedades da
 * entidade Account. E assim que o Spring Data sabe quais colunas selecionar.
 */
public interface AccountBalanceProjection {

    UUID getId();

    BigDecimal getBalance();

    OffsetDateTime getUpdatedAt();
}
