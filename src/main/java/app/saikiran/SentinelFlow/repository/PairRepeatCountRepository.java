package app.saikiran.SentinelFlow.repository;

import app.saikiran.SentinelFlow.model.entity.PairRepeatCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PairRepeatCountRepository extends JpaRepository<PairRepeatCount, Long> {

    Optional<PairRepeatCount> findByAccountAAndAccountB(String accountA, String accountB);
}
