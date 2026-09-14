package br.com.ptf.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita @Scheduled.
 *
 * Numa classe separada, e nao na classe main, para que o motivo de existir esteja
 * escrito em algum lugar e para que desligar o agendamento (num teste, por
 * exemplo) seja uma exclusao localizada.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
