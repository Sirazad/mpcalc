package hu.mpcalc.backend.repo;

import hu.mpcalc.backend.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public final class Repositories {
    private Repositories() {}

    public interface SchoolRepository extends JpaRepository<School, String> {}
    public interface MosaicRepository extends JpaRepository<MosaicEntity, String> {
        List<MosaicEntity> findAllByOrderBySchoolAscTierAscNameAsc();
    }
    public interface FormRepository extends JpaRepository<FormEntity, String> {}
    public interface MaterialGroupRepository extends JpaRepository<MaterialGroup, String> {}
    public interface ReferenceTextRepository extends JpaRepository<ReferenceText, String> {}
    public interface PlayerRepository extends JpaRepository<Player, Long> {
        Optional<Player> findByName(String name);
    }
    public interface CharacterRepository extends JpaRepository<GameCharacter, Long> {
        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @org.springframework.data.jpa.repository.Query("select c from GameCharacter c where c.id = :id")
        Optional<GameCharacter> lockById(@org.springframework.data.repository.query.Param("id") Long id);
        List<GameCharacter> findAllByPlayerIdOrderByName(Long playerId);
    }
    public interface TierRepository extends JpaRepository<CharacterSchoolTier, CharacterSchoolTier.Key> {
        List<CharacterSchoolTier> findAllByCharacterId(Long characterId);
        void deleteAllByCharacterId(Long characterId);
    }
    public interface SavedSpellRepository extends JpaRepository<SavedSpell, Long> {
        List<SavedSpell> findAllByCharacterIdOrderByName(Long characterId);
    }
    public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
}
