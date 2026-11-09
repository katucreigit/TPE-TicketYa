package com.ticketya.reservas;

import com.ticketya.reservas.client.EventosClient;
import com.ticketya.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Huso horario de la sesion de la base (design.md, "Huso horario de la sesion"): cada conexion del pool queda
 * en UTC por connection-init-sql, y Hibernate (jdbc.time_zone UTC) entrega esas horas a Java en el huso de la JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HusoHorarioSesionTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private ReservaRepository reservaRepository;

    @MockitoBean
    private EventosClient eventosClient;

    @Test
    void cadaConexionDelPoolTieneLaSesionEnUtc() throws Exception {
        // Varias conexiones abiertas a la vez obligan al pool a entregar conexiones distintas.
        List<Connection> conexiones = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                conexiones.add(dataSource.getConnection());
            }
            for (Connection c : conexiones) {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT current_setting('TimeZone'), localtimestamp = (now() AT TIME ZONE 'UTC')")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("UTC");
                    assertThat(rs.getBoolean(2)).as("localtimestamp es la hora UTC").isTrue();
                }
            }
        } finally {
            for (Connection c : conexiones) {
                c.close();
            }
        }
    }

    @Test
    void laHoraDeLaBaseLlegaAJavaEnElHusoDeLaJvmSinCorrimiento() {
        LocalDateTime ahoraBase = reservaRepository.ahora();

        assertThat(Duration.between(ahoraBase, LocalDateTime.now()).abs())
                .as("localtimestamp (UTC) convertido por Hibernate al huso de la JVM").isLessThan(Duration.ofSeconds(30));
    }
}
