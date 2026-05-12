package work.jscraft.alt.ops.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemParameterRepository extends JpaRepository<SystemParameterEntity, String> {
}
