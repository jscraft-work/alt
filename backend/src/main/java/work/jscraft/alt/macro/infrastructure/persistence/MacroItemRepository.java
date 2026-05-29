package work.jscraft.alt.macro.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MacroItemRepository extends JpaRepository<MacroItemEntity, UUID> {

    Optional<MacroItemEntity> findByBaseDate(LocalDate baseDate);

    @Query("SELECT MAX(m.baseDate) FROM MacroItemEntity m")
    Optional<LocalDate> findMaxBaseDate();
}
